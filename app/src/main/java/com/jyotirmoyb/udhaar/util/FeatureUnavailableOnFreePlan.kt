package com.jyotirmoyb.udhaar.util

import java.io.IOException

/**
 * Typed failure exception returned or thrown when a Blaze-tier feature
 * (e.g. Cloud Functions, Cloud Storage, Server-side KMS sync) is requested
 * while running on the Firebase Spark (free) tier.
 *
 * In accordance with the enterprise fail-closed policy, this exception prevents
 * fake success, CONFIRMED, or verified states when operations are unavailable.
 */
class FeatureUnavailableOnFreePlan(
    val featureName: String = "Cloud Functions / Storage",
    override val message: String = "Feature '$featureName' requires the Firebase Blaze plan and is disabled in Spark mode."
) : IOException(message)
