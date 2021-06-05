import java.net.URI

plugins {
    java
    `maven-publish`
    jacoco
    id("io.freefair.lombok") version "6.0.0-m2"
    id("com.github.gradle-git-version-calculator") version "1.1.0"
    id("com.github.ben-manes.versions") version "0.38.0"
}

group = "io.github.1c-syntax"
version = gitVersionCalculator.calculateVersion("v")

val junitVersion = "5.8.0-M1"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {

    implementation("org.apache.commons", "commons-collections4", "4.4")

    // тестирование
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
    testImplementation("org.assertj", "assertj-core", "3.19.0")
    testImplementation("com.ginsberg", "junit5-system-exit", "1.1.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                description.set("Context provider API for 1C:Enterprise 8 language (BSL).")
                url.set("https://github.com/1c-syntax/bsl-context")
                licenses {
                    license {
                        name.set("GNU LGPL 3")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                        distribution.set("repo")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/1c-syntax/bsl-context.git")
                    developerConnection.set("scm:git:git@github.com:1c-syntax/bsl-context.git")
                    url.set("https://github.com/1c-syntax/bsl-context")
                }
            }
        }
    }
}