
plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (
            requested.group == "org.apache.activemq" &&
            requested.name.startsWith("artemis-")
        ) {
            useVersion("2.36.0")
            because("Align Artemis modules with Corda 4.12.5")
        }
    }
}

group = "net.corda.samples"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://download.corda.net/maven/corda-releases")
    maven("https://download.corda.net/maven/corda-dependencies")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Client RPC Corda 4.12.5
    implementation("net.corda:corda-rpc:4.12.5")
    implementation("net.corda:corda-core:4.12.5")
    // JAR générés par le build Corda principal
    implementation(files("../contracts/build/libs/contracts-1.0.jar"))
    implementation(files("../workflows/build/libs/workflows-1.0.jar"))

    implementation(project(":workflows"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.test {
    useJUnitPlatform()
}

