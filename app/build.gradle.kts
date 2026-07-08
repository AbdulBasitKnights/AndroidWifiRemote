plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.tvremote.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "tf.smart.tvremote.cast.tv.mirroring"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.1"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "ADJUST_ENVIRONMENT", "\"sandbox\"")
            buildConfigField("String", "inter_splash", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "inter_splash_high", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "native_splash", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_splash_high", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_language", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_language_high", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_language_alt", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_language_alt_high", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_survey", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_survey_hf", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "inter_survey", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "inter_survey_high", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            buildConfigField("String", "ADJUST_ENVIRONMENT", "\"production\"")
            buildConfigField("String", "inter_splash", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "inter_splash_high", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "native_splash", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_splash_high", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_language", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_language_high", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_language_alt", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_language_alt_high", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_survey", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "native_survey_hf", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "inter_survey", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "inter_survey_high", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":remotecontrol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerview)
    implementation(libs.glide)
    implementation(libs.androidx.mediarouter)
    implementation(libs.play.services.cast)
    implementation(libs.nanohttpd)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.play.services.ads)
    implementation(libs.mediation.vungle)
    implementation(libs.mediation.fyber)
    implementation(libs.mediation.facebook)
    implementation(libs.mediation.inmobi)
    implementation(libs.mediation.mintegral)
    implementation(libs.mediation.applovin)
    implementation(libs.mediation.pangle)
    implementation(libs.facebook.android.sdk)
    implementation(libs.adjust.android)
    implementation(libs.billing.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.viewpager2)
    implementation(libs.lottie)
    implementation(libs.shimmer)
    implementation(libs.sdp.android)
    implementation(libs.ssp.android)
    implementation(libs.firebase.config)
}
