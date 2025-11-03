plugins {
  id("org.springframework.boot")
  id("io.spring.dependency-management")
  id("java")
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  // JSON Schema validation for experiment config
  implementation("com.networknt:json-schema-validator:1.0.91")
  runtimeOnly("com.h2database:h2")
}

springBoot { mainClass.set("io.pit.control.Application") }
