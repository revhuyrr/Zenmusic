// Toolchain mirrors the combination SimpMusic builds with: AGP 9 has Kotlin built in, so only
// the Compose compiler plugin is applied alongside it.
plugins {
    id("com.android.application") version "9.5.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
