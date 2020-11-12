import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    application
}
group = "me.artem"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ru.tinkoff.invest:openapi-java-sdk-core:0.4.1")
    implementation("ru.tinkoff.invest:openapi-java-sdk-java8:0.4.1")
    implementation("org.reactivestreams:reactive-streams-examples:1.0.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.3")

    testImplementation(kotlin("test-junit5"))
}
tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}
application {
    mainClassName = "MainKt"
}