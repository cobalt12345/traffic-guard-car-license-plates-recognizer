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
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class PlatesRecognizer implements RequestHandler<S3Event, String> {
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();
    private AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    private final String carLicensePlatePattern = "^[a-zA-z]{1}\\d{3}[a-zA-Z]{2}\\d{1,4}$";
    private Pattern pattern = Pattern.compile(carLicensePlatePattern);
    private int scale = 5;
    private String destinationBucket = "traffic-guard-cars-and-plates";

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
                                textDetection.getDetectedText().trim())).collect(Collectors.toList());

            for (TextDetection detectedCarLicensePlateNumber : detectedCarLicensePlateNumbers) {
                BoundingBox boundingBox = detectedCarLicensePlateNumber.getGeometry().getBoundingBox();
                List<Point> platePolygon = detectedCarLicensePlateNumber.getGeometry().getPolygon();
                getCarFromImage(bucket, key, detectedCarLicensePlateNumber, platePolygon);
            }

            return "Ok";
        }
    }

    private void getCarFromImage(String bucket, String objectKey, TextDetection detectedCarLicensePlateNumber,
                                 List<Point> platePolygon) {

        log.debug("Car license plate number: {}. Bounding box: {}", detectedCarLicensePlateNumber,
                platePolygon);

        try (com.amazonaws.services.s3.model.S3Object detectedFrame =
                     s3Client.getObject(new GetObjectRequest(bucket, objectKey));
                        InputStream detectedFrameIs = detectedFrame.getObjectContent()) {

            BufferedImage detectedFrameImage = ImageIO.read(detectedFrameIs);
            int detectedFrameImageWidth = detectedFrameImage.getWidth();
            int detectedFrameImageHeight = detectedFrameImage.getHeight();

//            int plateLeft = (int) (detectedFrameImageWidth * boundingBox.getLeft().floatValue());
//            int plateTop = (int) (detectedFrameImageHeight * boundingBox.getTop().floatValue());
//            int plateWidth = (int) (detectedFrameImageWidth * boundingBox.getWidth());
//            int plateHeight = (int) (detectedFrameImageHeight * boundingBox.getHeight());
            int plateLeft = (int) (platePolygon.get(3).getX().floatValue() * detectedFrameImageWidth);
            int plateTop = (int) (platePolygon.get(3).getY().floatValue() * detectedFrameImageHeight);
            int plateWidth = (int) (detectedFrameImageWidth * (platePolygon.get(1).getX().floatValue()
                - platePolygon.get(3).getX().floatValue()));

            int plateHeight = (int) (detectedFrameImageHeight * (platePolygon.get(1).getY().floatValue()
                - platePolygon.get(3).getY().floatValue()));

            BufferedImage carPlate = detectedFrameImage.getSubimage(plateLeft, plateTop, plateWidth, plateHeight);
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                ImageIO.write(carPlate, "jpg", os);
                try (ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray())) {
                    ObjectMetadata meta = new ObjectMetadata();
                    meta.setContentLength(os.size());
                    meta.setContentType("image/jpeg");
                    String[] pathParts = objectKey.split("/");
                    String carPlateImageKey = detectedCarLicensePlateNumber.getDetectedText() + "/" +
                            pathParts[pathParts.length - 1];

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
}
