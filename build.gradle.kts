plugins {
    java
}

group = "com.citycore"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")                          // Vault
    maven("https://maven.citizensnpcs.co/repo")           // Citizens2
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")               // Vault
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") {
        exclude(group = "*", module = "*")                          // Ignore les dépendances transitives
    }                                                               // Citizens2
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
}