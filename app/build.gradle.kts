plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.qdtranslate"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.qdtranslate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // Без минификации: Xposed ищет классы по именам из assets/xposed_init
            isMinifyEnabled = false
            // Подписываем debug-ключом, чтобы APK сразу ставился
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Предоставляется фреймворком (LSPosed / LSPatch) во время работы — в APK не попадает
    compileOnly("de.robv.android.xposed:api:82")
}
