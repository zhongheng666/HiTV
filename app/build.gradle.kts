plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android") // 启用 Hilt
    id("com.google.devtools.ksp")        // 启用 KSP
}

android {
    namespace = "com.htlac.hitv" // 已修改为 hitv
    compileSdk = 36

    defaultConfig {
        applicationId = "com.htlac.hitv" // 已修改为 hitv
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Hilt 2.57.2 和 Room 2.8.4 强制需要 Java 17
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Kotlin 2.0+ 推荐的全新编译器配置 DSL，用于替代旧版 kotlinOptions
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ================= 核心依赖 =================

    // 1. UI: Compose for TV (Material3 1.0.1)
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11") // TV 基础焦点控制
    implementation("androidx.tv:tv-material:1.0.1")

    // 2. 播放器: Media3 1.9.0 & Jellyfin FFmpeg 扩展
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-ui:1.9.0")
    implementation("androidx.media3:media3-session:1.9.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.9.0")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    // 2.1 备用播放器: mpv (根据设计文档添加)
    implementation("dev.jdtech.mpv:libmpv:0.5.1")

    // 3. 网络: Retrofit 3.0.0 + OkHttp 4.12
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 4. 依赖注入: Hilt 2.57.2
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // 5. 图片加载: Coil 3.3.0
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // 6. 数据库: Room 2.8.4
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // 7. 路由导航：Compose 官方大管家
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // 8. 图标库: Material Extended (包含 QrCode2 等更多图标)
    implementation("androidx.compose.material:material-icons-extended:1.6.1")

    // 9. 微型本地服务器与二维码生成
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")

    // 10. 本地数据持久化：DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // 11. 启动页 (Splash Screen)
    implementation("androidx.core:core-splashscreen:1.0.1")
}