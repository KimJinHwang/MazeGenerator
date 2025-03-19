plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.6.10"
    `maven-publish`
}

group = "com.stella"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

// credentials 불러오기
file(rootProject.gradle.rootProject.projectDir.path + "/credentials.gradle.kts").let {
    if (it.exists()) {
        apply(it.path)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "nexus"
            url = uri("https://maven.hqservice.kr/repository/maven-snapshots/")
            credentials {
                if (extra.has("nexusUsername") && extra.has("nexusPassword")) {
                    this.username = extra["nexusUsername"].toString()
                    this.password = extra["nexusPassword"].toString()
                }
            }
        }
    }
}