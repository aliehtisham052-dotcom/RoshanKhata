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
    // Compiled against Android 16, still behaving like Android 14.
    //
    // compileSdk is which APIs the code may call; targetSdk is which runtime
    // behaviour the app opts in to. Moving them together would mix two kinds
    // of breakage in one build — a compile error and a screen that has quietly
    // rearranged itself — so this build moves only the first. Warnings about
    // anything deprecated since 34 surface here, where they are cheap.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.innovation313.roshankhata"
        minSdk = 24
        // 36 is Google Play's floor for new apps from 31 Aug 2026. Raised
        // last, after the window-inset handling in ScreenInsets had been
        // checked screen by screen on a real phone — from 35 Android draws
        // apps behind the status and navigation bars whether they are ready
        // or not, and this app is now ready.
        targetSdk = 36
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
    // QR generation only — pure Java, no camera, ~0.5 MB. Scanning is a
    // later step and uses Play Services so no camera permission is needed.
    implementation("com.google.zxing:core:3.5.3")
    // Scanning, through Google Play Services: the scanner UI and models live
    // there, so the app needs no CAMERA permission and gains no model weight.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-ktx:1.13.1")
    // Reads the orientation flag a camera writes into a JPEG. Without it a
    // portrait photo saves sideways, because the pixels really are stored
    // rotated and only the flag says which way is up.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Biometric: fingerprint/face with automatic fallback to the device
    // PIN, pattern, or password. Deliberately NOT a home-grown PIN — the OS
    // already stores credentials in hardware-backed secure storage, and we
    // have no business duplicating that badly.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Google Sign-In + Drive, for cloud backup to the user's OWN Drive.
    // The backup file lives in the app's private "appDataFolder" on their
    // Drive — invisible in their file list, tied to this app, and counted
    // against their 15GB but effectively weightless (a backup is a few KB).
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    // Credential Manager for Sign in with Google -- used ONLY to learn which
    // Google account the owner is connecting Drive to, so the backup screen can
    // show that email. The Drive permission itself is a separate step
    // (AuthorizationClient, above). credentials-play-services-auth backs the
    // sign-in on Android 13 and below; googleid provides the Google option.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "com.google.guava")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "com.google.guava")
    }
    // Guava, put back deliberately after it crashed the backup screen.
    //
    // The excludes above drop Guava to save weight, and the build stayed green
    // the whole time it was missing: the classes that need it live inside the
    // Google client library, already compiled, so nothing here referenced them
    // and nothing failed to compile. It only broke when the code finally RAN —
    // Drive.Builder and NetHttpTransport reach for com.google.common at the
    // moment a Drive client is constructed, which is the first thing the backup
    // screen does. Hidden behind a disabled flag, that day never came until now.
    //
    // The -android variant rather than the default -jre one: same classes, far
    // less of what a phone cannot use. The excludes stay so only this version
    // arrives, instead of the heavier one being pulled in transitively.
    implementation("com.google.guava:guava:33.4.0-android")

    // Room — offline-first local database
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    testImplementation("junit:junit:4.13.2")
}
