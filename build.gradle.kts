import java.net.URI

plugins {
    java
    id("io.franzbecker.gradle-lombok") version "3.3.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

group = "com.github.1c-syntax"
version = "0.1"

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

dependencies {
    testImplementation("junit", "junit", "4.12")

    // com.github.1c-syntax
    implementation("com.github.1c-syntax", "utils", "4034e83681b")
    
    compileOnly("org.projectlombok", "lombok", lombok.version)
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = archiveVersion.get()
    }
    enabled = false
    dependsOn(tasks.shadowJar)
}
tasks.shadowJar {
    project.configurations.implementation.get().isCanBeResolved = true
    configurations = listOf(project.configurations["implementation"])
    archiveClassifier.set("")
}

lombok {
    version = "1.18.12"
    sha256 = "49381508ecb02b3c173368436ef71b24c0d4418ad260e6cc98becbcf4b345406"
}
