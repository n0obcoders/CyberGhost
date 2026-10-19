plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.cyberghost"; compileSdk = 37
    defaultConfig { applicationId = "com.cyberghost"; minSdk = 29; targetSdk = 37; versionCode = 1; versionName = "2.0" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}"; excludes += "META-INF/BC*.SF"; excludes += "META-INF/BC*.DSA"; excludes += "META-INF/INDEX.LIST" } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui"); implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.0"); implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("io.netty:netty-all:4.1.115.Final")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77"); implementation("org.bouncycastle:bcpkix-jdk18on:1.77")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0"); implementation("org.json:json:20231013")
}
