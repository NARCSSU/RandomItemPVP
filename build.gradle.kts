plugins {
    `java`
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // For annotations if needed
    compileOnly("org.jetbrains:annotations:24.1.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(
            "name" to project.name,
            "version" to project.version
        )
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}




