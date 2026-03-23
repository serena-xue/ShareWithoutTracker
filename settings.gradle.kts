pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }

        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像源
        maven { url =uri("https://maven.aliyun.com/repository/public") }    // 公共仓库
        maven { url =uri("https://maven.aliyun.com/repository/central") }  // Maven Central 镜像
        maven { url= uri("https://maven.aliyun.com/repository/google") }   // Google 镜像

        // 官方仓库（备用）
        google()          // 原始 Google 仓库（Android 项目必需）
        mavenCentral()    // 原始 Maven Central 仓库
    }
}

rootProject.name = "ShareWithoutTracker"
include(":app")

