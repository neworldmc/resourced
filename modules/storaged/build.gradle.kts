plugins {
    kotlin("jvm")
}

group = "site.neworld"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":modules:utils"))
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
}