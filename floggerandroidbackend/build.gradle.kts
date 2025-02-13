plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.tbohne.android.flogger.backend"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.logging", "stdout")
                it.systemProperty(
                    "flogger.backend_factory",
                    "com.tbohne.android.flogger.backend.AndroidBackendFactory"
                )
                //testLogging {
                //    showStandardStreams = true
                //    events 'passed', 'skipped', 'failed', 'standardOut', 'standardError'
                //}
            }
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.flogger)
    implementation(libs.checker.qual)
    implementation(libs.flogger.system.backend)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.hamcrest.all)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}