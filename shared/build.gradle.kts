import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * 공유 모듈(Kotlin Multiplatform): 안드로이드 앱과 iOS 앱이 함께 쓰는 알람 계산 엔진과 서버 통신.
 * 플랫폼 기능(알람 예약, 화면, DB)은 각 앱에 두고, 플랫폼과 상관없는 계산 규칙과 Proxy 서버 호출(Ktor)을 여기 둔다.
 * 날짜·시간은 iOS 에서도 동작하는 kotlinx-datetime / kotlin.time 을 쓴다.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.example.routealarm.shared"
        compileSdk = 36
        minSdk = 26
        compilations.configureEach {
            compileTaskProvider.configure { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
        }
    }

    // 공용 테스트를 빠르게 돌리는 JVM 대상(./gradlew :shared:jvmTest)
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // iOS 앱이 쓰는 프레임워크. 리눅스(CI)에서는 빌드할 수 없어 자동으로 건너뛴다.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Swift 의 백그라운드 Task 에서도 suspend 함수를 부를 수 있게 한다.
            binaryOption("objcExportSuspendFunctionLaunchThreadRestriction", "none")
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        // 서버 통신(ProxyClient)은 iOS 앱만 쓴다. 안드로이드 앱은 기존 Retrofit·OkHttp 를 그대로 쓰므로
        // 안드로이드·JVM 에는 Ktor 엔진을 넣지 않는다(OkHttp 버전 충돌 방지). 테스트는 MockEngine 으로 돈다.
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
    }
}
