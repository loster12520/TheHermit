plugins {
    kotlin("multiplatform") version "2.1.0"
}

group = "com.lignting.front"
version = "0.1-SNAPSHOT"

kotlin {
    js {
        browser {
        }
        binaries.executable()
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test")) // 会自动引入所有的平台依赖项
            implementation(npm("axios", "1.8.2"))
            implementation(npm("react", "19.0.0"))
            implementation(npm("react-dom", "19.0.0"))
            implementation(npm("react-router-dom", "7.6.0"))
            implementation(npm("antd", "5.25.1"))
        }
    }
}

//rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
//    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().downloadBaseUrl = "https://kkgithub.com/yarnpkg/yarn/releases/download"
//}