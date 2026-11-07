import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Duration

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "com.jyotirmoyb.udhaar"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.jyotirmoyb.udhaar"
    minSdk = 24
    targetSdk = 36
    versionCode = 24
    versionName = "24.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  androidResources {
    localeFilters += listOf("en", "hi", "bn", "as", "ne")
  }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      val enableR8Minify = providers.gradleProperty("udhaar.r8")
        .map { !it.equals("false", ignoreCase = true) }
        .getOrElse(true)
      isCrunchPngs = false
      isMinifyEnabled = enableR8Minify
      isShrinkResources = enableR8Minify
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperty(
          "robolectric.dependency.repo.url",
          "https://maven-central.storage-download.googleapis.com/maven2"
        )
      }
    }
    managedDevices {
      allDevices {
        maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34").apply {
          device = "Pixel 6"
          apiLevel = 34
          systemImageSource = "aosp-atd"
        }
      }
    }
  }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
  bundle {
    language {
      enableSplit = false
    }
  }
}

val verifyReleaseR8 = tasks.register("verifyReleaseR8") {
  group = "verification"
  description = "Fails the build if bundleRelease runs with R8 minification or resource shrinking disabled."
  val r8PropertyEnabled = providers.gradleProperty("udhaar.r8")
    .map { !it.equals("false", ignoreCase = true) }
    .getOrElse(true)
  val releaseBuildType = android.buildTypes.getByName("release")
  val minifyEnabled = releaseBuildType.isMinifyEnabled
  val shrinkResources = releaseBuildType.isShrinkResources
  doLast {
    if (!r8PropertyEnabled || !minifyEnabled || !shrinkResources) {
      throw GradleException(
        "verifyReleaseR8 failed: bundleRelease requires R8 minification (isMinifyEnabled=$minifyEnabled) " +
          "and resource shrinking (isShrinkResources=$shrinkResources) to be ON. " +
          "Do not pass -Pudhaar.r8=false when building release bundles."
      )
    }
    logger.lifecycle("verifyReleaseR8 passed: R8 minification and resource shrinking are enabled for release.")
  }
}

tasks.matching { it.name == "bundleRelease" || it.name == "packageReleaseBundle" }.configureEach {
  dependsOn(verifyReleaseR8)
}

tasks.withType<Test> {
  failFast = true
  timeout.set(Duration.ofMinutes(8))
  testLogging {
    events("passed", "failed", "skipped")
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-Xskip-metadata-version-check")
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.biometric)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.analytics)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.functions)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.play.services.auth)
  implementation(libs.androidx.security.crypto)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.sqlite.ktx)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  implementation(libs.zxing.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.mockwebserver)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
