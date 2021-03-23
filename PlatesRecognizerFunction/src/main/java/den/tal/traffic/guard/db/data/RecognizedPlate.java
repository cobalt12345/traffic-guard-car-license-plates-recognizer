package den.tal.traffic.guard.db.data;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@DynamoDBTable(tableName = "TrafficGuardParsedCarLicensePlates")
public class RecognizedPlate {

    @DynamoDBHashKey(attributeName = "car_license_plate_number")
    private String carLicensePlateNumber;

    @Builder.Default
    @DynamoDBAttribute(attributeName = "parsed_timestamp")
    private long timestamp = System.currentTimeMillis();

    @DynamoDBIndexHashKey(attributeName = "object_key_in_bucket", globalSecondaryIndexName = "ObjectKeyInBucketGSI")
    @DynamoDBAttribute(attributeName = "object_key_in_bucket")
    private String objectKeyInBucket;

}
