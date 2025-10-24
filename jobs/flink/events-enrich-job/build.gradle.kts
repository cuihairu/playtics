plugins {
  id("java")
}

dependencies {
  implementation("org.apache.flink:flink-streaming-java:1.19.0")
  implementation("org.apache.flink:flink-clients:1.19.0")
  implementation("org.apache.flink:flink-connector-kafka:3.2.0-1.19")
  implementation("org.apache.flink:flink-connector-jdbc:3.2.0-1.19")
  implementation("org.apache.avro:avro:1.11.3")
  implementation("io.apicurio:apicurio-registry-serdes-avro-serde:2.6.5.Final")
  implementation("com.clickhouse:clickhouse-jdbc:0.6.8")
  implementation("com.maxmind.geoip2:geoip2:4.2.0")
  implementation("nl.basjes.parse.useragent:yauaa:7.24")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

tasks.withType<JavaCompile> { options.release.set(21) }
