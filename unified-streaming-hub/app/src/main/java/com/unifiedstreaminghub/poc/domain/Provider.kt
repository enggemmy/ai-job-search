package com.unifiedstreaminghub.poc.domain

/**
 * Streaming providers under test, with the Android package identifier of each provider's
 * phone app.
 *
 * Package IDs were verified on 2026-11-06 from the providers' own Google Play listings
 * (https://play.google.com/store/apps/details?id=<id>). Per listing, checked: the page loads,
 * the developer name matches the provider, and the "Phone" device chip is shown.
 *
 * | Provider    | Package ID                        | Play developer     |
 * |-------------|-----------------------------------|--------------------|
 * | Netflix     | com.netflix.mediaclient           | Netflix, Inc.      |
 * | Disney+     | com.disney.disneyplus             | Disney             |
 * | Prime Video | com.amazon.avod.thirdpartyclient  | Amazon Mobile LLC  |
 * | Apple TV    | com.apple.atve.androidtv.appletv  | Apple              |
 *
 * Deliberately excluded after the same check: `com.amazon.amazonvideo.livingroom` (Prime Video
 * TV listing, no Phone chip) and `com.apple.atve.sony.appletv` (Sony TV variant, no Phone chip).
 *
 * Caveats (not verified): Galaxy Store, regional or OEM builds may use other IDs; and the Apple
 * ID contains "androidtv" even though its listing shows Phone/Tablet, so its behaviour on a
 * Galaxy phone must be confirmed on the device.
 *
 * No URLs, hosts or custom schemes are hard-coded. Title URLs are supplied by the tester.
 * Any ID added here must also be added to the manifest `<queries>`; ManifestQueriesTest enforces it.
 */
enum class Provider(val displayName: String, val packageName: String) {
    Netflix("Netflix", "com.netflix.mediaclient"),
    DisneyPlus("Disney+", "com.disney.disneyplus"),
    PrimeVideo("Prime Video", "com.amazon.avod.thirdpartyclient"),
    AppleTv("Apple TV", "com.apple.atve.androidtv.appletv"),
}
