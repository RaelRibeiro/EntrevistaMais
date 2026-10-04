// ---------------------------------------------------------------------------
// Assinatura de release
// ----------------------------------------------------------------------------
// A chave vive em Downloads (entrevistador-release.jks) e as credenciais em
// app/keystore.properties — arquivo que NÃO sobe para o GitHub (gitignore).
// Com isso o Google deixa de mostrar "app desconhecido" no download.
// ---------------------------------------------------------------------------
import java.io.File as ArquivoChave

val keystoreProperties = run {
    val arquivo = rootProject.file("app/keystore.properties")
    if (!arquivo.exists()) return@run emptyMap()
    arquivo.readLines()
        .filter { it.contains("=") }
        .associate { linha ->
            val indice = linha.indexOf("=")
            linha.substring(0, indice).trim() to linha.substring(indice + 1).trim()
        }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Firebase
// ----------------------------------------------------------------------------
// O app usa o mesmo Firebase do site (Auth + Firestore).
// A inicialização é feita em código (FirebaseConfig.kt), então não é preciso
// do google-services.json nem do plugin Google Services.
// ----------------------------------------------------------------------------

android {
    namespace = "com.example.entrevistador"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.entrevistador"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        create("release") {
            val caminho = keystoreProperties["keystoreFile"] ?: return@create
            if (!ArquivoChave(caminho).exists()) return@create
            storeFile = ArquivoChave(caminho)
            storePassword = keystoreProperties["keystorePassword"]
            keyAlias = keystoreProperties["keyAlias"]
            keyPassword = keystoreProperties["keyPassword"]
            // v1 assina apenas a parte do ZIP do APK, formato mais antigo em
            // que o Android instala com menos verificação. Assinar as três
            // versões (v1+v2+v3) entrega ao instalador o mesmo formato de um
            // app publicado no Google Play, e é um dos pontos que o Play
            // Protect olha ao exibir o aviso de "app perigoso".
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    // java.time (LocalDate/LocalTime) no minSdk 24
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

// ---------------------------------------------------------------------------
// Firebase (Auth + Firestore). Como o app precisa da mesma conta e
// dos mesmos dados do site, o Firebase entra sempre no conjunto de fontes.
// ---------------------------------------------------------------------------

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Navegação + ViewModel
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (configurações + sessão)
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Firebase (mesmo projeto do site)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
