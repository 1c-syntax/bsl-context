import java.net.URI

plugins {
    java
    id("io.franzbecker.gradle-lombok") version "3.3.0"
//    id("com.github.johnrengelman.shadow") version "5.2.0"
}

group = "com.github.1c-syntax"
version = "0.1"

val junitVersion = "5.5.2"

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

dependencies {
    testImplementation("junit", "junit", "4.12")

    compileOnly("org.projectlombok", "lombok", lombok.version)

    // тестирование
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
    testImplementation("org.assertj", "assertj-core", "3.12.2")
    testImplementation("com.ginsberg", "junit5-system-exit", "1.0.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

//tasks.jar {
//    manifest {
//        attributes["Implementation-Version"] = archiveVersion.get()
//    }
//    enabled = false
//    dependsOn(tasks.shadowJar)
//}
//tasks.shadowJar {
//    project.configurations.implementation.get().isCanBeResolved = true
//    configurations = listOf(project.configurations["implementation"])
//    archiveClassifier.set("")
//}

lombok {
    version = "1.18.12"
    sha256 = "49381508ecb02b3c173368436ef71b24c0d4418ad260e6cc98becbcf4b345406"
}
