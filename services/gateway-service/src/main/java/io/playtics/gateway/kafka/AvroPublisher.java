package io.playtics.gateway.kafka;

import io.playtics.common.model.Event;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.Future;

@Component
public class AvroPublisher {
    @Value("${playtics.kafka.bootstrap}")
    private String bootstrap;

    @Value("${playtics.kafka.topic}")
    private String topic;

    @Value("${playtics.kafka.registryUrl}")
    private String registryUrl;

    @Value("classpath:schemas/playtics-event.avsc")
    private Resource avroSchemaRes;

    private KafkaProducer<String, Object> producer;
    private Schema schema;
    private final ObjectMapper om;

    public AvroPublisher(ObjectMapper om) { this.om = om; }

    @PostConstruct
    public void init() throws Exception {
        try (InputStream is = avroSchemaRes.getInputStream()) {
            String s = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            schema = new Schema.Parser().parse(s);
        }
        Properties p = new Properties();
        p.put("bootstrap.servers", bootstrap);
        p.put("acks", "all");
        p.put("linger.ms", "5");
        p.put("batch.size", "65536");
        p.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        // Apicurio Avro 序列化器
        p.put("value.serializer", "io.apicurio.registry.serde.avro.AvroKafkaSerializer");
        p.put("apicurio.registry.url", registryUrl);
        p.put("apicurio.registry.auto-register", true);
        p.put("apicurio.registry.find-latest", true);
        // subject 策略: topic-value
        p.put("apicurio.registry.serde.subject","topic-value");
        producer = new KafkaProducer<>(p);
    }

    public Future<RecordMetadata> publish(Event e) {
        GenericRecord gr = new GenericData.Record(schema);
        gr.put("event_id", e.eventId);
        gr.put("event_name", e.eventName);
        gr.put("project_id", e.projectId);
        gr.put("user_id", e.userId);
        gr.put("device_id", e.deviceId);
        gr.put("session_id", e.sessionId);
        gr.put("ts_client", e.tsClient * 1000L); // assume ms -> micros
        gr.put("ts_server", e.tsServer == null ? null : e.tsServer * 1000L);
        gr.put("platform", e.platform);
        gr.put("app_version", e.appVersion);
        gr.put("country", e.country);
        gr.put("revenue_amount", null);
        gr.put("revenue_currency", null);
        try {
            gr.put("props_json", e.props == null ? "{}" : om.writeValueAsString(e.props));
        } catch (Exception ex) {
            gr.put("props_json", "{}");
        }
        return producer.send(new ProducerRecord<>(topic, e.projectId, gr));
    }
}
