plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.yunxiao.kits"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        testApplicationId = ".dubg"
    }

    buildTypes {
        release {
            buildConfigField("boolean", "GLOBAL_LOG", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("boolean", "GLOBAL_LOG", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    sourceSets {
        getByName("debug") {
            res.srcDirs("src/debug/res")
            assets.srcDirs("src/debug/assets")
            java.srcDirs("src/debug/java")
        }
        getByName("release") {
            res.srcDirs("src/release/res")
            assets.srcDirs("src/release/assets")
            java.srcDirs("src/release/java")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugApi("com.facebook.stetho:stetho-okhttp3:1.6.0")
    debugApi("com.facebook.stetho:stetho:1.6.0")
    debugApi("com.facebook.stetho:stetho-js-rhino:1.6.0")

    debugApi("com.github.chuckerteam.chucker:library:4.1.0")
    releaseApi("com.github.chuckerteam.chucker:library-no-op:4.1.0")
    releaseImplementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.16")
    val lastversion = "3.7.1"
    //核心模块

    debugApi("io.github.didi.dokit:dokitx:${lastversion}") {
        exclude(group = "com.android.volley", module = "volley")
        exclude(group = "com.github.ybq", module = "Android-SpinKit")

    }
    debugApi("com.android.volley:volley:1.2.1")
    debugApi("com.github.ybq:Android-SpinKit:1.4.0")

}