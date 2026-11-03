plugins {
    id("com.android.library")
    id("maven-publish")
    signing
}

group = "io.github.josense"
version = "1.0.0"


android {
    namespace = "com.josense.boxshadow"
    compileSdk = 36
    defaultConfig { minSdk = 16 }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

val repositoryDir = layout.buildDirectory.dir("distribution-repository/$group/$version")
publishing {
    repositories {
        maven {
            name = "distribution"
            url = repositoryDir.get().asFile.toURI()
        }
    }
}

val apiDocs = tasks.register<Jar>("apiDocs") {
    archiveClassifier.set("javadoc")
    from(rootProject.file("docs/api"))
}

val signingKey = providers.environmentVariable("BOXSHADOW_SIGNING_KEY")
val signingPassword = providers.environmentVariable("BOXSHADOW_SIGNING_PASSWORD")
afterEvaluate {
    publishing.publications.create<MavenPublication>("release") {
        from(components["release"])
        artifact(apiDocs)
        pom {
            name.set("BoxShadowLayout")
            description.set("Android XML container with inner and outer shadows, rounded corners, drawable backgrounds and borders.")
            url.set("https://github.com/Josense/BoxShadow")
            licenses {
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("Josense")
                    name.set("Josense")
                    url.set("https://github.com/Josense")
                }
            }
            scm {
                url.set("https://github.com/Josense/BoxShadow")
                connection.set("scm:git:https://github.com/Josense/BoxShadow.git")
                developerConnection.set("scm:git:ssh://git@github.com/Josense/BoxShadow.git")
            }
        }
    }
    if (signingKey.isPresent) {
        signing {
            useInMemoryPgpKeys(signingKey.get(), signingPassword.getOrElse(""))
            sign(publishing.publications["release"])
        }
    }
}

tasks.register<Zip>("packageDistribution") {
    dependsOn("publishReleasePublicationToDistributionRepository")
    from(repositoryDir) { exclude("**/*.asc", "**/*.asc.*") }
    from(rootProject.file("LICENSE"))
    archiveFileName.set("boxshadow-$version-maven.zip")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("dist"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}


// Only versioned deployment files belong in the Central upload.
tasks.register<Zip>("packageCentralBundle") {
    dependsOn("publishReleasePublicationToDistributionRepository")
    val artifactPath = "io/github/josense/boxshadow/$version"
    from(repositoryDir) { include("$artifactPath/**") }
    archiveFileName.set("boxshadow-$version-central.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central"))
    inputs.property("signingConfigured", signingKey.isPresent)
    doFirst {
        check(signingKey.isPresent) { "Set BOXSHADOW_SIGNING_KEY to your ASCII-armored PGP private key." }
        val dir = repositoryDir.get().dir(artifactPath).asFile
        for (suffix in listOf(".aar", ".pom", ".module", "-sources.jar", "-javadoc.jar")) {
            check(dir.resolve("boxshadow-$version$suffix.asc").isFile) { "Missing signature for $suffix" }
        }
    }
}
