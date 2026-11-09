import java.net.URL
import java.net.HttpURLConnection
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.StandardCopyOption

plugins {
    kotlin("android")
    id("com.android.application")
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.quickie.bundled)
    implementation(libs.androidx.activity.ktx)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

android {
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
}

val couiRevision = providers.gradleProperty("cmfa.couiRevision").map { revision ->
    require(revision.matches(Regex("[1-9][0-9]{0,6}"))) { "Invalid COUI revision" }
    revision.toInt()
}

if (couiRevision.isPresent) {
    androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
        val revision = couiRevision.get()
        variant.outputs.forEach { output ->
            output.versionCode.set(211042 + revision)
            output.versionName.set("2.11.36.Live.Mobile.1-COUI.$revision")
        }
    }
}

val mainlineBuildNumber = providers.gradleProperty("cmfa.mainlineBuildNumber").map {
    val number = it.toInt()
    require(number in 1..1_000_000) { "Invalid mainline build number" }
    number
}

if (mainlineBuildNumber.isPresent) {
    androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
        val build = mainlineBuildNumber.get()
        variant.outputs.forEach { output ->
            output.versionCode.set(211046 + build)
            output.versionName.set("2.11.36.Live.Coui.R8.$build")
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

// GeoData is provisioned independently by .github/scripts/prepare_geoassets.py.
// Ordinary Android builds only verify the local assets and their SHA-256 manifest.
val geoFilesDownloadDir = file("src/main/assets")
val requiredGeoAssets = setOf("geoip.metadb", "geosite.dat", "ASN.mmdb", "BundleMRS.7z")
val geoManifest = rootProject.file(".geoassets/sha256sums")

tasks.register("verifyGeoFiles") {
    inputs.files(requiredGeoAssets.map { geoFilesDownloadDir.resolve(it) })
    inputs.file(geoManifest)
    doLast {
        check(geoManifest.isFile) {
            "GeoData manifest is missing. Run: python3 .github/scripts/prepare_geoassets.py"
        }
        val linePattern = Regex("^([a-f0-9]{64})  ([A-Za-z0-9_.-]+)$")
        val entries = geoManifest.readLines().filter { it.isNotBlank() }.map { line ->
            val match = linePattern.matchEntire(line)
                ?: error("Invalid GeoData manifest line: $line")
            match.groupValues[2] to match.groupValues[1]
        }.toMap()
        check(entries.keys == requiredGeoAssets) {
            "GeoData manifest must contain exactly the four required data assets"
        }
        requiredGeoAssets.forEach { name ->
            val asset = geoFilesDownloadDir.resolve(name)
            check(asset.isFile && asset.length() > 1024L) {
                "Missing or truncated GeoData: $name. Run: python3 .github/scripts/prepare_geoassets.py"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            asset.inputStream().buffered().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count == -1) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == entries[name]) {
                "GeoData checksum mismatch for $name; refresh via prepare_geoassets.py"
            }
        }
        val archive = geoFilesDownloadDir.resolve("BundleMRS.7z")
        check(archive.inputStream().use { stream ->
            stream.readNBytes(6).contentEquals(byteArrayOf(0x37, 0x7a, 0xbc.toByte(), 0xaf.toByte(), 0x27, 0x1c))
        }) { "BundleMRS.7z has an invalid archive signature" }
        println("GeoData integrity verified; no network access in Android assemble")
    }
}

// This check replaces the former download-on-every-build dependency.
afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") }.configureEach {
        dependsOn("verifyGeoFiles")
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(geoFilesDownloadDir, geoManifest)
}
