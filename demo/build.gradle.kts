plugins { id("com.android.application") }
android {
    namespace = "com.josense.boxshadow.demo"
    compileSdk = 36
    defaultConfig { minSdk = 16 }
}
android { defaultConfig { targetSdk = 36 } }
dependencies { implementation(project(":boxshadow")) }
