plugins {
  alias(libs.plugins.android.test)
}

android {
  namespace = "com.jyotirmoyb.udhaar.baselineprofile"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 28
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
  }

  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  testOptions {
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
}

dependencies {
  implementation(libs.androidx.junit)
  implementation(libs.androidx.espresso.core)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
