plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.jls.makeitrun.ftms"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // En "implementation" et non "api" : les types Nordic ne doivent pas fuiter hors du module,
    // pour que l'application ne dependre que de l'API FTMS exposee ici.
    implementation(libs.nordic.ble.client)
    implementation(libs.nordic.ble.scanner)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
