package com.jyotirmoyb.udhaar.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates an ART Baseline Profile covering Udhaar's cold-start and primary user journeys:
 * 1. Cold start to Cloud Verification screen -> Continue locally
 * 2. Home dashboard scroll & People directory tab switch
 * 3. Opening the Add Record screen and returning cleanly
 *
 * Run on a Gradle Managed Device or physical rooted/userdebug API 34+ device:
 *   gradle :baselineprofile:pixel6Api34DebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.jyotirmoyb.udhaar.baselineprofile.BaselineProfileGenerator
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateUdhaarBaselineProfile() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            // If optional cloud verification screen is displayed, tap "Continue locally"
            val skipLocalBtn = device.wait(Until.findObject(By.res("cloud_gate_skip_local_btn")), 3_000L)
                ?: device.findObject(By.textContains("Continue locally"))
            skipLocalBtn?.click()
            device.waitForIdle()

            // Scroll the Home transactions list when present
            val list = device.wait(Until.findObject(By.scrollable(true)), 3_000L)
            list?.let {
                it.setGestureMargin(device.displayWidth / 5)
                it.fling(Direction.DOWN)
                device.waitForIdle()
                it.fling(Direction.UP)
                device.waitForIdle()
            }

            // Exercise People tab navigation
            device.findObject(By.text("People"))?.click()
            device.waitForIdle()
            device.findObject(By.text("Home"))?.click()
            device.waitForIdle()

            // Exercise Add Record screen startup path
            device.findObject(By.res("add_udhaar_fab"))?.click()
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.jyotirmoyb.udhaar"
    }
}
