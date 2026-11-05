group = "com.lotusplugins"

plugins {
  `java-library`
  `maven-publish`
  signing
}

repositories {
  mavenCentral()
  maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
  maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
  compileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
  options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

java {
  withSourcesJar()
  withJavadocJar()
}

tasks.javadoc {
  (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
  publications {
    register<MavenPublication>("api") {
      artifactId = "warrant-api"
      from(components["java"])
      pom {
        name.set("Warrant API")
        description.set("Events and model types for plugins that integrate with Warrant.")
        url.set("https://github.com/bradenk04/punishments")
        licenses {
          license {
            name.set("GNU General Public License, Version 3")
            url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
          }
        }
        developers {
          developer {
            id.set("bradenk04")
            name.set("Braden")
            url.set("https://github.com/bradenk04")
          }
        }
        scm {
          url.set("https://github.com/bradenk04/punishments")
          connection.set("scm:git:https://github.com/bradenk04/punishments.git")
          developerConnection.set("scm:git:ssh://git@github.com/bradenk04/punishments.git")
        }
      }
    }
  }
}

signing {
  val signingKey = providers.environmentVariable("SIGNING_KEY")
  setRequired(signingKey.isPresent)
  useInMemoryPgpKeys(signingKey.orNull, providers.environmentVariable("SIGNING_PASSWORD").orNull)
  sign(publishing.publications)
}
