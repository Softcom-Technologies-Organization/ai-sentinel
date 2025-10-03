package pro.softcom.sentinelle.infrastructure.pii.detection.adapter.out.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pii_detection.PIIDetectionServiceGrpc;
import pii_detection.PiiDetection;
import pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.config.PiiDetectorConfig;
import pro.softcom.sentinelle.infrastructure.pii.scan.adapter.out.transport.NettyGrpcPiiTransport;

@ExtendWith(MockitoExtension.class)
class NettyGrpcPiiTransportTest {

    @AfterEach
    void clearInterruptFlag() {
        // Ensure tests don't leak thread interruption state across cases
        Thread.interrupted();
    }

    @Test
    void Should_InvokeDetectNormally_When_ChannelHealthy() throws InterruptedException {
        try (MockedStatic<ManagedChannelBuilder> mcb = Mockito.mockStatic(ManagedChannelBuilder.class);
             MockedStatic<PIIDetectionServiceGrpc> stubStatic = Mockito.mockStatic(PIIDetectionServiceGrpc.class)) {

            // Arrange main channel + builder
            ManagedChannel channel = mock(ManagedChannel.class);
            when(channel.getState(false)).thenReturn(ConnectivityState.READY);
            when(channel.shutdown()).thenReturn(channel);

            @SuppressWarnings("unchecked")
            ManagedChannelBuilder<?> builder = mock(ManagedChannelBuilder.class, Mockito.RETURNS_SELF);
            when(builder.build()).thenReturn(channel);

            mcb.when(() -> ManagedChannelBuilder.forAddress(anyString(), anyInt())).thenReturn(builder);

            // Arrange stub
            PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub = mock(PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub.class);
            when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);

            PiiDetection.PIIDetectionResponse response = PiiDetection.PIIDetectionResponse.newBuilder().build();
            when(stub.detectPII(any(PiiDetection.PIIDetectionRequest.class))).thenReturn(response);

            stubStatic.when(() -> PIIDetectionServiceGrpc.newBlockingStub(channel)).thenReturn(stub);

            // Create transport
            PiiDetectorConfig cfg = new PiiDetectorConfig("example.com", 50051, 0.5f, 2000, 5000);
            NettyGrpcPiiTransport transport = new NettyGrpcPiiTransport(cfg);

            // Act
            PiiDetection.PIIDetectionResponse out = transport.detect("data", 0.7f, 1000);

            // Assert
            assertThat(out).isSameAs(response);
            verify(channel, times(0)).resetConnectBackoff();
            verify(stub).detectPII(any(PiiDetection.PIIDetectionRequest.class));

            // shutdown path
            transport.shutdown();
            verify(channel).shutdown();
            verify(channel).awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void Should_ResetBackoff_When_ChannelInTransientFailure() {
        try (MockedStatic<ManagedChannelBuilder> mcb = Mockito.mockStatic(ManagedChannelBuilder.class);
             MockedStatic<PIIDetectionServiceGrpc> stubStatic = Mockito.mockStatic(PIIDetectionServiceGrpc.class)) {

            ManagedChannel channel = mock(ManagedChannel.class);
            when(channel.getState(false)).thenReturn(ConnectivityState.TRANSIENT_FAILURE);

            @SuppressWarnings("unchecked")
            ManagedChannelBuilder<?> builder = mock(ManagedChannelBuilder.class, Mockito.RETURNS_SELF);
            when(builder.build()).thenReturn(channel);
            mcb.when(() -> ManagedChannelBuilder.forAddress(anyString(), anyInt())).thenReturn(builder);

            PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub = mock(PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub.class);
            when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
            when(stub.detectPII(any(PiiDetection.PIIDetectionRequest.class)))
                    .thenReturn(PiiDetection.PIIDetectionResponse.newBuilder().build());
            stubStatic.when(() -> PIIDetectionServiceGrpc.newBlockingStub(channel)).thenReturn(stub);

            NettyGrpcPiiTransport transport = new NettyGrpcPiiTransport(new PiiDetectorConfig("example.com", 50051, 0.5f, 2000, 5000));

            transport.detect("content", 0.5f, 1500);

            verify(channel).resetConnectBackoff();
        }
    }

    @Test
    void Should_RetryOnceWithFreshChannel_When_UnimplementedUnknownService()
        throws InterruptedException {
        try (MockedStatic<ManagedChannelBuilder> mcb = Mockito.mockStatic(ManagedChannelBuilder.class);
             MockedStatic<PIIDetectionServiceGrpc> stubStatic = Mockito.mockStatic(PIIDetectionServiceGrpc.class)) {

            // main channel + builder
            ManagedChannel channel1 = mock(ManagedChannel.class);
            when(channel1.getState(false)).thenReturn(ConnectivityState.READY);
            @SuppressWarnings("unchecked")
            ManagedChannelBuilder<?> builder1 = mock(ManagedChannelBuilder.class, Mockito.RETURNS_SELF);
            when(builder1.build()).thenReturn(channel1);

            // temp channel + builder
            ManagedChannel channel2 = mock(ManagedChannel.class);
            when(channel2.shutdown()).thenReturn(channel2);
            @SuppressWarnings("unchecked")
            ManagedChannelBuilder<?> builder2 = mock(ManagedChannelBuilder.class, Mockito.RETURNS_SELF);
            when(builder2.build()).thenReturn(channel2);

            mcb.when(() -> ManagedChannelBuilder.forAddress(anyString(), anyInt()))
                    .thenReturn(builder1)
                    .thenReturn(builder2);

            // stubs
            PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub1 = mock(PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub.class);
            PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub2 = mock(PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub.class);

            when(stub1.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub1);
            when(stub2.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub2);

            StatusRuntimeException unimplemented = new StatusRuntimeException(
                    Status.UNIMPLEMENTED.withDescription("Unknown service pii_detection.PIIDetectionService"));
            when(stub1.detectPII(any(PiiDetection.PIIDetectionRequest.class))).thenThrow(unimplemented);

            PiiDetection.PIIDetectionResponse expected = PiiDetection.PIIDetectionResponse.newBuilder().build();
            when(stub2.detectPII(any(PiiDetection.PIIDetectionRequest.class))).thenReturn(expected);

            stubStatic.when(() -> PIIDetectionServiceGrpc.newBlockingStub(channel1)).thenReturn(stub1);
            stubStatic.when(() -> PIIDetectionServiceGrpc.newBlockingStub(channel2)).thenReturn(stub2);

            NettyGrpcPiiTransport transport = new NettyGrpcPiiTransport(new PiiDetectorConfig("localhost", 50051, 0.5f, 2000, 5000));

            PiiDetection.PIIDetectionResponse out = transport.detect("hello", 0.5f, 1200);

            assertThat(out).isSameAs(expected);
            verify(channel2).shutdown();
            verify(channel2).awaitTermination(3L, TimeUnit.SECONDS);
        }
    }

    @Test
    void Should_PropagateException_When_StatusNotUnknownService() {
        try (MockedStatic<ManagedChannelBuilder> mcb = Mockito.mockStatic(ManagedChannelBuilder.class);
             MockedStatic<PIIDetectionServiceGrpc> stubStatic = Mockito.mockStatic(PIIDetectionServiceGrpc.class)) {

            ManagedChannel channel = mock(ManagedChannel.class);
            when(channel.getState(false)).thenReturn(ConnectivityState.READY);

            @SuppressWarnings("unchecked")
            ManagedChannelBuilder<?> builder = mock(ManagedChannelBuilder.class, Mockito.RETURNS_SELF);
            when(builder.build()).thenReturn(channel);

            mcb.when(() -> ManagedChannelBuilder.forAddress(anyString(), anyInt())).thenReturn(builder);

            PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub = mock(PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub.class);
            when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
            StatusRuntimeException tooSlow = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
            when(stub.detectPII(any(PiiDetection.PIIDetectionRequest.class))).thenThrow(tooSlow);
            stubStatic.when(() -> PIIDetectionServiceGrpc.newBlockingStub(channel)).thenReturn(stub);

            NettyGrpcPiiTransport transport = new NettyGrpcPiiTransport(new PiiDetectorConfig("example.com", 1234, 0.5f, 2000, 5000));

            assertThatThrownBy(() -> transport.detect("abc", 0.4f, 900))
                    .isSameAs(tooSlow);
        }
    }

    @Test
    void Should_ThrowCancelled_When_InterruptedDuringRetry() {
        try (MockedStatic<ManagedChannelBuilder> mcb = Mockito.mockStatic(ManagedChannelBuilder.class);
             MockedStatic<PIIDetectionServiceGrpc> stubStatic = Mockito.mockStatic(PIIDetectionServiceGrpc.class)) {

            // main builder/channel
            ManagedChannel channel1 = mock(ManagedChannel.class);
            when(channel1.getState(false)).thenReturn(ConnectivityState.READY);
            @SuppressWarnings("unchecked")
            ManagedChannelBuilder<?> builder1 = mock(ManagedChannelBuilder.class, Mockito.RETURNS_SELF);
            when(builder1.build()).thenReturn(channel1);

            // Second call to forAddress will not be reached due to interruption, but return something if it does
            @SuppressWarnings("unchecked")
            ManagedChannelBuilder<?> builder2 = mock(ManagedChannelBuilder.class, Mockito.RETURNS_SELF);

            mcb.when(() -> ManagedChannelBuilder.forAddress(anyString(), anyInt()))
                    .thenReturn(builder1)
                    .thenReturn(builder2);

            PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub stub1 = mock(PIIDetectionServiceGrpc.PIIDetectionServiceBlockingStub.class);
            when(stub1.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub1);
            when(stub1.detectPII(any(PiiDetection.PIIDetectionRequest.class)))
                    .thenThrow(new StatusRuntimeException(Status.UNIMPLEMENTED.withDescription("unknown service pii_detection.piidetectionservice")));
            stubStatic.when(() -> PIIDetectionServiceGrpc.newBlockingStub(channel1)).thenReturn(stub1);

            NettyGrpcPiiTransport transport = new NettyGrpcPiiTransport(new PiiDetectorConfig("example.com", 50051, 0.5f, 2000, 5000));

            // Interrupt this test thread so that Thread.sleep(200) throws InterruptedException
            Thread.currentThread().interrupt();

            Assertions.assertThatThrownBy(() -> transport.detect("content", 0.5f, 1200))
                    .isInstanceOf(StatusRuntimeException.class)
                    .hasMessageContaining("CANCELLED")
                    .satisfies(ex -> assertThat(((StatusRuntimeException) ex).getStatus().getCode()).isEqualTo(Status.Code.CANCELLED));
        }
    }

    @Test
    void Should_NormalizeHost_When_ConstructingChannel() {
        try (MockedStatic<ManagedChannelBuilder> mcb = Mockito.mockStatic(ManagedChannelBuilder.class)) {
            ManagedChannel channel = mock(ManagedChannel.class);

            @SuppressWarnings("unchecked")
            ManagedChannelBuilder<?> builder = mock(ManagedChannelBuilder.class, Mockito.RETURNS_SELF);
            when(builder.build()).thenReturn(channel);

            // Capture calls to forAddress to verify normalization
            mcb.when(() -> ManagedChannelBuilder.forAddress(anyString(), anyInt())).thenReturn(builder);

            // localhost -> 127.0.0.1
            new NettyGrpcPiiTransport(new PiiDetectorConfig("localhost", 1111, 0.5f, 2000, 5000));
            mcb.verify(() -> ManagedChannelBuilder.forAddress("127.0.0.1", 1111));

            // blank -> 127.0.0.1
            new NettyGrpcPiiTransport(new PiiDetectorConfig("   ", 2222, 0.5f, 2000, 5000));
            mcb.verify(() -> ManagedChannelBuilder.forAddress("127.0.0.1", 2222));

            // custom host gets trimmed
            new NettyGrpcPiiTransport(new PiiDetectorConfig("  foo.com ", 3333, 0.5f, 2000, 5000));
            mcb.verify(() -> ManagedChannelBuilder.forAddress("foo.com", 3333));
        }
    }
}
