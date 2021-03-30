import java.net.URI

plugins {
    kotlin("jvm")
}

val dukatVersion = "0.5.8-rc.4"

repositories {
    ivy {
        url = URI("https://registry.npmjs.org/dukat/-")

        patternLayout {
            artifact("[artifact]-[revision].[ext]")
        }

        metadataSources {
            artifact()
        }
    }
}

val dukat by configurations.creating

dependencies {
    implementation(kotlinStdlib())
    implementation("org.jsoup:jsoup:1.8.2")
    dukat(group = "dukat", name="dukat", version = dukatVersion, ext = "tgz")
    implementation(tarTree(dukat.resolvedConfiguration.resolvedArtifacts.single().file).matching {
        include("**/build/runtime/dukat-cli.jar")
    })
}

task("downloadIDL", JavaExec::class) {
    main = "org.jetbrains.kotlin.tools.dukat.DownloadKt"
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn(":dukat:build")
}

task("generateStdlibFromIDL", JavaExec::class) {
    main = "org.jetbrains.kotlin.tools.dukat.LaunchKt"
    classpath = sourceSets["main"].runtimeClasspath
    dependsOn(":dukat:build")
    systemProperty("line.separator", "\n")
}
