pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Solo per sherpa-onnx, il motore della voce locale.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.k2-fsa.*") } }
    }
}
rootProject.name = "Brieffo"
include(":app")
