plugins {
    id("java")
    `java-library`
    jacoco
}

group = "com.kuan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    implementation(project(":di-container-module"))
    // https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-server
    implementation("org.eclipse.jetty:jetty-server:11.0.11")
    // https://mvnrepository.com/artifact/org.eclipse.jetty/jetty-servlet
    implementation("org.eclipse.jetty:jetty-servlet:11.0.11")
    // https://mvnrepository.com/artifact/jakarta.ws.rs/jakarta.ws.rs-api
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    // https://mvnrepository.com/artifact/jakarta.servlet/jakarta.servlet-api
    implementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.mockito:mockito-core:4.7.0")

    implementation("org.slf4j:slf4j-api:2.0.0")
    implementation("org.slf4j:slf4j-simple:2.0.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}