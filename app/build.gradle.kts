plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.tetrisprojem"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tetrisprojem"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}
dependencies {

    // --- ANDROIDX TEMEL BAĞIMLILIKLARI ---
    // Core KTX modülü, Kotlin uzantı fonksiyonları sağlar
    implementation(libs.androidx.core.ktx)
    // Lifecycle KTX, yaşam döngüsü bileşenleri için Kotlin uzantıları sağlar (lifecycleScope dahil)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Activity Compose, ComponentActivity'yi Compose ile kullanmayı sağlar
    implementation(libs.androidx.activity.compose)


    // --- COMPOSE BAĞIMLILIKLARI (BOM KULLANILARAK) ---
    // Compose BOM (Bill of Materials), tüm Compose kütüphanelerinin uyumlu versiyonlarını bir arada tutar.
    // libs.versions.toml dosyanızdaki 'androidx.compose.bom' versiyonunun güncel olduğundan emin olun (örneğin '2024.04.00').
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI Toolkit'in ana bileşenleri
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview) // Önizleme için
    // Compose Material3 Tasarım Sistemi bileşenleri
    implementation(libs.androidx.material3)
    // Eğer temel Foundation bileşenlerine (örneğin Canvas, Column, Row gibi) ihtiyacın varsa:
    implementation("androidx.compose.foundation:foundation")
    // Eğer LiveData ile Compose'u birleştireceksen:
    implementation("androidx.compose.runtime:runtime-livedata")


    // --- KOTLIN COROUTINES BAĞIMLILIKLARI ---
    // Coroutine'lerin temel işlevselliği
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1") // En güncel ve stabil versiyonu kullanın
    // Android'e özel coroutine dispatchers (Dispatchers.Main gibi)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1") // En güncel ve stabil versiyonu kullanın


    // --- FIREBASE BAĞIMLILIKLARI ---
    // Firebase BOM, tüm Firebase kütüphanelerinin uyumlu versiyonlarını yönetir.
    // Lütfen Firebase konsolunuzdaki proje ayarlarına göre en güncel versiyonu kullanın.
    // Örnek: 33.11.0 Mayıs 2025 itibarıyla güncel olabilir.
    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))

    // Firebase Authentication (Kimlik Doğrulama) KTX versiyonu
    implementation("com.google.firebase:firebase-auth-ktx")
    // Cloud Firestore (Gerçek zamanlı veritabanı) KTX versiyonu
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Google Play Services Coroutines Uzantısı - Firebase'in Task API'lerini coroutine'lerle kullanmayı sağlar (.await() metodu için KRİTİK!)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1") // Coroutine versiyonuyla uyumlu olmalı


    // --- EKSTRA GOOGLE SERVİSLERİ (Gereklilik halinde) ---
    // Google ile giriş yapmak istersen (örn. FirebaseAuth.GoogleAuthProvider)
    implementation("com.google.android.gms:play-services-auth:21.2.0") // En güncel versiyonu kontrol edin


    // --- TEST BAĞIMLILIKLARI ---
    // JUnit 4 için test
    testImplementation(libs.junit)
    // AndroidX JUnit testleri
    androidTestImplementation(libs.androidx.junit)
    // Espresso UI testleri
    androidTestImplementation(libs.androidx.espresso.core)
    // Android Compose UI testleri (BOM ile versiyonu otomatik gelir)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // Compose UI Tooling (önizleme ve hata ayıklama için)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}