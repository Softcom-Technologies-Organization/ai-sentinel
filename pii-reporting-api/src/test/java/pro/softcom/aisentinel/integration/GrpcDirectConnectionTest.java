package pro.softcom.aisentinel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;

class GrpcDirectConnectionTest {
    
    private static final Logger logger = LoggerFactory.getLogger(GrpcDirectConnectionTest.class);
    
    @Test
    void testDirectGrpcConnection() {
        logger.info("Testing direct gRPC connection to localhost:50051");
        
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .build();
        
        try {
            PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub = 
                PIIDetectionServiceGrpc.newBlockingStub(channel);
            
            PiiDetection.PIIDetectionRequest request = 
                PiiDetection.PIIDetectionRequest.newBuilder()
                    .setContent("Test email john@gmail.com")
                    .setThreshold(0.5f)
                    .build();
            
            logger.info("Sending request to gRPC service...");
            PiiDetection.PIIDetectionResponse response = stub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .detectPII(request);
            
            logger.info("Response received successfully!");
            logger.info("Entities found: {}", response.getEntitiesCount());
            logger.info("Summary: {}", response.getSummaryMap());
            logger.info("Masked content: {}", response.getMaskedContent());

            assertThat(response.getEntitiesCount()).isGreaterThan(0);
            assertThat(response.getMaskedContent()).isNotEmpty();
            assertThat(response.getSummaryMap()).isNotEmpty();
            
        } catch (StatusRuntimeException e) {
            logger.error("gRPC call failed - Status: {}, Code: {}, Description: {}", 
                        e.getStatus(), e.getStatus().getCode(), e.getStatus().getDescription());
            throw e;
        } finally {
            try {
                channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Error shutting down channel", e);
            }
        }
    }
}
