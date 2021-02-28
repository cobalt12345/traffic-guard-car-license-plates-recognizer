package den.tal.traffic.guard;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.Arrays;

@Slf4j
public class PlatesRecognizerTest {

    @Test
    public void handleRequestTest() {
        var platesRecognizer = new PlatesRecognizer();
        var s3Event = new S3Event(Arrays.asList(new S3EventNotification.S3EventNotificationRecord(
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

        var result = platesRecognizer.handleRequest(s3Event, null);
        log.info("Handle request result: {}", result);
    }
}
