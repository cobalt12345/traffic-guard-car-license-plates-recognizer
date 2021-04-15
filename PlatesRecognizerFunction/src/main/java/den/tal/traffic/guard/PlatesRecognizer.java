package den.tal.traffic.guard;

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
import org.slf4j.MDC;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class PlatesRecognizer implements RequestHandler<S3Event, String> {
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();
    private AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final String carLicensePlatePattern = "^[a-zA-z]{1}\\d{3}[a-zA-Z]{2}(\\s)*\\d{1,3}(\\.)*$";
    private Pattern pattern = Pattern.compile(carLicensePlatePattern);
    private float scale = 20.0f;
    private String destinationBucket = "traffic-guard-cars-and-plates";
    private ImageFragmentExtractor imageFragmentExtractor = new ImageFragmentExtractor(scale);
    private RecognizedPlatesService recognizedPlatesService = new RecognizedPlatesService(5, ChronoUnit.MINUTES);
    private static int lambdaInstanceNum = 0;
    {
        log.debug("Created instance #{} of PlatesRecognizer lambda.", ++lambdaInstanceNum);
        MDC.put("lambdaInstanceNum", Integer.toString(lambdaInstanceNum));
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
                    rekognitionClient.detectText(textDetectionRequest).getTextDetections().parallelStream()
                        .filter(textDetection -> doesTextLookLikeCarLicensePlateNumber(
                                textDetection.getDetectedText().trim()))
//                                    .filter(textDetection ->
//                                            TextTypes.fromValue(textDetection.getType()) == TextTypes.LINE)
                                                .collect(Collectors.toList());

            for (TextDetection detectedCarLicensePlateNumber : detectedCarLicensePlateNumbers) {
                TextDetection detectedCarLicensePlateNumberNormalized =
                        normalizeCarPlateNumber(detectedCarLicensePlateNumber);

                RecognizedPlate recognizedPlate = new RecognizedPlate();
                recognizedPlate.setCarLicensePlateNumber(detectedCarLicensePlateNumberNormalized.getDetectedText());
//                recognizedPlate.setObjectKeyInBucket(key);
                recognizedPlate.setTimestamp(System.currentTimeMillis());
                if (!recognizedPlatesService.carLicensePlateNumberWasRecognizedInPeriod(recognizedPlate)) {
                    String carPlateImageKey = getCarFromImage(bucket, key, detectedCarLicensePlateNumberNormalized);
                    if (null != carPlateImageKey) {
                        log.debug("Car plate images saved with key '{}'.", carPlateImageKey);
                        recognizedPlate.setObjectKeyInBucket(carPlateImageKey);
                        recognizedPlatesService.saveRecognizedCarLicensePlateNumber(recognizedPlate);
                    } else {
                        log.warn("Car plate images was not saved!");
                    }
                }
            }

            log.debug("Now remove uploaded image '{}'. From bucket '{}'.", key, bucket);
            s3Client.deleteObject(new DeleteObjectRequest(bucket, key));

            return "Ok";
        }
    }

    private String getCarFromImage(String bucket, String objectKey, TextDetection detectedCarLicensePlateNumber) {
        log.debug("Car license plate number (json): {}.", gson.toJson(detectedCarLicensePlateNumber));
        try (com.amazonaws.services.s3.model.S3Object detectedFrame =
                     s3Client.getObject(new GetObjectRequest(bucket, objectKey));
                        InputStream detectedFrameIs = detectedFrame.getObjectContent()) {

            BufferedImage detectedFrameImage = ImageIO.read(detectedFrameIs);
            BufferedImage carPlate = imageFragmentExtractor.extractFragment(detectedCarLicensePlateNumber.getGeometry()
                    .getBoundingBox(), detectedFrameImage);

            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(carPlate, Params.FORMAT_NAME.toString(), os);
                try (ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray())) {
                    ObjectMetadata meta = new ObjectMetadata();
                    meta.setContentLength(os.size());
                    meta.setContentType(Params.CONTENT_TYPE.toString());
                    String[] pathParts = objectKey.split("/");
                    String carPlateImageKey = detectedCarLicensePlateNumber.getDetectedText() + "/" +
                            pathParts[pathParts.length - 1];

                    log.debug("Write car plate image to {}", "s3://" + destinationBucket + "/" + carPlateImageKey);
                    s3Client.putObject(destinationBucket, carPlateImageKey, is, meta);

                    return carPlateImageKey;
                }
            }
        } catch (IOException ioex) {
            log.error(String.format("Couldn't get image '%s' from bucket '%s'", objectKey, bucket), ioex);
        }

        return null;
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
