// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // FCM. app 모듈에서 google-services.json 이 있을 때만 적용한다(app/build.gradle.kts 참고).
    alias(libs.plugins.google.services) apply false
}
