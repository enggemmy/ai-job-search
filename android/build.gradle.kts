// Intentionally empty: each subproject declares its own plugins with explicit versions.
// This keeps `./gradlew :domain:test` resolvable without ever touching the Android Gradle
// Plugin classpath, which (in this sandbox) requires dl.google.com and is unreachable - see
// android/NETWORK_LIMITATIONS.md.
