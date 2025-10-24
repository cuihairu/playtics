package io.playtics.jobs.enrich;

import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class EventsEnrichJob {
    public static void main(String[] args) throws Exception {
        String bootstrap = System.getProperty("kafka.bootstrap", "localhost:9092");
        String registry = System.getProperty("registry.url", "http://localhost:8081/apis/registry/v2");
        String topic = System.getProperty("kafka.topic", "playtics.events_raw");
        String chUrl = System.getProperty("clickhouse.url", "jdbc:clickhouse://localhost:8123/default");
        String chUser = System.getProperty("clickhouse.user", "default");
        String chPass = System.getProperty("clickhouse.pass", "");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<GenericRecord> source = KafkaSource.<GenericRecord>builder()
                .setBootstrapServers(bootstrap)
                .setTopics(topic)
                .setGroupId("playtics-events-enrich")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setDeserializer(new ApicurioAvroFlinkDeserializer(registry))
                .build();

        DataStreamSource<GenericRecord> stream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "events-raw");

        var mapped = stream.map((MapFunction<GenericRecord, EventRow>) record -> {
            EventRow row = new EventRow();
            row.project_id = str(record.get("project_id"));
            row.ts_server = toTimestamp(record.get("ts_server"));
            if (row.ts_server == null) row.ts_server = new Timestamp(System.currentTimeMillis());
            row.event_id = str(record.get("event_id"));
            row.event_name = str(record.get("event_name"));
            row.user_id = nz(str(record.get("user_id")));
            row.device_id = str(record.get("device_id"));
            row.session_id = nz(str(record.get("session_id")));
            row.platform = nz(str(record.get("platform")));
            row.app_version = nz(str(record.get("app_version")));
            row.country = nz(str(record.get("country")));
            Object pj = record.get("props_json");
            row.props_json = pj == null ? "{}" : pj.toString();
            row.revenue_amount = BigDecimal.ZERO; // 表为非 Nullable，使用默认 0
            row.revenue_currency = "USD";
            return row;
        });

        var sink = JdbcSink.sink(
                "INSERT INTO events (project_id, ts_server, event_id, event_name, user_id, device_id, session_id, platform, app_version, country, props_json, revenue_amount, revenue_currency) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (ps, r) -> {
                    ps.setString(1, r.project_id);
                    ps.setTimestamp(2, r.ts_server);
                    ps.setString(3, r.event_id);
                    ps.setString(4, r.event_name);
                    ps.setString(5, r.user_id);
                    ps.setString(6, r.device_id);
                    ps.setString(7, r.session_id);
                    ps.setString(8, r.platform);
                    ps.setString(9, r.app_version);
                    ps.setString(10, r.country);
                    ps.setString(11, r.props_json);
                    ps.setBigDecimal(12, r.revenue_amount);
                    ps.setString(13, r.revenue_currency);
                },
                JdbcExecutionOptions.builder().withBatchIntervalMs(200).withBatchSize(2000).withMaxRetries(3).build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(chUrl)
                        .withDriverName("com.clickhouse.jdbc.ClickHouseDriver")
                        .withUsername(chUser)
                        .withPassword(chPass)
                        .build()
        );

        mapped.addSink(sink).name("clickhouse-sink");

        env.execute("playtics-events-enrich");
    }

    private static String str(Object v) { return v == null ? null : v.toString(); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static Timestamp toTimestamp(Object micros) {
        if (micros == null) return null;
        if (micros instanceof Long) return new Timestamp(((Long) micros) / 1000L);
        try { return new Timestamp(Long.parseLong(micros.toString()) / 1000L); } catch (Exception e) { return null; }
    }

    public static class EventRow {
        public String project_id;
        public Timestamp ts_server;
        public String event_id;
        public String event_name;
        public String user_id;
        public String device_id;
        public String session_id;
        public String platform;
        public String app_version;
        public String country;
        public String props_json;
        public BigDecimal revenue_amount;
        public String revenue_currency;
    }
}
