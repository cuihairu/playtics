plugins {
  id("org.springframework.boot") version "3.3.3" apply false
  id("io.spring.dependency-management") version "1.1.6" apply false
  id("java") apply false
}

allprojects {
  group = "io.playtics"
  version = "0.1.0"

  repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven")
  }
}

subprojects {
  apply(plugin = "java")
  tasks.withType<JavaCompile> { options.release.set(21) }
}
