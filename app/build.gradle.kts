plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Room writes the schema out here. With this in place a future migration can be
// verified against the real schema at build time — a broken one fails the build
// instead of failing on a shopkeeper's phone.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.innovation313.roshankhata"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.innovation313.roshankhata"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            // The keystore is supplied by CI as a decoded file + secrets, never
            // committed. A stable key matters for two reasons: Google Sign-In is
            // registered against ONE SHA-1 that must not change, and the Play
            // Store will only accept updates signed by the same key forever.
            //
            // If the env vars aren't present (a local build without the key),
            // this stays null and the build falls back to debug signing below —
            // so the project still builds for anyone, just without a release key.
            val storePathValue = System.getenv("RELEASE_STORE_FILE")
            if (storePathValue != null) {
                storeFile = file(storePathValue)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release key when it's available, otherwise leave unsigned
            // (CI signs release; local debug builds don't need it).
            if (System.getenv("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
            // Sign the DEBUG build with the release key too when it's present,
            // so the APK the owner installs from CI has the STABLE SHA-1 that
            // Google Sign-In needs. Without this, each CI debug build would be
            // signed by a throwaway key and Drive sign-in would break on every
            // update.
            if (System.getenv("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Biometric: fingerprint/face with automatic fallback to the device
    // PIN, pattern, or password. Deliberately NOT a home-grown PIN — the OS
    // already stores credentials in hardware-backed secure storage, and we
    // have no business duplicating that badly.
    implementation("androidx.biometric:biometric:1.1.0")

    // Google Sign-In + Drive, for cloud backup to the user's OWN Drive.
    // The backup file lives in the app's private "appDataFolder" on their
    // Drive — invisible in their file list, tied to this app, and counted
    // against their 15GB but effectively weightless (a backup is a few KB).
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "com.google.guava")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "com.google.guava")
    }

    // Room — offline-first local database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
}
