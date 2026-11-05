plugins {
  id("com.gradleup.nmcp.settings") version "1.6.2"
}

nmcpSettings {
  centralPortal {
    username = System.getenv("CENTRAL_USERNAME").orEmpty()
    password = System.getenv("CENTRAL_PASSWORD").orEmpty()
  }
}

rootProject.name = "warrant"
include("api")
