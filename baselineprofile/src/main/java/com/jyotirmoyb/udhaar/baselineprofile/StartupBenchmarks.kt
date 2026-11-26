package com.jyotirmoyb.udhaar.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark suite measuring Udhaar cold startup time with and without the Baseline Profile.
 *
 * Run on a Gradle Managed Device or physical device:
 *   gradle :baselineprofile:pixel6Api34ReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.jyotirmoyb.udhaar.baselineprofile.StartupBenchmarks
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupColdCompilationNone() = measureColdStartup(CompilationMode.None())

    @Test
    fun startupColdCompilationBaselineProfiles() = measureColdStartup(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    private fun measureColdStartup(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = BaselineProfileGenerator.TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait()
        }
    }
}
