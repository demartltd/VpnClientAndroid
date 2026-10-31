import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val demarEnv = Properties()
val demarEnvFile = rootProject.file(".env")
check(demarEnvFile.exists()) {
    "Missing .env — copy .env.example to .env and set DEMAR_*_API_BASE"
}
demarEnv.load(FileInputStream(demarEnvFile))
fun demarEnv(key: String): String =
    demarEnv.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Missing $key in .env")

val versionNameProp = providers.gradleProperty("VERSION_NAME").get()
val versionCodeProp = providers.gradleProperty("VERSION_CODE").get().toInt()

android {
    namespace = "com.demar.vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.demar.vpn"
        minSdk = 33
        targetSdk = 36
        versionCode = versionCodeProp
        versionName = versionNameProp
        buildConfigField("String", "DEMAR_AUTH_API_BASE", "\"${demarEnv("DEMAR_AUTH_API_BASE")}\"")
        buildConfigField("String", "DEMAR_VPN_API_BASE", "\"${demarEnv("DEMAR_VPN_API_BASE")}\"")
        buildConfigField("String", "DEMAR_ACCOUNT_API_BASE", "\"${demarEnv("DEMAR_ACCOUNT_API_BASE")}\"")
        buildConfigField("String", "DEMAR_SUPPORT_API_BASE", "\"${demarEnv("DEMAR_SUPPORT_API_BASE")}\"")
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = keystoreProperties.getProperty("storeFile")?.let { file(it) }
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        jniLibs {
            // Extract libopenflux.so so ProcessBuilder can exec it (W^X: no exec from filesDir).
            useLegacyPackaging = true
            excludes += setOf(
                "**/armeabi-v7a/**",
                "**/x86/**",
                "**/x86_64/**",
            )
        }
    }
}

gradle.taskGraph.whenReady {
    val releaseTask = allTasks.any { it.name.contains("Release", ignoreCase = true) }
    if (releaseTask && !keystorePropertiesFile.exists()) {
        throw GradleException(
            """
            Release build requires key.properties (upload keystore).
            Copy key.properties.example and create a keystore — see readme-signing.md
            """.trimIndent(),
        )
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.appset)
    implementation(libs.play.services.ads.identifier)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material.kolor)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.window)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}

tasks.register("verifyBundledLibboxAar") {
    doLast {
        val aar = layout.projectDirectory.file("libs/libbox.aar").asFile
        if (!aar.isFile) {
            throw GradleException(
                """
                Missing libbox.aar in app/libs/.

                Fix: python3 scripts/libbox-build-android.py
                """.trimIndent(),
            )
        }
    }
}

tasks.register("verifyRuleSets") {
    doLast {
        val dir = layout.projectDirectory.dir("src/main/assets/rule-sets").asFile
        val required = listOf(
            "geosite-category-ads",
            "geosite-category-ru",
            "geosite-ir",
            "geosite-cn",
            "geoip-ru",
            "geoip-ir",
            "geoip-cn",
        )
        val missing = required.filter { tag ->
            val f = File(dir, "$tag.srs")
            !f.isFile || f.length() <= 0L
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                """
                Missing rule-sets in ${dir.path}: ${missing.joinToString(", ")}

                Fix: pwsh scripts/sync_rulesets.ps1
                (also run automatically by build_android.ps1 / install_release.ps1)
                """.trimIndent(),
            )
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("verifyBundledLibboxAar", "verifyRuleSets")
}
