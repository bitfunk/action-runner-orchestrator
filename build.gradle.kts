plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"

    // https://github.com/ben-manes/gradle-versions-plugin
    id("com.github.ben-manes.versions") version "0.43.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://dl.bintray.com/jakubriegel/kotlin-shell")
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.RequiresOptIn")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host:1.7.20")
    implementation("org.jetbrains.kotlin:kotlin-scripting-dependencies:1.7.20")
    implementation("org.jetbrains.kotlin:kotlin-script-util:1.7.20")

    // Script dependencies
    implementation("org.jetbrains.kotlin:kotlin-script-runtime:1.7.20")
    implementation("org.jetbrains.kotlin:kotlin-main-kts:1.7.20")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("eu.jrie.jetbrains:kotlin-shell-core:0.2.1")

    implementation("io.ktor:ktor-client-core-jvm:2.1.3")
    implementation("io.ktor:ktor-client-cio-jvm:2.1.3")
    implementation("io.ktor:ktor-client-logging-jvm:2.1.3")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:2.1.3")
    implementation("io.ktor:ktor-serialization-gson-jvm:2.1.3")
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    resolutionStrategy {
        componentSelection {
            all {
                if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
                    reject("Release candidate")
                }
            }
        }
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "7.5.1"
    distributionType = Wrapper.DistributionType.ALL
}
