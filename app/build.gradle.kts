plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.cyberghost"; compileSdk = 35
    defaultConfig { applicationId = "com.cyberghost"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "2.0" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/BC*.SF"
            excludes += "META-INF/BC*.DSA"
            excludes += "META-INF/BC*.RSA"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/MANIFEST.MF"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/io.netty.*"
            excludes += "META-INF/native-image/**"
            excludes += "META-INF/versions/**"
            excludes += "META-INF/services/reactor.*"
            pickFirsts += "META-INF/services/**"
            pickFirsts += "META-INF/native/**"
            pickFirsts += "mozilla/public-suffix-list.txt"
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("io.netty:netty-all:4.1.107.Final")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.77")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.json:json:20231013")
    // QR code generation
    implementation("com.google.zxing:core:3.5.3")
}
