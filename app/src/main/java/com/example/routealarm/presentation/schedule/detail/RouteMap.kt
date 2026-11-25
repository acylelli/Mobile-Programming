package com.example.routealarm.presentation.schedule.detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.routealarm.BuildConfig
import com.example.routealarm.domain.model.GeoPoint
import com.example.routealarm.presentation.common.MapLine
import com.example.routealarm.presentation.common.RouteMapLines
import com.example.routealarm.presentation.common.transitLineColor
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles

/**
 * 카카오맵 SDK 초기화. 키가 없거나(CI 빌드 등) 기기가 지원하지 않으면(지도 라이브러리는 arm 기기만) 지도를 숨긴다.
 */
object KakaoMapSupport {
    var isAvailable: Boolean = false
        private set

    fun init(context: Context) {
        val key = BuildConfig.KAKAO_NATIVE_APP_KEY
        if (key.isBlank()) return
        isAvailable = try {
            KakaoMapSdk.init(context, key)
            true
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) Log.w(TAG, "카카오맵 초기화 실패", e)
            false
        }
    }

    internal const val TAG = "RouteMap"
}

/**
 * 상세 화면의 경로 지도. 스크롤 화면 안에 있어 손가락으로 움직이지 않는 미리보기로 두고,
 * 경로 전체가 보이도록 카메라를 맞춘다. 탑승 구간은 노선색, 도보는 회색 선.
 */
@Composable
fun RouteMap(
    lines: List<MapLine>,
    origin: GeoPoint,
    destination: GeoPoint,
    walkColor: Color,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val mapView = remember { MapView(context) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() = Unit
                    override fun onMapError(error: Exception) {
                        // 키 인증 실패(키 해시·플랫폼 미등록 등)나 네트워크 오류. 지도만 숨기고 화면은 그대로 쓴다.
                        if (BuildConfig.DEBUG) Log.w(KakaoMapSupport.TAG, "지도 오류", error)
                        onError()
                    }
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(map: KakaoMap) {
                        GestureType.entries.forEach { map.setGestureEnable(it, false) }
                        kakaoMap = map
                    }
                },
            )
            mapView
        },
    )

    LaunchedEffect(kakaoMap, lines) {
        val map = kakaoMap ?: return@LaunchedEffect
        val widthPx = with(density) { 5f * this.density }
        val walkWidthPx = with(density) { 3f * this.density }
        val routeLayer = map.routeLineManager?.layer ?: return@LaunchedEffect
        routeLayer.removeAll()
        lines.forEach { line ->
            val color = if (line.isWalk) walkColor else transitLineColor(line.type, line.lineName)
            val style = RouteLineStyle.from(if (line.isWalk) walkWidthPx else widthPx, color.toArgb())
            routeLayer.addRouteLine(RouteLineOptions.from(RouteLineSegment.from(line.points.map { it.toLatLng() }, RouteLineStyles.from(style))))
        }
        map.labelManager?.layer?.let { labels ->
            labels.removeAll()
            val radius = with(density) { 7f * this.density }
            labels.addLabel(LabelOptions.from(origin.toLatLng()).setStyles(LabelStyles.from(LabelStyle.from(dot(radius, ORIGIN_COLOR)))))
            labels.addLabel(LabelOptions.from(destination.toLatLng()).setStyles(LabelStyles.from(LabelStyle.from(dot(radius, DESTINATION_COLOR)))))
        }
        val points = RouteMapLines.boundsPoints(lines).map { it.toLatLng() }.toTypedArray()
        if (points.isNotEmpty()) {
            val padding = with(density) { 36f * this.density }.toInt()
            map.moveCamera(CameraUpdateFactory.fitMapPoints(points, padding))
        }
    }
}

private fun GeoPoint.toLatLng(): LatLng = LatLng.from(latitude, longitude)

/** 출발·도착 표시(흰 테두리 원). 이미지 리소스 없이 그린다. */
private fun dot(radius: Float, color: Int): Bitmap {
    val border = radius / 3f
    val size = ((radius + border) * 2).toInt()
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, radius + border, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, radius, paint)
    return bitmap
}

private const val ORIGIN_COLOR = 0xFF1E88E5.toInt()
private const val DESTINATION_COLOR = 0xFFE53935.toInt()
