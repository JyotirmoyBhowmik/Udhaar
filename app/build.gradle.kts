import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Duration
import java.util.Properties

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
    versionCode = 29
    versionName = "29.0"

    val versionPropsFile = rootProject.file("version.properties")
    val versionProps = Properties().apply {
      if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
      }
    }

    val minVersionCode = versionProps.getProperty("minVersionCode")?.trim()?.toIntOrNull() ?: 28
    val ciVersionCodeBase = versionProps.getProperty("ciVersionCodeBase")?.trim()?.toIntOrNull() ?: 100
    val cliVersionCode = providers.gradleProperty("versionCode").orNull?.trim()?.toIntOrNull()
    val propVersionCode = cliVersionCode
      ?: versionProps.getProperty("versionCode")?.trim()?.toIntOrNull()
    val rawCiRunNumber = providers.environmentVariable("BUILD_NUMBER")
      .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
      .orNull
      ?.trim()
      ?.toIntOrNull()

    val resolvedVersionCode = when {
      propVersionCode == null -> throw GradleException(
        "Missing versionCode: define versionCode in root version.properties (must be >= $minVersionCode)."
      )
      rawCiRunNumber != null && rawCiRunNumber > 0 -> maxOf(propVersionCode, ciVersionCodeBase + rawCiRunNumber)
      else -> propVersionCode
    }
    if (resolvedVersionCode < minVersionCode) {
      throw GradleException(
        "Invalid versionCode=$resolvedVersionCode: versionCode can never go below $minVersionCode."
      )
    }
    if (cliVersionCode == null && (rawCiRunNumber == null || rawCiRunNumber <= 0) && propVersionCode != versionCode) {
      throw GradleException(
        "Version mismatch: app/build.gradle.kts versionCode ($versionCode) != version.properties versionCode ($propVersionCode)."
      )
    }
    if (resolvedVersionCode != versionCode) {
      this::versionCode.set(resolvedVersionCode)
    }

    val cliVersionName = providers.environmentVariable("VERSION_NAME")
      .orElse(providers.gradleProperty("versionName"))
      .orNull
      ?.trim()
      ?.takeIf { it.isNotBlank() }
    val resolvedVersionName = cliVersionName
      ?: versionProps.getProperty("versionName")?.trim()?.takeIf { it.isNotBlank() }
      ?: throw GradleException(
        "Missing versionName: define versionName in root version.properties."
      )
    if (cliVersionName == null && resolvedVersionName != versionName) {
      throw GradleException(
        "Version mismatch: app/build.gradle.kts versionName ($versionName) != version.properties versionName ($resolvedVersionName)."
      )
    }
    if (resolvedVersionName != versionName) {
      this::versionName.set(resolvedVersionName)
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    val udhaarTier = providers.gradleProperty("udhaar.tier").getOrElse("spark")
    buildConfigField("String", "UDHAAR_TIER", "\"$udhaarTier\"")
    val cloudFeatures = providers.gradleProperty("udhaar.cloudFeatures")
      .map { it.equals("true", ignoreCase = true) }
      .getOrElse(false)
    buildConfigField("boolean", "CLOUD_FEATURES", cloudFeatures.toString())
    val blazeFeaturesEnabled = providers.gradleProperty("udhaar.blaze.enabled")
      .map { it.equals("true", ignoreCase = true) }
      .getOrElse(false)
    buildConfigField("boolean", "BLAZE_FEATURES_ENABLED", blazeFeaturesEnabled.toString())
  androidResources {
    localeFilters += listOf("en", "hi", "bn", "as", "ne")
  }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val localProps = Properties().apply {
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
          localPropsFile.inputStream().use { stream ->
            load(stream)
          }
        }
      }
      fun getPropOrEnv(key: String): String? {
        return System.getenv(key)
          ?: localProps.getProperty(key)
          ?: providers.gradleProperty(key).orNull
      }

      val keystorePath = getPropOrEnv("KEYSTORE_PATH")
      val keystorePassword = getPropOrEnv("KEYSTORE_PASSWORD") ?: getPropOrEnv("STORE_PASSWORD")
      val keyAliasVal = getPropOrEnv("KEY_ALIAS") ?: "upload"
      val keyPasswordVal = getPropOrEnv("KEY_PASSWORD") ?: keystorePassword

      val customFile = keystorePath?.let { file(it) }
        ?: file("${rootDir}/my-upload-key.jks")

      if (customFile.exists() && !keystorePassword.isNullOrBlank()) {
        storeFile = customFile
        storePassword = keystorePassword
        keyAlias = keyAliasVal
        keyPassword = keyPasswordVal
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
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
      isDebuggable = false
      isMinifyEnabled = enableR8Minify
      isShrinkResources = enableR8Minify
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isDebuggable = true
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

val verifyReleaseVersioning = tasks.register("verifyReleaseVersioning") {
  group = "verification"
  description = "Fails release builds if version.properties, versionCode (>= 28), or versionName is missing or invalid."
  val propsFile = rootProject.file("version.properties")
  val configuredVersionCode = android.defaultConfig.versionCode
  val configuredVersionName = android.defaultConfig.versionName
  doLast {
    if (!propsFile.exists()) {
      throw GradleException("verifyReleaseVersioning failed: version.properties is missing at ${propsFile.absolutePath}.")
    }
    val props = Properties().apply { propsFile.inputStream().use { load(it) } }
    val propCode = props.getProperty("versionCode")?.trim()?.toIntOrNull()
    val propName = props.getProperty("versionName")?.trim()
    val minCode = props.getProperty("minVersionCode")?.trim()?.toIntOrNull() ?: 28
    if (propCode == null || propCode < minCode) {
      throw GradleException("verifyReleaseVersioning failed: version.properties versionCode=$propCode must be >= $minCode.")
    }
    if (propName.isNullOrBlank()) {
      throw GradleException("verifyReleaseVersioning failed: version.properties versionName is missing or blank.")
    }
    if (configuredVersionCode == null || configuredVersionCode < minCode) {
      throw GradleException("verifyReleaseVersioning failed: resolved versionCode=$configuredVersionCode must be >= $minCode.")
    }
    if (configuredVersionName.isNullOrBlank() || configuredVersionName == "1.0.0") {
      throw GradleException("verifyReleaseVersioning failed: invalid resolved versionName='$configuredVersionName'.")
    }
    logger.lifecycle("verifyReleaseVersioning passed: versionCode=$configuredVersionCode, versionName=$configuredVersionName (min=$minCode).")
  }
}

val verifyReleaseNoDebugAppCheck = tasks.register("verifyReleaseNoDebugAppCheck") {
  group = "verification"
  description = "Verifies that firebase-appcheck-debug is completely absent from release compile and runtime classpaths."
  doLast {
    val forbidden = listOf("firebase-appcheck-debug", "appcheck-debug")
    val releaseConfigs = listOf("releaseCompileClasspath", "releaseRuntimeClasspath")
    for (configName in releaseConfigs) {
      val cfg = configurations.findByName(configName)
        ?: throw GradleException("verifyReleaseNoDebugAppCheck failed: configuration '$configName' not found.")
      val offending = cfg.resolvedConfiguration.resolvedArtifacts
        .map { "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}:${it.moduleVersion.id.version}" }
        .filter { coords -> forbidden.any { token -> coords.contains(token, ignoreCase = true) } }
      if (offending.isNotEmpty()) {
        throw GradleException(
          "verifyReleaseNoDebugAppCheck failed: forbidden debug App Check dependency found in $configName: $offending"
        )
      }
    }
    logger.lifecycle("verifyReleaseNoDebugAppCheck passed: zero debug App Check providers on release classpaths.")
  }
}

val detektConfig = configurations.create("detekt")

tasks.register<JavaExec>("detekt") {
  group = "verification"
  description = "Runs Detekt static analysis on Android Kotlin sources using config/detekt/detekt.yml."
  classpath = detektConfig
  mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
  val inputDir = file("src/main/java")
  val configFile = rootProject.file("config/detekt/detekt.yml")
  val baselineFile = rootProject.file("config/detekt/baseline.xml")
  val reportDir = layout.buildDirectory.dir("reports/detekt").get().asFile
  doFirst {
    reportDir.mkdirs()
  }
  val cliArgs = mutableListOf(
    "--input", inputDir.absolutePath,
    "--config", configFile.absolutePath,
    "--build-upon-default-config",
    "--report", "xml:${File(reportDir, "detekt.xml").absolutePath}",
    "--report", "html:${File(reportDir, "detekt.html").absolutePath}"
  )
  if (baselineFile.exists()) {
    cliArgs.add("--baseline")
    cliArgs.add(baselineFile.absolutePath)
  }
  args(cliArgs)
}

tasks.register<JavaExec>("detektBaseline") {
  group = "verification"
  description = "Regenerates config/detekt/baseline.xml for pre-existing legacy Detekt findings."
  classpath = detektConfig
  mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
  val inputDir = file("src/main/java")
  val configFile = rootProject.file("config/detekt/detekt.yml")
  val baselineFile = rootProject.file("config/detekt/baseline.xml")
  args(
    "--input", inputDir.absolutePath,
    "--config", configFile.absolutePath,
    "--build-upon-default-config",
    "--create-baseline",
    "--baseline", baselineFile.absolutePath
  )
}

tasks.matching { it.name == "bundleRelease" || it.name == "packageReleaseBundle" }.configureEach {
  dependsOn(verifyReleaseR8, verifyReleaseVersioning, verifyReleaseNoDebugAppCheck)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
  dependsOn(verifyReleaseVersioning, verifyReleaseNoDebugAppCheck)
}

tasks.matching {
  it.name.contains("CrashlyticsMappingFile") ||
    it.name == "extractReleaseNativeSymbolTables" ||
    it.name == "extractReleaseVersionControlInfo"
}.configureEach {
  enabled = false
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
  // [BLAZE-DISABLED] firebase-functions & firebase-storage: Preserved for Blaze plan upgrade; unused in Spark mode.
  implementation(libs.firebase.functions)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // App Check (Free tier: Play Integrity for release, Debug provider for debug builds ONLY)
  implementation(libs.firebase.appcheck.playintegrity)
  debugImplementation(libs.firebase.appcheck.debug)
  detektConfig(libs.detekt.cli)
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
  testImplementation(libs.androidx.work.testing)
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
