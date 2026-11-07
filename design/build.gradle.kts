plugins {
    kotlin("android")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures.compose = true
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":service"))
    implementation(libs.coui)

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.viewpager)
    implementation(libs.google.material)
    implementation(libs.zxing.core)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
}
