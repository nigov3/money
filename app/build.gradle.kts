// ============================================================================
// app/build.gradle.kts — модуль приложения "ZenFlow Finance"
// Стек: Kotlin + Jetpack Compose (Material 3) + Room + Hilt + Coroutines/Flow
// ============================================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)          // компилятор плагиn Compose (обязателен с AGP 8.x)
    alias(libs.plugins.ksp)                     // Room / Hilt компиляция аннотаций быстрее KAPT
    alias(libs.plugins.hilt)
}

android {
    namespace = "ru.zenflow.finance"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.zenflow.finance"
        minSdk = 26          // Android 8.0: нужен для Notification Channels и WorkManager без доп. костылей
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room: схема БД в JSON пригодится для миграций на следующих этапах
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // ---------- Ядро / Lifecycle ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)   // collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ---------- Jetpack Compose (BOM выравнивает версии всех compose-артефактов) ----------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended) // иконки для категорий/действий
    implementation(libs.androidx.navigation.compose)              // навигация между экранами
    implementation(libs.androidx.animation)                       // кастомные переходы/анимации
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---------- Room (Flow-based persistence) ----------
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)      // Flow, suspend-запросы, withTransaction extensions
    ksp(libs.androidx.room.compiler)

    // ---------- Hilt (DI) ----------
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel() в composable

    // ---------- Корутины ----------
    implementation(libs.kotlinx.coroutines.android)

    // ---------- WorkManager ----------
    // Периодические фоновые задачи: чистка дублей, пересчёт кэшей аналитики,
    // отложенная обработка уведомлений, когда сервис был убит (см. подводные камни в README)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler /* для @HiltWorker viaassistedInject */)

    // ---------- Графики: Vico (Compose-native) ----------
    implementation(libs.vico.compose.m3)        // Material 3 обёртка над Vico charts

    // ---------- Данные/сериализация (для парсера и настроек позже) ----------
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)         // настройки темы/валюты

    // ---------- Тесты ----------
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
