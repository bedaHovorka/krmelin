plugins {
    kotlin("jvm") version "2.1.21"
    application
    id("com.gradleup.shadow") version "8.3.6"
    jacoco
}

group = "krmelin"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("krmelin.MainKt")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("krmelin.lexer", "krmelin.parser", "krmelin.resolve", "krmelin.types")
            limit {
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("krmelin")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())
    mergeServiceFiles()
}

// Rename the plain jar so it does not collide with the shadow jar output file.
tasks.named<Jar>("jar") {
    archiveClassifier.set("slim")
}

kotlin {
    jvmToolchain(21)
}
