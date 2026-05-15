plugins {
    id("java")
    id("java-library")
    `maven-publish`
}

group = "io.github.1c-syntax"

version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    // bsl-help-toc-parser подключаем через jitpack по SHA коммита master:
    // версия 0.2.0 (переход на 1c-syntax fork ANTLR + Java 21) ещё не
    // опубликована в Maven Central, на jitpack доступна сразу после merge.
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.1c-syntax:bsl-help-toc-parser:4452a79")
    implementation("org.jsoup:jsoup:1.18.3")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("bsl-context")
                description.set("Парсер синтакс-помощника платформы 1С:Предприятие. " +
                    "Извлекает типы, методы, свойства, события, конструкторы и их " +
                    "метаданные (sinceVersion, deprecated, default values, examples) " +
                    "из .hbk-файлов.")
                url.set("https://github.com/1c-syntax/bsl-context")
                licenses {
                    license {
                        name.set("LGPL-3.0-or-later")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/1c-syntax/bsl-context")
                    connection.set("scm:git:https://github.com/1c-syntax/bsl-context.git")
                }
            }
        }
    }
}

tasks.withType<Javadoc> {
    isFailOnError = false
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
    }
}
tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
