import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// FCM: Firebase 콘솔(kidhack-38a89)에서 받은 app/google-services.json 이 있을 때만 플러그인을 적용한다.
// 파일이 없어도 빌드는 되게 — 그 빌드에선 FirebaseApp 초기화가 실패해 푸시만 조용히 꺼진다
// (PushTokenRegistrar 가 runCatching 으로 삼킨다). 파일은 git 에 올리지 않는다(.gitignore).
val hasGoogleServicesJson = rootProject.file("app/google-services.json").exists()
if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("kidhack FCM: app/google-services.json 없음 — google-services 플러그인 미적용(푸시 비활성)")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun detectLanIpv4(): String? = runCatching {
    DatagramSocket().use { socket ->
        socket.connect(InetAddress.getByName("8.8.8.8"), 53)
        val addr = socket.localAddress
        if (addr is Inet4Address && !addr.isLoopbackAddress) addr.hostAddress else null
    }
}.getOrNull()

// Prefer host LAN IP on Windows emulators; 10.0.2.2 often times out for OkHttp only.
val apiBaseUrl: String =
    localProperties.getProperty("api.base.url")
        ?: detectLanIpv4()?.let { "http://$it:8080/api" }
        ?: "http://10.0.2.2:8080/api"
logger.lifecycle("kidhack BASE_URL = $apiBaseUrl")

// Google Maps API 키. 우선순위: .env → local.properties (둘 다 git 에 커밋되지 않음)
val mapsApiKey: String = run {
    val fromEnv = Properties().apply {
        val f = rootProject.file(".env")
        if (f.exists()) f.inputStream().use { load(it) }
    }.getProperty("MAPS_API_KEY")
    (fromEnv ?: localProperties.getProperty("MAPS_API_KEY") ?: "").trim()
}


android {
    namespace = "com.hack.kidhack"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "com.hack.kidhack"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
        manifestPlaceholders["usesCleartextTraffic"] = "false"

        // [VULN-MOBILE-SECRET-1] Google Maps API 키를 빌드 시 Manifest(<meta-data> API_KEY)에 주입.
        // Firebase 키도 google-services.json → strings.xml 로 박혀 APK 에서 그대로 추출된다.
        // 의도된 취약점(OWASP Mobile M2 Inadequate Supply Chain / M8). 보존: 고치지 말 것. 명세: mobile/docs/vuln-spec-mobile.md
        // AndroidManifest의 ${MAPS_API_KEY} 자리에 주입
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    // 데모 서명 설정. release 변형을 고정 keystore 로 서명해 설치·시연이 가능하도록 한다.
    // 주의: R8/난독화 비활성(VULN-MOBILE-RE-1)은 그대로 유지한다 — 여기서는 "서명"만 한다.
    // keystore 비밀번호는 데모용이라 그대로 둔다(이 프로젝트는 의도적 취약점 실습용).
    signingConfigs {
        create("demo") {
            storeFile = file("demo.keystore")
            storePassword = "kidhack"
            keyAlias = "kidhack-demo"
            keyPassword = "kidhack"
        }
    }

    buildTypes {
        // 목업 UI 테스트 (Fake repos)
        debug {
            buildConfigField("boolean", "USE_MOCK", "true")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        // 로컬/실서버 API 연동 테스트 (debuggable, mock off)
        create("api") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            applicationIdSuffix = ".api"
            versionNameSuffix = "-api"
            buildConfigField("boolean", "USE_MOCK", "false")
            buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
            // [VULN-MOBILE-NET-1] api 변형에서 평문 HTTP 허용(usesCleartextTraffic=true). networkSecurityConfig
            // 미설정 + 인증서 피닝 없음 → MITM 노출. 의도된 취약점(OWASP Mobile M5). 명세: mobile/docs/vuln-spec-mobile.md
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        // 배포·보안 실습용 (서명 필요). keystore 연결 후 signingConfig 지정.
        release {
            buildConfigField("boolean", "USE_MOCK", "false")
            buildConfigField(
                "String",
                "BASE_URL",
                "\"https://kidhackapp.com/api\"",
            )
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            // 데모 keystore 로 서명.
            signingConfig = signingConfigs.getByName("demo")
            // [VULN-MOBILE-RE-1] R8 축소·난독화 완전 비활성(enable=false) + release signingConfig 없음 →
            // 클래스/메서드/문자열이 평문으로 남아 jadx/apktool 리버싱 난이도 최저. 의도된 취약점
            // (OWASP Mobile M7 Insufficient Binary Protection). 보존: 고치지 말 것. 명세: mobile/docs/vuln-spec-mobile.md
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // FCM (docs/fcm-integration.md). 서비스 클래스: background/push/KidhackFirebaseMessagingService.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.compose.navigation)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.stomp.android)
    implementation(libs.rxjava)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
