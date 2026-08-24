import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.ctjsoft.devops"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local(providers.gradleProperty("ideaLocalPath"))
        bundledPlugin("Git4Idea")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        name = "Issue Link Push"
        version = project.version.toString()

        ideaVersion {
            // The implementation uses APIs available since the 2024.3 platform
            // and deliberately has no upper bound for forward compatibility.
            sinceBuild = "243"
        }
    }

    pluginVerification {
        ides {
            local(providers.gradleProperty("ideaLocalPath"))
        }
    }

    // Marketplace credentials and signing material are supplied only through
    // environment variables/Gradle properties and must never be committed.
    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget = JvmTarget.JVM_21
}

tasks.test {
    useJUnitPlatform()
}
