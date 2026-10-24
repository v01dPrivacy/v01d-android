plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.voidprivacy.app"
    compileSdk = 35

    // Needed so AGP runs objcopy on the (huge, symbol-laden) native libs from
    // libsignal — without an NDK they ship unstripped (~110 MB per ABI).
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.voidprivacy.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 8
        versionName = "1.1.0"
        // arm64-v8a: real devices (GrapheneOS) · x86_64: emulators.
        // Drops 32-bit ABIs — halves libsignal's native payload.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/void-release.jks")
            storePassword = System.getenv("VOID_KEYSTORE_PASSWORD") ?: ""
            keyAlias = "void"
            keyPassword = System.getenv("VOID_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // libsignal ships a huge *testing* JNI lib we never load — exclude it.
        jniLibs.excludes += setOf(
            "**/libsignal_jni_testing.so",
            "**/libsignal_jni_testing*.dylib",
            "**/libsignal_jni_testing*.dll",
        )
        // Desktop JVM natives from the libsignal-client JAR must not enter the APK.
        resources.excludes += setOf(
            "**/*.dylib",
            "**/*.dll",
        )
    }
}

dependencies {
    // Core library desugaring (required by libsignal-android)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Security - EncryptedSharedPreferences for key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // Bitcoin - bitcoinj for wallet operations
    implementation("org.bitcoinj:bitcoinj-core:0.16.3")

    // QR code generation + scanning
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Tor - Guardian Project
    implementation("info.guardianproject:tor-android:0.4.8.21")
    implementation("info.guardianproject:jtorctl:0.4.5.7")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (Android's is a stub)
    testImplementation("org.json:json:20240303")

    // Room database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SQLCipher encrypted databases
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Signal Protocol - E2E encryption for Void Message (X3DH + Double Ratchet)
    implementation("org.signal:libsignal-android:0.99.3")
}

// libsignal is compiled to Java 21 bytecode; run unit tests on a Java 21 JVM.
// The app itself still compiles to Java 17 bytecode for Android (see compileOptions).
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}
