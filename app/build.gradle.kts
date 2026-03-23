plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.sharewithouttracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sharewithouttracker"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

//        现代手机绝大多数是 arm64-v8a。只打这一个包体积会缩小 50% 以上
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            // 1. 启用代码混淆和压缩
            isMinifyEnabled = true
            // 2. 启用资源压缩（去掉未使用的图片、布局等）
            isShrinkResources = true
            // 默认混淆规则
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // AndroidX Core 基础库 (提供 NotificationCompat 等支持)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Kotlin 协程库 (用于异步执行网络请求)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // OkHttp 网络请求库 (用于调用 Telegram Bot API)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 测试依赖
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("org.jsoup:jsoup:1.22.1")
    implementation("com.microsoft.playwright:playwright:1.58.0")

//    implementation("androidx.core:core-ktx:1.13.1")
//    implementation(libs.androidx.appcompat)
//    implementation(libs.material)
//    implementation("androidx.activity:activity:1.9.0")
//    implementation(libs.androidx.constraintlayout)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.androidx.junit)
//    androidTestImplementation(libs.androidx.espresso.core)
//
//    // define a BOM and its version
//    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.3.0"))
//
//    // define any required OkHttp artifacts without version
//    implementation("com.squareup.okhttp3:okhttp")
//    implementation("com.squareup.okhttp3:logging-interceptor")
}