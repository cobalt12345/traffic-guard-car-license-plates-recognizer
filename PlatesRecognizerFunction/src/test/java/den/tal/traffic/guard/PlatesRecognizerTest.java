package den.tal.traffic.guard;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Ignore("Current test is just an example.")
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
                                "00603BDB0BB2324184"),"1.0"),

                new S3EventNotification.UserIdentityEntity("AWS:AIDAW2W76X6J7OEPEBHYZ")
        )));

        String result = platesRecognizer.handleRequest(s3Event, new TestContext());
        log.info("Handle request result: {}", result);
    }

    @Test
    public void doesTextLookLikeCarLicensePlateNumberTest() {
        String carNumberEn = "E642YN36";
        String carNumberRus = "о951ХУ36";
        assertTrue(platesRecognizer.doesTextLookLikeCarLicensePlateNumber(carNumberEn));
        assertFalse(platesRecognizer.doesTextLookLikeCarLicensePlateNumber(carNumberRus));

        String weddingPlate = "wedding";
        assertFalse(platesRecognizer.doesTextLookLikeCarLicensePlateNumber(weddingPlate));
    }
}
