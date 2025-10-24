package io.playtics.gateway.api;

import io.playtics.common.auth.HmacSigner;
import io.playtics.gateway.config.AuthService;
import io.playtics.gateway.kafka.AvroPublisher;
import io.playtics.gateway.kafka.DlqPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;

import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HmacSignatureWindowTest {

    @Autowired
    WebTestClient client;

    @MockBean
    AvroPublisher avroPublisher;

    @MockBean
    DlqPublisher dlqPublisher;

    @MockBean
    AuthService authService;

    @Test
    void validSignatureWithinWindowAccepted() {
        when(authService.getSecret("pk_hmac")).thenReturn("sek");
        String body = "{" +
                "\"event_id\":\"01JHMACOK\",\"event_name\":\"level_start\",\"project_id\":\"p1\",\"device_id\":\"d1\",\"ts_client\":1730000000000}";
        long t = Instant.now().getEpochSecond();
        String msg = t + "." + body;
        String sig = HmacSigner.hmacSha256Hex("sek", msg);
        client.post().uri("/v1/batch")
                .contentType(MediaType.valueOf("application/x-ndjson"))
                .header("x-api-key", "pk_hmac")
                .header("x-signature", "t="+t+", s="+sig)
                .bodyValue(body)
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    @Test
    void expiredSignatureRejected() {
        when(authService.getSecret("pk_hmac")).thenReturn("sek");
        String body = "{" +
                "\"event_id\":\"01JHMACEX\",\"event_name\":\"level_start\",\"project_id\":\"p1\",\"device_id\":\"d1\",\"ts_client\":\"1730000000000\"}";
        long t = Instant.now().getEpochSecond() - 400; // > 300s window
        String msg = t + "." + body;
        String sig = HmacSigner.hmacSha256Hex("sek", msg);
        client.post().uri("/v1/batch")
                .contentType(MediaType.valueOf("application/x-ndjson"))
                .header("x-api-key", "pk_hmac")
                .header("x-signature", "t="+t+", s="+sig)
                .bodyValue(body)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
