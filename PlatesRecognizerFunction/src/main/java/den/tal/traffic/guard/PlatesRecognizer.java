package den.tal.traffic.guard;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import den.tal.traffic.guard.db.data.RecognizedPlate;
import den.tal.traffic.guard.db.services.RecognizedPlatesService;
import den.tal.traffic.guard.settings.Params;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class PlatesRecognizer implements RequestHandler<S3Event, String> {
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private AmazonRekognition rekognitionClient;
    private AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final String carLicensePlatePattern = "[a-zA-z]{1}\\d{3,4}[a-zA-Z]{2}\\d{1,3}";
    private Pattern pattern = Pattern.compile(carLicensePlatePattern);
    private final String NOT_ALLOWED_CHARACTERS = "[^A-Za-z0-9]";
    private float scale = 20.0f;
    private String destinationBucket = "traffic-guard-cars-and-plates";
    private ImageFragmentExtractor imageFragmentExtractor = new ImageFragmentExtractor(scale);
    private RecognizedPlatesService recognizedPlatesService = new RecognizedPlatesService(
            Integer.parseInt(System.getenv().get("dontRecognizeAgainInMinutes")), ChronoUnit.MINUTES);

    public PlatesRecognizer() {
        rekognitionClient = AmazonRekognitionClientBuilder.standard()
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance()).withRegion(
                        System.getenv().get("rekognitionServiceRegion")).build();
    }

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        log.info("EVENT: {}", gson.toJson(s3Event));
        S3EventNotification.S3EventNotificationRecord record = s3Event.getRecords().get(0);
        String bucket = record.getS3().getBucket().getName();
        String key = record.getS3().getObject().getUrlDecodedKey();
        log.info("File '{}' was uploaded into the bucket '{}'.", key, bucket);
        if (!key.endsWith("jpg")) {
            log.warn("Uploaded object '{}' is not a JPEG image file.", key);

            return "Not a JPEG image file.";

        } else {
            DetectTextRequest textDetectionRequest = new DetectTextRequest().withImage(new Image()
                    .withS3Object(new S3Object().withBucket(bucket).withName(key)));

            List<TextDetection> detectedCarLicensePlateNumbers =
                    rekognitionClient.detectText(textDetectionRequest).getTextDetections();

            //Remove invalid symbols from detections and concatenate all words into single line
            detectedCarLicensePlateNumbers.stream().forEach(text -> text.setDetectedText(text.getDetectedText()
                    .replaceAll(NOT_ALLOWED_CHARACTERS, "")));
            //Concatenate all recognized text fragments
            String wholeDetectResultAsString = detectedCarLicensePlateNumbers.stream().reduce("",
                    (str, textDetection) -> str + textDetection.getDetectedText(),
                    (strLeft, strRight) -> strLeft + strRight);

            wholeDetectResultAsString = wholeDetectResultAsString.toUpperCase();
            //Now try to find if the whole fragment contains something looking like car license plate numbers
            Scanner scanner = new Scanner(wholeDetectResultAsString);
            for (String seems2bCarLicensePlateNumber = scanner.findInLine(pattern);
                 null != seems2bCarLicensePlateNumber;seems2bCarLicensePlateNumber = scanner.findInLine(pattern)) {
                log.debug("Seems to be a car license plate number : {}", seems2bCarLicensePlateNumber);
                RecognizedPlate recognizedPlate = new RecognizedPlate();
                recognizedPlate.setCarLicensePlateNumber(seems2bCarLicensePlateNumber);
                recognizedPlate.setTimestamp(System.currentTimeMillis());
                if (!recognizedPlatesService.carLicensePlateNumberWasRecognizedInPeriod(recognizedPlate)) {
                    getCarFromImage(bucket, key, recognizedPlate);
                    if (null != recognizedPlate.getObjectKeyInBucket()) {
                        log.debug("Car plate images saved with key '{}'.", recognizedPlate.getObjectKeyInBucket());
                        recognizedPlatesService.saveRecognizedCarLicensePlateNumber(recognizedPlate);
                    } else {
                        log.warn("Car plate images was not saved!");
                    }
                }
            }

            if (Boolean.parseBoolean(System.getenv().get("RemoveProcessedImagesFromSourceBucket"))) {
                log.debug("Now remove uploaded image '{}'. From bucket '{}'.", key, bucket);
                s3Client.deleteObject(new DeleteObjectRequest(bucket, key));
            } else {
                log.debug("Keep image '{}' in bucket '{}' for the following analysis.", key, bucket);
            }

            return "Ok";
        }
    }

    private void getCarFromImage(String bucket, String objectKey, RecognizedPlate recognizedPlate) {
        final String detectedCarLicensePlateNumber = recognizedPlate.getCarLicensePlateNumber();
        log.debug("Car license plate number: {}.", detectedCarLicensePlateNumber);
        try (com.amazonaws.services.s3.model.S3Object detectedFrame =
                     s3Client.getObject(new GetObjectRequest(bucket, objectKey));
                        InputStream detectedFrameIs = detectedFrame.getObjectContent()) {

            BufferedImage detectedFrameImage = ImageIO.read(detectedFrameIs);
            BufferedImage carPlate = imageFragmentExtractor.extractFragment(
                    new BoundingBox().withHeight(Float.MAX_VALUE).withWidth(Float.MAX_VALUE)
                    .withTop(1.0f).withLeft(1.0f), detectedFrameImage);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(carPlate, Params.FORMAT_NAME.toString(), os);
                try (ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray())) {
                    ObjectMetadata meta = new ObjectMetadata();
                    meta.setContentLength(os.size());
                    meta.setContentType(Params.CONTENT_TYPE.toString());
                    Map<String, String> userMetadata = detectedFrame.getObjectMetadata().getUserMetadata();
                    meta.setUserMetadata(userMetadata);
                    if (userMetadata.containsKey("location")) {
                        recognizedPlate.setGpsLocation(userMetadata.get("location"));
                    }
                    String[] pathParts = objectKey.split("/");
                    String carPlateImageKey = detectedCarLicensePlateNumber + "/" +
                            pathParts[pathParts.length - 1];

                    recognizedPlate.setObjectKeyInBucket(carPlateImageKey);
                    log.debug("Write car plate image to {}", "s3://" + destinationBucket + "/" + carPlateImageKey);
                    s3Client.putObject(destinationBucket, carPlateImageKey, is, meta);
                }
            }
        } catch (IOException ioex) {
            log.error(String.format("Couldn't get image '%s' from bucket '%s'", objectKey, bucket), ioex);
        }
    }

    boolean doesTextLookLikeCarLicensePlateNumber(String text) {
        Matcher matcher = pattern.matcher(text);
        boolean isCarLicensePlate = matcher.matches();
        log.debug("Text '{}' is a car license plate? {}!", text, isCarLicensePlate ? "YES" : "NO");

        return isCarLicensePlate;
    }

    TextDetection normalizeCarPlateNumber(TextDetection detectedCarLicensePlateNumber) {
        String carPlateNumberText = detectedCarLicensePlateNumber.getDetectedText();
        String carPlateNumberTextNormalized = carPlateNumberText.replaceAll("[\\.\\s]", "")
                .toUpperCase();

        log.debug("Original car plate number: {} Normalizar car plate number: {}", carPlateNumberText,
                carPlateNumberTextNormalized);

        return detectedCarLicensePlateNumber.clone().withDetectedText(carPlateNumberTextNormalized);
    }
}
