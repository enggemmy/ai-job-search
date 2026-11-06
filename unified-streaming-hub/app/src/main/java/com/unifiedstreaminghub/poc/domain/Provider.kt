package com.unifiedstreaminghub.poc.domain

/**
 * Streaming providers under test.
 *
 * Deliberately carries NO URL, host, package identifier or custom scheme. Every title URL is
 * supplied by the tester (taken from the provider's own official site/app share link), and app
 * detection works by asking Android which activities can handle that URL, not by looking up a
 * hard-coded package name.
 */
enum class Provider(val displayName: String) {
    Netflix("Netflix"),
    DisneyPlus("Disney+"),
    PrimeVideo("Prime Video"),
    AppleTv("Apple TV"),
}
