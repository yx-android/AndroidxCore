pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven(url = "https://jitpack.io") // JitPack 仍需保留
        maven(
            url = "https://oss.sonatype.org/content/groups/public"
        )

        maven(url = "https://repo.iyunxiao.com/artifactory/android-group/")
        maven(url = "https://repo.iyunxiao.com/artifactory/maven-group/")
//        maven(url = "https://s01.oss.sonatype.org/content/groups/public")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://jitpack.io") // JitPack 仍需保留
        mavenCentral()
        maven(
            url = "https://oss.sonatype.org/content/groups/public"
        )
        maven(url = "https://repo.iyunxiao.com/artifactory/android-group/")
        maven(url = "https://repo.iyunxiao.com/artifactory/maven-group/")
    }
}

rootProject.name = "AndroidxCore"
include(":AndroidxCore")
include(":kits")
include(":floatball")
include(":kiosk")
project(":kiosk").buildFileName = "build.gradle.kts"
include(":ByWebView")
project(":ByWebView").buildFileName = "build.gradle.kts"
include(":appmanager")
