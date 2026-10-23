plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.example.mi12liteledtester"
    compileSdk = 35
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    defaultConfig { applicationId = "com.example.mi12liteledtester"; minSdk = 26; targetSdk = 28; versionCode = 1; versionName = "1.0" }
    signingConfigs {
        create("ciRelease") {
            val store = providers.gradleProperty("releaseStoreFile").orNull
            if (store != null) {
                storeFile = file(store)
                storePassword = providers.gradleProperty("releaseStorePassword").orNull
                keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
                keyPassword = providers.gradleProperty("releaseKeyPassword").orNull
            }
        }
    }
    buildTypes { getByName("release") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("ciRelease") } }


}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
