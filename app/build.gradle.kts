import java.net.URL
import java.net.HttpURLConnection
import java.io.IOException
import java.nio.file.Files
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

val geoFilesDownloadDir = "src/main/assets"

task("downloadGeoFiles") {

    val geoFilesUrls = mapOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
        // "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb" to "country.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb" to "ASN.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/BundleMRS.7z" to "BundleMRS.7z",
    )

    doLast {
        geoFilesUrls.forEach { (downloadUrl, outputFileName) ->
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()
            // GitHub release CDN occasionally returns HTTP 504 for individual
            // large geo assets. Retry each independently; never retain partial
            // files after a failed attempt or replace a valid prior download.
            val target = outputPath.toPath()
            val staging = outputPath.toPath().resolveSibling("$outputFileName.download")
            val attempts = 10
            var downloaded = false
            for (attempt in 1..attempts) {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout = 90_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "ClashMetaLive-Android-Release")
                    }
                    connection.inputStream.use { input ->
                        Files.copy(input, staging, StandardCopyOption.REPLACE_EXISTING)
                    }
                    require(Files.size(staging) > 1024L) {
                        "Downloaded geo file is unexpectedly small: $outputFileName"
                    }
                    Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING)
                    println("$outputFileName downloaded to $outputPath (attempt $attempt)")
                    downloaded = true
                    break
                } catch (cause: Exception) {
                    Files.deleteIfExists(staging)
                    if (attempt == attempts) {
                        throw IOException("Unable to download $outputFileName after $attempts attempts", cause)
                    }
                    val delayMs = (attempt * 3_000L).coerceAtMost(15_000L)
                    logger.warn("Download $outputFileName attempt $attempt/$attempts failed: ${cause.message}. Retrying.")
                    Thread.sleep(delayMs)
                } finally {
                    connection?.disconnect()
                }
            }
            check(downloaded) { "No successful geo download: $outputFileName" }
        }
    }
}

afterEvaluate {
    val downloadGeoFilesTask = tasks["downloadGeoFiles"]

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file(geoFilesDownloadDir))
}
