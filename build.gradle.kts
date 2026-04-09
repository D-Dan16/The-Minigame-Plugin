plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm") version "2.2.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://maven.enginehub.org/repo")
    }

    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }

    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
    mavenCentral()
}

dependencies {
    api(libs.xyz.jpenilla.run.task)
    implementation(kotlin("stdlib"))
    api(libs.org.jetbrains.kotlinx.multik.core)
    api(libs.org.jetbrains.kotlinx.multik.kotlin.jvm)
    api(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
    testImplementation(libs.org.jetbrains.kotlin.kotlin.test)
    compileOnly(libs.com.sk89q.worldedit.worldedit.bukkit)
    compileOnly(libs.io.papermc.paper.paper.api)
}

group = "me.dirtydan16"
version = "5.3.0"
description = "MinigamePlugin"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<xyz.jpenilla.runtask.task.AbstractRun>().configureEach {
    javaLauncher = javaToolchains.launcherFor {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(25)
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
        downloadPlugins {}
    }
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}