import java.net.URI

plugins {
    java
    maven
    jacoco
    id("io.franzbecker.gradle-lombok") version "3.3.0"
    id("com.github.gradle-git-version-calculator") version "1.1.0"
}

group = "com.github.1c-syntax"
version = gitVersionCalculator.calculateVersion("v")

val junitVersion = "5.5.2"

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

dependencies {

    implementation("org.apache.commons", "commons-collections4", "4.4")
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

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    reports {
        html.isEnabled = true
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
    options.compilerArgs.add("-parameters")
}


lombok {
    version = "1.18.12"
    sha256 = "49381508ecb02b3c173368436ef71b24c0d4418ad260e6cc98becbcf4b345406"
}
