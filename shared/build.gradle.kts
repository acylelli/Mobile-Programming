import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * 공유 모듈(Kotlin Multiplatform): 안드로이드 앱과 iOS 앱이 함께 쓰는 알람 계산 엔진.
 * 플랫폼 기능(알람 예약, 화면, DB)은 각 앱에 두고, 플랫폼과 상관없는 계산 규칙만 여기 둔다.
 * 날짜·시간은 iOS 에서도 동작하는 kotlinx-datetime / kotlin.time 을 쓴다.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
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
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
