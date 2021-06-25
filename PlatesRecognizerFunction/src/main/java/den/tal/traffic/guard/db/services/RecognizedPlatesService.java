package den.tal.traffic.guard.db.services;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import den.tal.traffic.guard.db.data.RecognizedPlate;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RecognizedPlatesService {
    private AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
    private DynamoDBMapperConfig mapperConfig = DynamoDBMapperConfig.builder()
            .withTableNameOverride(DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(
                    System.getenv().get("trafficGuardTableName")))
            .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT).build();

    private DynamoDBMapper mapper = new DynamoDBMapper(dynamoDBClient, mapperConfig);
    private Duration nonRecognizablePeriod;

    public RecognizedPlatesService(long period, ChronoUnit chronoUnit) {
        log.debug("Non-recognizable period: {} {}", period, chronoUnit);
        nonRecognizablePeriod = Duration.of(period, chronoUnit);
    }

    public boolean carLicensePlateNumberWasRecognizedInPeriod(RecognizedPlate recognizedPlate) {
        log.debug("Check if plate number was already recognized: {}", recognizedPlate);
        Map<String, AttributeValue> attributeValues = new HashMap<>();
        attributeValues.put(":val1", new AttributeValue().withS(recognizedPlate.getCarLicensePlateNumber()));
        attributeValues.put(":val2", new AttributeValue().withN(Long.toString(System.currentTimeMillis() -
                nonRecognizablePeriod.toMillis())));

        DynamoDBQueryExpression<RecognizedPlate> queryExpression = new DynamoDBQueryExpression<>();
        queryExpression.withKeyConditionExpression("car_license_plate_number = :val1 and parsed_timestamp >= :val2")
                .withExpressionAttributeValues(attributeValues);

        List<RecognizedPlate> recognizedPlates = mapper.query(RecognizedPlate.class, queryExpression);
        log.debug("Car license plate number '{}' was already recognized {} times within last {} minutes.",
                recognizedPlate.getCarLicensePlateNumber(), recognizedPlates.size(), nonRecognizablePeriod.toMinutes());

        return !recognizedPlates.isEmpty();
    }

    public void saveRecognizedCarLicensePlateNumber(RecognizedPlate recognizedPlate) {
        log.debug("Save recognized car plate number: {}", recognizedPlate);
        mapper.save(recognizedPlate);
    }
}
