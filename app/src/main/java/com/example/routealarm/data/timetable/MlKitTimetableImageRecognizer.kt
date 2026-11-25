package com.example.routealarm.data.timetable

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.example.routealarm.BuildConfig
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.policy.OcrLine
import com.example.routealarm.domain.policy.OcrWord
import com.example.routealarm.domain.policy.TimetableImageParser
import com.example.routealarm.domain.policy.TimetableParseResult
import com.example.routealarm.domain.repository.TimetableImageRecognizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Google ML Kit 한국어 글자 인식으로 시간표 사진을 읽는다.
 * 모델이 APK 안에 있어 인식은 기기 안에서 바로 하고, 사진은 기기 밖으로 나가지 않으며 키·네트워크도 필요 없다.
 * 글자와 위치만 ML Kit 에 맡기고, 시간표 해석은 [TimetableImageParser](순수 Kotlin, 단위 테스트)가 한다.
 */
@Singleton
class MlKitTimetableImageRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : TimetableImageRecognizer {

    private val recognizer by lazy { TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()) }

    override suspend fun recognize(imageUri: String): AppResult<TimetableParseResult> = try {
        val image = InputImage.fromFilePath(context, imageUri.toUri())
        val text = recognizer.process(image).await()
        val lines = text.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(
                text = line.text,
                left = box.left, top = box.top, right = box.right, bottom = box.bottom,
                words = line.elements.mapNotNull { element ->
                    element.boundingBox?.let { OcrWord(element.text, it.left, it.top, it.right, it.bottom) }
                },
            )
        }
        val result = TimetableImageParser.parse(lines)
        if (BuildConfig.DEBUG) {
            // 인식이 기대와 다를 때 원인을 찾기 위한 디버그 로그(release 빌드에는 남지 않는다)
            lines.forEach { Log.d(TAG, "line '${it.text}' [${it.left},${it.top},${it.right},${it.bottom}] words=${it.words.map { w -> w.text }}") }
            Log.d(TAG, "parse result: $result")
        }
        AppResult.Success(result)
    } catch (e: IOException) {
        AppResult.Failure(AppError.Unknown("사진을 열지 못했어요"))
    } catch (e: Exception) {
        // 인식 모델 오류 등. 사용자는 직접 입력으로 계속할 수 있다.
        if (BuildConfig.DEBUG) Log.w(TAG, "recognition failed", e)
        AppResult.Failure(AppError.Unknown(e.message))
    }

    private companion object {
        const val TAG = "TimetableOcr"
    }
}
