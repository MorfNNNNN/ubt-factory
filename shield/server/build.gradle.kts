plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

application {
    mainClass.set("ru.ubt.familyshield.server.MainKt")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.h2)
    implementation(libs.logback)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.shadowJar {
    archiveFileName.set("family-shield-server.jar")
    mergeServiceFiles()
}

tasks.register("fatJar") {
    group = "build"
    description = "Self-contained server jar (alias for shadowJar)"
    dependsOn(tasks.shadowJar)
}
