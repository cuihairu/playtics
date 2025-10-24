plugins {
  id("org.springframework.boot")
  id("io.spring.dependency-management")
  id("java")
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  runtimeOnly("com.h2database:h2")
}

springBoot { mainClass.set("io.playtics.control.Application") }
