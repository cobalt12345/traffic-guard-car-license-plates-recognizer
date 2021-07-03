package den.tal.traffic.guard.db.data;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@ToString
public class RecognizedPlate {

    @Getter
    @Setter
    @DynamoDBHashKey(attributeName = "car_license_plate_number")
    private String carLicensePlateNumber;

    @Getter
    @DynamoDBRangeKey(attributeName = "parsed_timestamp")
    private int timestamp = (int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

    @Getter
    @Setter
    @DynamoDBAttribute(attributeName = "parsed_date_time")
    private String timeStampHumanReadable = new Date(timestamp).toString();

    @Getter
    @Setter
    @DynamoDBIndexHashKey(attributeName = "object_key_in_bucket", globalSecondaryIndexName = "ObjectKeyInBucketGSI")
    @DynamoDBAttribute(attributeName = "object_key_in_bucket")
    private String objectKeyInBucket;

    @Getter
    @Setter
    @DynamoDBAttribute(attributeName = "gps_location")
    private String gpsLocation;

    //GraphQL API required fields
    /*
    AWSTimestamp - number of seconds before or after Unix epoch.
     */
    @Getter
    @Setter
    @DynamoDBAttribute(attributeName = "_lastChangedAt")
    private int _lastChangedAt = (int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

    /*
    AWSDateTime - YYYY-MM-DDThh:mm:ss.sssZ
     */
    @Getter
    @Setter
    @DynamoDBAttribute(attributeName = "createdAt")
    private String createdAt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date());

    /*
    AWSDateTime - YYYY-MM-DDThh:mm:ss.sssZ
     */
    @Getter
    @Setter
    @DynamoDBAttribute(attributeName = "updatedAt")
    private String updatedAt = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date());

    @Getter
    @Setter
    @DynamoDBAttribute(attributeName = "_version")
    private int _version = 0;

    /**
     * Set timestamp in millis since Unix epoch.
     *
     * @param timestamp
     */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
        timeStampHumanReadable = new Date(TimeUnit.SECONDS.toMillis(timestamp)).toString();
    }
}
