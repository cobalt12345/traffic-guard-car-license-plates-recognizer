package den.tal.traffic.guard;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.rekognition.model.DetectTextResult;
import com.amazonaws.services.rekognition.model.TextDetection;
import com.amazonaws.services.rekognition.model.TextTypes;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@SetEnvironmentVariable(key = "dontRecognizeAgainInMinutes", value = "5")
@SetEnvironmentVariable(key = "rekognitionServiceRegion", value = "eu-central-1")
@Slf4j
public class PlatesRecognizerTest {

    private PlatesRecognizer platesRecognizer = new PlatesRecognizer();

    private class TestContext implements Context {
        @Override
        public String getAwsRequestId() {
            return null;
        }

        @Override
        public String getLogGroupName() {
            return null;
        }

        @Override
        public String getLogStreamName() {
            return null;
        }

        @Override
        public String getFunctionName() {
            return null;
        }

        @Override
        public String getFunctionVersion() {
            return null;
        }

        @Override
        public String getInvokedFunctionArn() {
            return null;
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 0;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 0;
        }

        @Override
        public LambdaLogger getLogger() {
            return null;
        }
    }

    @Disabled("Current test is just an example.")
    @Test
    public void handleRequestTest() {
        S3Event s3Event = new S3Event(Arrays.asList(new S3EventNotification.S3EventNotificationRecord(
                Regions.EU_CENTRAL_1.getName(),
                "ObjectCreated:Put",
                "aws:s3",
                null,
                "2.1",
                new S3EventNotification.RequestParametersEntity("77.45.169.197"),

                new S3EventNotification.ResponseElementsEntity("7qwFNgYC4vI9ZuBh6yWc4r17y7LHfDQDGM0cB"
                        + "LIFjuCz4qIMnwjq7V4u/3AG5jxyiW7TmkmJxkP3QP5ZlZ241vCnDsWPVq1B",
                        "4D311D5AAE716239"),

                new S3EventNotification.S3Entity("f6e1e682-2823-47f2-892b-58ea19633d3a",
                        new S3EventNotification.S3BucketEntity("traffic-guard-frames",
                                new S3EventNotification.UserIdentityEntity("A4P4B48SH2ZH7"),
                                "arn:aws:s3:::traffic-guard-frames"),
                        new S3EventNotification.S3ObjectEntity("1af8be9b-7417-449c-8ada-bccfeac98484.jpg",
                                27061L, "8d390f203f84fe6682bd50fdc1202f3f", "",
                                "00603BDB0BB2324184"), "1.0"),

                new S3EventNotification.UserIdentityEntity("AWS:AIDAW2W76X6J7OEPEBHYZ")
        )));

        String result = platesRecognizer.handleRequest(s3Event, new TestContext());
        log.info("Handle request result: {}", result);
    }

    @Test
    public void doesTextLookLikeCarLicensePlateNumberTest() {
        String carNumberEn = "E642YH36";
        String carNumberRus = "о951ХУ36";
        assertTrue(platesRecognizer.doesTextLookLikeCarLicensePlateNumber(carNumberEn));
        assertFalse(platesRecognizer.doesTextLookLikeCarLicensePlateNumber(carNumberRus));

        String weddingPlate = "wedding";
        assertFalse(platesRecognizer.doesTextLookLikeCarLicensePlateNumber(weddingPlate));

        String carNumberInvalid = "O951VE36";
        assertFalse(platesRecognizer.doesTextLookLikeCarLicensePlateNumber(carNumberInvalid));
    }

    @Test
    public void normalizeCarPlateNumberTest() {
        final String carNumber = "e642yN 36.";
        final String carNumberNormalized = "E642YN36";
        assertEquals(carNumberNormalized, platesRecognizer.normalizeCarPlateNumber(new TextDetection()
                .withDetectedText(carNumber)).getDetectedText());
    }

    @Test
    public void removeInvalidCharactersTest() {
        final String invalidCharacters = "[^A-Za-z0-9]";
        final String text = "x 277yp.73";
        final String result = text.replaceAll(invalidCharacters, "");
        assertEquals("x277yp73", result);

        final String text1 = "2. .aB8/%^";
        final String result1 = text1.replaceAll(invalidCharacters, "");
        assertEquals("2aB8", result1);

    }

    @Test
    public void concatenateWordsToLineTest() throws IOException {
        final String carLicensePlatePattern = "[a-zA-z]{1}\\d{3,4}[a-zA-Z]{2}\\d{1,3}";
        var detectTextResult = new DetectTextResult().withTextDetections(
                new TextDetection().withType(TextTypes.LINE).withDetectedText("o951"),
                new TextDetection().withType(TextTypes.LINE).withDetectedText(".xY 13 6"),
                new TextDetection().withType(TextTypes.WORD).withDetectedText(" E."),
                new TextDetection().withType(TextTypes.WORD).withDetectedText("6 4 ../2"),
                new TextDetection().withType(TextTypes.WORD).withDetectedText(" Yh 3&6")
        );
        detectTextResult.getTextDetections().stream().forEach(text -> text.setDetectedText(text.getDetectedText()
                .replaceAll("[^A-Za-z0-9]", "")));

        String wholeDetectResultAsString = detectTextResult.getTextDetections().stream().reduce("",
                (str, textDetection) -> str + textDetection.getDetectedText(),
                (strLeft, strRight) -> strLeft + strRight);

        Scanner scanner = new Scanner(wholeDetectResultAsString);
        Pattern pattern = Pattern.compile(carLicensePlatePattern);

        for (String seems2bCarLicensePlate = scanner.findInLine(pattern);
             null != seems2bCarLicensePlate;seems2bCarLicensePlate = scanner.findInLine(pattern)) {

            log.debug("Seems to be a car license plate: {}", seems2bCarLicensePlate);
        }

    }

    @Test
    public void allowedCharactersTest() {
        String alphabit = "";
        for (char character = '0'; character <= 'z'; alphabit += character++);
        log.info("Alphabit: {}", alphabit);
    }

    @Test
    public void checkPrecision() throws Exception {
        Long firstCaughtLong = Long.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        int firstCaughtInt = firstCaughtLong.intValue();
        String firstCaughtString = new Date(TimeUnit.SECONDS.toMillis(firstCaughtInt)).toString();
        //now we save int value to the DB
        System.out.println("1 Long timestamp: " + firstCaughtLong);
        System.out.println("1 int timestamp: " + firstCaughtInt);
        System.out.println("1 String timestamp: " + firstCaughtString);
        Thread.sleep(5000);
        Long secondCaughtLong = Long.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        int secondCaughtInt = secondCaughtLong.intValue();
        String secondCaughtString = new Date(TimeUnit.SECONDS.toMillis(secondCaughtInt)).toString();
        System.out.println("2 Long timestamp: " + secondCaughtLong);
        System.out.println("2 int timestamp: " + secondCaughtInt);
        System.out.println("2 String timestamp: " + secondCaughtString);

        Duration nonRecognizablePeriod =
                Duration.of(Integer.parseInt(System.getenv().get("dontRecognizeAgainInMinutes")), ChronoUnit.MINUTES);

        String skipSeconds = Long.toString(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) -
                nonRecognizablePeriod.toSeconds());

        System.out.printf("Skip last %s seconds%n", skipSeconds);
    }
}
