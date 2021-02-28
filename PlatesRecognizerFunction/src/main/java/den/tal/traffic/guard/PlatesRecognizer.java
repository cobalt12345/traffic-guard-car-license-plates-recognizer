package den.tal.traffic.guard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class PlatesRecognizer implements RequestHandler<S3Event, String> {
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();
    private final String carLicensePlatePattern = "^[a-zA-z]{1}\\d{3}[a-zA-Z]{2}\\d{1,4}$";
    private Pattern pattern = Pattern.compile(carLicensePlatePattern);

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
                getCarFromImage(detectedCarLicensePlateNumber, boundingBox);
            }

            return "Ok";
        }
    }

    private void getCarFromImage(TextDetection detectedCarLicensePlateNumber, BoundingBox boundingBox) {
        log.debug("Car license plate number: {}. Bounding box: {}", detectedCarLicensePlateNumber,
                boundingBox);

    }

    boolean doesTextLookLikeCarLicensePlateNumber(String text) {
        Matcher matcher = pattern.matcher(text);
        boolean isCarLicensePlate = matcher.matches();
        log.debug("Text '{}' is a car license plate? {}!", text, isCarLicensePlate ? "YES" : "NO");

        return isCarLicensePlate;
    }
}
