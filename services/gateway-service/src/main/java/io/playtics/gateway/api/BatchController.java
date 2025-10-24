package io.playtics.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.playtics.common.model.Event;
import io.playtics.gateway.kafka.AvroPublisher;
import io.playtics.gateway.kafka.DlqPublisher;
import io.playtics.gateway.config.PropsPolicy;
import io.playtics.gateway.config.JsonSchemaValidator;
import io.playtics.gateway.config.PolicyService;
import io.playtics.gateway.config.PiiPolicy;
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
    private final PropsPolicy propsPolicy;
    private final JsonSchemaValidator schemaValidator;
    private final PolicyService policyService;
    private final PiiPolicy piiPolicy;
    public BatchController(ObjectMapper om, AvroPublisher publisher, DlqPublisher dlq, PropsPolicy propsPolicy, JsonSchemaValidator schemaValidator, PolicyService policyService, PiiPolicy piiPolicy) { this.om = om; this.publisher = publisher; this.dlq = dlq; this.propsPolicy = propsPolicy; this.schemaValidator = schemaValidator; this.policyService = policyService; this.piiPolicy = piiPolicy; }

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
            if (propsPolicy.exceedsRequestLimit(raw)) {
                // 大包直接 413
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "request_too_large");
            }
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
            String apiKey = req.getHeaders().getFirst("x-api-key");
            io.playtics.gateway.config.PolicyService.Policy p = policyService.getPolicy(apiKey);
            io.playtics.gateway.config.PiiPolicy.Overrides overrides = null;
            if (p != null && (p.piiEmail != null || p.piiPhone != null || p.piiIp != null || (p.denyKeys != null && !p.denyKeys.isEmpty()) || (p.maskKeys != null && !p.maskKeys.isEmpty()))) {
                overrides = new io.playtics.gateway.config.PiiPolicy.Overrides();
                if (p.piiEmail != null) overrides.emailMode = parseMode(p.piiEmail);
                if (p.piiPhone != null) overrides.phoneMode = parseMode(p.piiPhone);
                if (p.piiIp != null) overrides.ipMode = parseIpMode(p.piiIp);
                if (p.denyKeys != null) overrides.denyKeys = new java.util.HashSet<>(p.denyKeys.stream().map(String::toLowerCase).toList());
                if (p.maskKeys != null) overrides.maskKeys = new java.util.HashSet<>(p.maskKeys.stream().map(String::toLowerCase).toList());
            }
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
                // props allowlist & depth clamp
                if (e.props != null) {
                    if (p != null && p.propsAllowlist != null && !p.propsAllowlist.isEmpty()) {
                        e.props = propsPolicy.filterWithAllowlist(e.props, p.propsAllowlist);
                    } else {
                        e.props = propsPolicy.filter(e.props);
                    }
                }
                // PII: block keys
                if (e.props != null && piiPolicy.hasBlockedKeys(e.props, overrides)) {
                    HashMap<String, String> rej = new HashMap<>();
                    rej.put("event_id", e.eventId);
                    rej.put("reason", "pii_blocked");
                    resp.rejected.add(rej);
                    dlq.publish(e.eventId, "pii_blocked", toJsonSilently(e));
                    continue;
                }
                // PII: sanitize props and client_ip, user_id
                if (e.props != null) e.props = piiPolicy.sanitizeProps(e.props, overrides);
                e.clientIp = piiPolicy.sanitizeClientIp(e.clientIp, overrides);
                if (e.userId != null) {
                    Map<String,Object> tmp = new java.util.HashMap<>(); tmp.put("user_id", e.userId);
                    tmp = piiPolicy.sanitizeProps(tmp, overrides);
                    Object v = tmp.get("user_id");
                    if (v == null) e.userId = null; else e.userId = String.valueOf(v);
                }
                // size check per event
                if (propsPolicy.exceedsEventLimit(e)) {
                    HashMap<String, String> rej = new HashMap<>();
                    rej.put("event_id", e.eventId);
                    rej.put("reason", "payload_too_large");
                    resp.rejected.add(rej);
                    dlq.publish(e.eventId, "payload_too_large", toJsonSilently(e));
                    continue;
                }
                // JSON Schema validate
                String err = schemaValidator.validate(e);
                if (err != null) {
                    HashMap<String, String> rej = new HashMap<>();
                    rej.put("event_id", e.eventId);
                    rej.put("reason", "invalid_schema");
                    resp.rejected.add(rej);
                    dlq.publish(e.eventId, "invalid_schema", toJsonSilently(e));
                    continue;
                }
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

    // helpers to parse PII modes from strings
    private io.playtics.gateway.config.PiiPolicy.Mode parseMode(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "allow" -> io.playtics.gateway.config.PiiPolicy.Mode.ALLOW;
            case "drop" -> io.playtics.gateway.config.PiiPolicy.Mode.DROP;
            default -> io.playtics.gateway.config.PiiPolicy.Mode.MASK;
        };
    }
    private io.playtics.gateway.config.PiiPolicy.IpMode parseIpMode(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "allow" -> io.playtics.gateway.config.PiiPolicy.IpMode.ALLOW;
            case "drop" -> io.playtics.gateway.config.PiiPolicy.IpMode.DROP;
            default -> io.playtics.gateway.config.PiiPolicy.IpMode.COARSE;
        };
    }
}
