import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.0"
}

group = "site.neworld"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:utils"))
    implementation(project(":modules:cio"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}