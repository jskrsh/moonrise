import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java-library")
    alias(libs.plugins.jmh)
    alias(libs.plugins.kraken)
    alias(libs.plugins.kotlin.jvm)
}

group = "net.mythicisland"
version = "1.1.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject.libs.caffeine)
    implementation(rootProject.libs.slf4j.api)
    implementation(rootProject.libs.bundles.configurate)

    compileOnlyApi(rootProject.libs.bundles.adventure)

    testImplementation(rootProject.libs.kotlin.test)
    testImplementation(rootProject.libs.bundles.adventure)

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

maven {
    artifactId = "moonrise"
    url = "https://github.com/xxjanisxx/moonrise"
    description = "A small and fast translation library based on Adventure MiniMessage"

    licenses {
        license {
            name = "Apache 2.0"
            url = "https://www.apache.org/licenses/"
        }
    }

    developers {
        developer {
            name = "Janis"
        }
    }

    scm {
        url = "https://github.com/xxjanisxx/moonrise"
        connection = "scm:git:https://github.com/xxjanisxx/moonrise.git"
        developerConnection = "scm:git:ssh://git@github.com/xxjanisxx/moonrise.git"
    }

    ciManagement {
        system = "GitHub Actions"
        url = "https://github.com/xxjanisxx/moonrise/actions"
    }

    repositories {
        register("releases") {
            url.set("https://repo.mythicisland.club/releases")
            username.set(System.getenv("MYTHICISLAND_USER"))
            password.set(System.getenv("MYTHICISLAND_PASSWORD"))
        }
        register("snapshots") {
            url.set("https://repo.mythicisland.club/snapshots")
            username.set(System.getenv("MYTHICISLAND_USER"))
            password.set(System.getenv("MYTHICISLAND_PASSWORD"))
        }
    }
}
