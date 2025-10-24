package io.playtics.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.playtics.common.model.Event;
import io.playtics.gateway.kafka.AvroPublisher;
import io.playtics.gateway.kafka.DlqPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

@RestController
@RequestMapping("/v1")
public class BatchController {

    private final ObjectMapper om;
    private final AvroPublisher publisher;
    private final DlqPublisher dlq;
    public BatchController(ObjectMapper om, AvroPublisher publisher, DlqPublisher dlq) { this.om = om; this.publisher = publisher; this.dlq = dlq; }

    public static class BatchResponse {
        public List<String> accepted = new CopyOnWriteArrayList<>();
        public List<Map<String, String>> rejected = new CopyOnWriteArrayList<>();
        public int next_hint_ms = 3000;
    }

    @PostMapping(value = "/batch", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/x-ndjson"})
    public Mono<BatchResponse> batch(@RequestHeader(value = "content-encoding", required = false) String encoding,
                                     @RequestHeader(value = "content-type", required = false) String contentType,
                                     org.springframework.http.server.reactive.ServerHttpRequest req,
                                     @RequestBody Mono<byte[]> bodyBytesMono) {
        return bodyBytesMono.map(bytes -> {
            byte[] raw = maybeGunzip(bytes, encoding);
            List<Event> events = parseEvents(raw, contentType);
            String userAgent = req.getHeaders().getFirst("user-agent");
            String xff = req.getHeaders().getFirst("x-forwarded-for");
            String clientIp = null;
            if (xff != null && !xff.isEmpty()) {
                clientIp = xff.split(",")[0].trim();
            } else if (req.getRemoteAddress() != null) {
                clientIp = req.getRemoteAddress().getAddress().getHostAddress();
            }
            BatchResponse resp = new BatchResponse();
            for (Event e : events) {
                if (e == null || e.eventId == null || e.eventName == null || e.projectId == null || e.deviceId == null) {
                    HashMap<String, String> rej = new HashMap<>();
                    rej.put("event_id", e != null ? String.valueOf(e.eventId) : "");
                    rej.put("reason", "invalid_schema");
                    resp.rejected.add(rej);
                    dlq.publish(e != null ? e.eventId : null, "invalid_schema", e == null ? null : toJsonSilently(e));
                    continue;
                }
                // enrich request-derived fields
                if (e.userAgent == null) e.userAgent = userAgent;
                if (e.clientIp == null) e.clientIp = clientIp;
                try { publisher.publish(e); resp.accepted.add(e.eventId); }
                catch (Exception ex) {
                    HashMap<String, String> rej = new HashMap<>();
                    rej.put("event_id", e.eventId);
                    rej.put("reason", "kafka_error");
                    resp.rejected.add(rej);
                    dlq.publish(e.eventId, "kafka_error", toJsonSilently(e));
                }
            }
            return resp;
        });
    }

    private List<Event> parseEvents(byte[] raw, String contentType) {
        try {
            String ct = contentType == null ? "application/json" : contentType;
            if (ct.contains("ndjson")) {
                List<Event> out = new ArrayList<>();
                String s = new String(raw);
                for (String line : s.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    out.add(om.readValue(line, Event.class));
                }
                return out;
            } else {
                // JSON 数组
                return om.readValue(raw, new TypeReference<List<Event>>(){});
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJsonSilently(Object o) {
        try { return om.writeValueAsString(o); } catch (Exception e) { return null; }
    }

    private byte[] maybeGunzip(byte[] raw, String encoding) {
        try {
            if (encoding != null && encoding.toLowerCase().contains("gzip")) {
                try (InputStream gis = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                    return gis.readAllBytes();
                }
            }
        } catch (Exception ignored) {}
        return raw;
    }
}
