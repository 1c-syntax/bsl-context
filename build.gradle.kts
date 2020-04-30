import java.net.URI

plugins {
    java
    id("io.franzbecker.gradle-lombok") version "3.3.0"
}

group = "com.github.1c-syntax"
version = "0.1"

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

dependencies {
    testImplementation("junit", "junit", "4.12")

    compileOnly("org.projectlombok", "lombok", lombok.version)
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}

lombok {
    version = "1.18.12"
    sha256 = "49381508ecb02b3c173368436ef71b24c0d4418ad260e6cc98becbcf4b345406"
}
