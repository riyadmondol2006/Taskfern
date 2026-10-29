import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------------------------------------------------
// Versioning (docs/RELEASING.md). CI passes TASKFERN_VERSION_CODE / TASKFERN_VERSION_NAME, computed once from the
// release workflow's run number, so the APK, tag, release title and About screen all agree. Local builds need no CI
// variables: they get versionCode 1 and versionName "1.0.0-dev".
// ---------------------------------------------------------------------------------------------------------------------
val maxVersionCode = 2_100_000_000 // Android's practical versionCode ceiling

fun versionOverride(gradleProperty: String, envVar: String): String? =
    providers.gradleProperty(gradleProperty).orElse(providers.environmentVariable(envVar)).orNull
        ?.trim()?.takeIf { it.isNotEmpty() }

val appVersionCode: Int = versionOverride("taskfern.versionCode", "TASKFERN_VERSION_CODE")?.let { raw ->
    raw.takeIf { Regex("[1-9][0-9]{0,9}").matches(it) }?.toLongOrNull()?.takeIf { it <= maxVersionCode }?.toInt()
        ?: throw GradleException("versionCode '$raw' must be an integer between 1 and $maxVersionCode")
} ?: 1

val appVersionName: String = versionOverride("taskfern.versionName", "TASKFERN_VERSION_NAME")?.also {
    if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z.]+)?").matches(it)) {
        throw GradleException("versionName '$it' must look like 1.2.3")
    }
} ?: "1.0.0-dev"

tasks.register("printVersion") {
    group = "help"
    description = "Prints the versionName and versionCode this build uses."
    val line = "versionName=$appVersionName versionCode=$appVersionCode"
    doLast { println(line) }
}

// ---------------------------------------------------------------------------------------------------------------------
// Release signing. Provide keystore.properties locally or the TASKFERN_* environment variables (CI). CI also sets
// TASKFERN_REQUIRE_SIGNING=true so a missing key fails the build instead of producing an unsigned artifact.
// ---------------------------------------------------------------------------------------------------------------------
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: providers.environmentVariable(envKey).orNull

val requireSigning = providers.environmentVariable("TASKFERN_REQUIRE_SIGNING").orNull == "true"

/** Fails the build when the merged manifest asks for a network or broad-storage permission. Taskfern is offline. */
abstract class VerifyOfflineManifestTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = mergedManifest.get().asFile.readText()
        val declared = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
            .findAll(text).map { it.groupValues[1] }.toSet()
        val forbidden = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "com.google.android.gms.permission.AD_ID",
        )
        val offending = declared.intersect(forbidden)
        check(offending.isEmpty()) { "Taskfern must stay offline, but the merged manifest declares: $offending" }
    }
}

android {
    namespace = "com.riyadmondol2006.taskfern"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.riyadmondol2006.taskfern"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "REPO_URL", "\"https://github.com/riyadmondol2006/Taskfern\"")
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "TASKFERN_KEYSTORE_PATH")
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "TASKFERN_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "TASKFERN_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "TASKFERN_KEY_PASSWORD")
            }
        }
        if (requireSigning) {
            val release = findByName("release")
            check(
                release != null && release.storeFile?.isFile == true &&
                    !release.storePassword.isNullOrEmpty() && !release.keyAlias.isNullOrEmpty() &&
                    !release.keyPassword.isNullOrEmpty(),
            ) { "TASKFERN_REQUIRE_SIGNING is set but the release keystore, alias or passwords are missing" }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Taskfern Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "Taskfern")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        // Dependencies are pinned in the version catalog and bumped deliberately.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
    }

    sourceSets {
        // Room exports schemas here so migrations can be verified by MigrationTest.
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

androidComponents {
    onVariants { variant ->
        val capName = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyOfflineManifestTask>("verifyOfflineManifest$capName") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(verify) }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material3.adaptive.nav.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
