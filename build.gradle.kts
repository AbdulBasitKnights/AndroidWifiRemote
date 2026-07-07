plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// AGP dex transforms create very deep paths; keep build output short on Windows.
val shortBuildRoot = run {
    val custom = providers.gradleProperty("atvr.build.dir").orNull
    if (!custom.isNullOrBlank()) {
        rootProject.file(custom)
    } else {
        File(System.getProperty("java.io.tmpdir")).resolve("atvr-build")
    }
}

subprojects {
    layout.buildDirectory.set(shortBuildRoot.resolve(project.name))
}
