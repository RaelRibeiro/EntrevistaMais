plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Firebase (opcional)
// ----------------------------------------------------------------------------
// O app funciona 100% offline com o login local (implementação padrão).
// Para migrar para o Firebase Auth:
//   1. Baixe o google-services.json e coloque em app/google-services.json
//   2. Troque `entrevistador.firebase=false` por `true` em gradle.properties
// Nenhuma outra alteração de código é necessária: o AuthRepository continua
// sendo o mesmo, só muda a implementação injetada no AppContainer.
// ---------------------------------------------------------------------------
val firebaseEnabled = providers.gradleProperty("entrevistador.firebase")
    .map { it.trim().equals("true", ignoreCase = true) }
    .getOrElse(false)

if (providers.gradleProperty("entrevistador.firebase").isPresent) {
    // Mantém o build determinístico: sem o arquivo json o build falharia.
    val jsonPresent = file("google-services.json").exists()
    if (firebaseEnabled && !jsonPresent) {
        throw GradleException(
            "entrevistador.firebase=true, mas app/google-services.json não existe. " +
                "Baixe o arquivo no console do Firebase ou defina entrevistador.firebase=false."
        )
    }
}

if (firebaseEnabled) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.example.entrevistador"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.entrevistador"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
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
        }
    }
}

// A implementação de auth do Firebase só entra no conjunto de fontes quando
// a flag está ligada, para que o build padrão não dependa do Firebase.
// Com o Kotlin embutido do AGP 9 o caminho é android.sourceSets (o
// kotlin.sourceSets não é permitido aqui).
if (firebaseEnabled) {
    android.sourceSets.getByName("main").java.srcDir("src/firebase/java")
}

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
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    if (firebaseEnabled) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.auth)
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
