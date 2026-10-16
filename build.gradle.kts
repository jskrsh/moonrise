import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-library`
    alias(libs.plugins.jmh)
    alias(libs.plugins.kotlin.jvm)
}

group = "net.mythicisland"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject.libs.caffeine)
    implementation(rootProject.libs.slf4j.api)
    implementation(rootProject.libs.bundles.configurate)

    // Adventure is provided by Paper, Velocity and Minestom, so it must never be shaded.
    compileOnlyApi(rootProject.libs.bundles.adventure)

    testImplementation(rootProject.libs.kotlin.test)
    testImplementation(rootProject.libs.bundles.adventure)
    testImplementation(platform(rootProject.libs.junit.bom))
    testRuntimeOnly(rootProject.libs.junit.platform.launcher)

    jmhImplementation(rootProject.libs.bundles.adventure)
}

kotlin {
    explicitApi()
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        languageVersion = KotlinVersion.KOTLIN_2_4
        apiVersion = KotlinVersion.KOTLIN_2_4
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.test {
    useJUnitPlatform()
}
