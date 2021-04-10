package den.tal.traffic.guard.db.data;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@ToString
@DynamoDBTable(tableName = "TrafficGuardParsedCarLicensePlates")
public class RecognizedPlate {

    @Getter
    @Setter
    @DynamoDBHashKey(attributeName = "car_license_plate_number")
    private String carLicensePlateNumber;

    @Getter
    @DynamoDBRangeKey(attributeName = "parsed_timestamp")
    private long timestamp = System.currentTimeMillis();

    @Getter
    @Setter
    @DynamoDBAttribute(attributeName = "parsed_date_time")
    private String timeStampHumanReadable = new Date(timestamp).toString();

    @Getter
    @Setter
    @DynamoDBIndexHashKey(attributeName = "object_key_in_bucket", globalSecondaryIndexName = "ObjectKeyInBucketGSI")
    @DynamoDBAttribute(attributeName = "object_key_in_bucket")
    private String objectKeyInBucket;

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        timeStampHumanReadable = new Date(timestamp).toString();
    }
}
