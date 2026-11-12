package com.example.routealarm.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppIcons
import com.example.routealarm.core.designsystem.theme.AppType
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.presentation.alarmlist.AlarmListRoute
import com.example.routealarm.presentation.home.HomeRoute
import com.example.routealarm.presentation.onboarding.OnboardingScreen
import com.example.routealarm.presentation.schedule.create.CreateScheduleRoute
import com.example.routealarm.presentation.schedule.detail.ScheduleDetailRoute
import com.example.routealarm.presentation.settings.SettingsRoute
import com.example.routealarm.presentation.settings.theme.ThemeRoute
import com.example.routealarm.presentation.subscription.GroupPassRoute
import com.example.routealarm.presentation.subscription.PaywallSheet
import com.example.routealarm.presentation.subscription.SubscriptionRoute
import kotlinx.coroutines.launch

private const val TRANSITION_MILLIS = 220

/**
 * 화면 어디서든 짧은 안내를 띄우기 위한 앱 공용 Snackbar.
 * 화면마다 Scaffold 를 두지 않고 최상위 Scaffold 하나가 하단 탭과 Snackbar 를 함께 관리한다.
 */
val LocalAppSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("SnackbarHostState is not provided") }

/** 하단 탭 3개: 홈 / 알람 / 설정 (INR-002) */
private data class TopLevelDestination(val route: Route, val labelRes: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(Route.Home, R.string.nav_home, AppIcons.Home),
    TopLevelDestination(Route.AlarmList, R.string.nav_alarms, AppIcons.Alarm),
    TopLevelDestination(Route.Settings, R.string.nav_settings, AppIcons.Settings),
)

@Composable
fun RouteAlarmApp(
    onboardingCompleted: Boolean,
    onCompleteOnboarding: () -> Unit,
    initialScheduleId: Long?,
    navController: NavHostController = rememberNavController(),
) {
    // 온보딩 완료 시 startDestination 이 바뀌면 NavHost 가 그래프를 다시 만들어 Home 이 두 번 생성된다.
    // 앱 시작 시점의 값만 사용하고, 온보딩 완료 후 이동은 명시적 navigate 로 처리한다.
    val startDestination: Route = remember { if (onboardingCompleted) Route.Home else Route.Onboarding }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { dest -> currentDestination?.hasRoute(dest.route::class) == true }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // PRO 페이월은 어느 화면에서든 잠긴 기능을 눌렀을 때 뜨는 바텀시트라 앱 최상위에서 한 번만 관리한다.
    var paywallVisible by rememberSaveable { mutableStateOf(false) }
    val showPaywall = { paywallVisible = true }

    /**
     * 탭으로 이동한다. 항상 홈 위에 한 화면만 쌓는다(뒤로 가기 = 홈).
     * @param keepState 탭을 오갈 때는 스크롤 위치 등을 유지한다. 일정 저장처럼 흐름을 끝내고 돌아갈 때는 false 로
     *   쌓여 있던 화면(만들기 단계)을 버린다. true 로 두면 저장해 둔 스택이 복원되어 만들기 화면으로 되돌아간다.
     */
    fun openTab(route: Route, keepState: Boolean = true) {
        navController.navigate(route) {
            popUpTo<Route.Home> { saveState = keepState }
            launchSingleTop = true
            restoreState = keepState
        }
    }

    CompositionLocalProvider(LocalAppSnackbar provides snackbar) {
        Scaffold(
            containerColor = RouteAlarmTheme.colors.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (showBottomBar) {
                    RouteAlarmBottomBar(
                        isSelected = { dest -> currentDestination?.hasRoute(dest.route::class) == true },
                        onSelect = { dest -> openTab(dest.route) },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_MILLIS)) { it / 6 } },
                exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
                popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
                popExitTransition = { fadeOut(tween(TRANSITION_MILLIS)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_MILLIS)) { it / 6 } },
            ) {
                composable<Route.Onboarding> {
                    OnboardingScreen(onFinish = {
                        onCompleteOnboarding()
                        navController.navigate(Route.Home) { popUpTo<Route.Onboarding> { inclusive = true } }
                    })
                }
                composable<Route.Home> {
                    HomeRoute(
                        onCreateSchedule = { navController.navigate(Route.CreateSchedule()) },
                        onOpenSchedule = { navController.navigate(Route.ScheduleDetail(it)) },
                        onShowPaywall = showPaywall,
                    )
                }
                composable<Route.AlarmList> {
                    AlarmListRoute(
                        onCreateSchedule = { navController.navigate(Route.CreateSchedule()) },
                        onOpenSchedule = { navController.navigate(Route.ScheduleDetail(it)) },
                        onShowPaywall = showPaywall,
                    )
                }
                composable<Route.Settings> {
                    SettingsRoute(
                        onShowPaywall = showPaywall,
                        onOpenTheme = { navController.navigate(Route.ThemeSelect) },
                        onOpenGroupPass = { navController.navigate(Route.GroupPass) },
                        onOpenSubscription = { navController.navigate(Route.Subscription) },
                    )
                }

                composable<Route.CreateSchedule> {
                    CreateScheduleRoute(
                        // 저장하면 알람 목록으로 이동한다(UI-C-006 ⑥). 화면이 바뀐 뒤에도 안내가 보이도록 앱 범위에서 띄운다.
                        onSaved = { inexactWarning ->
                            openTab(Route.AlarmList, keepState = false)
                            scope.launch {
                                snackbar.showSnackbar(
                                    context.getString(if (inexactWarning) R.string.create_result_inexact_warning else R.string.create_result_saved),
                                )
                            }
                        },
                        onClose = { navController.popBackStack() },
                        onShowPaywall = showPaywall,
                    )
                }
                composable<Route.ScheduleDetail> {
                    ScheduleDetailRoute(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Route.CreateSchedule(editScheduleId = it)) },
                    )
                }
                composable<Route.ThemeSelect> {
                    ThemeRoute(onBack = { navController.popBackStack() })
                }
                composable<Route.GroupPass> {
                    GroupPassRoute(
                        onBack = { navController.popBackStack() },
                        // PRO 를 적용하고 들어왔던 화면(설정)으로 돌아간다(UI-E-002 ⑤).
                        onApplied = { organization ->
                            navController.popBackStack()
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.group_pass_applied, organization)) }
                        },
                    )
                }
                composable<Route.Subscription> {
                    SubscriptionRoute(
                        onBack = { navController.popBackStack() },
                        onShowPaywall = showPaywall,
                        onOpenGroupPass = { navController.navigate(Route.GroupPass) },
                    )
                }
            }
        }

        if (paywallVisible) {
            PaywallSheet(
                onDismiss = { paywallVisible = false },
                onOpenGroupPass = {
                    paywallVisible = false
                    navController.navigate(Route.GroupPass) { launchSingleTop = true }
                },
                onSubscribed = {
                    paywallVisible = false
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.paywall_trial_started)) }
                },
            )
        }
    }

    // 알림을 눌러 들어온 경우 해당 일정 상세로 이동
    LaunchedEffect(initialScheduleId, onboardingCompleted) {
        if (initialScheduleId != null && onboardingCompleted) {
            navController.navigate(Route.ScheduleDetail(initialScheduleId)) { launchSingleTop = true }
        }
    }
}

/**
 * 하단 탭(UI-B-001 ⑦). 현재 탭은 굵은 글자와 강조색 점으로 표시한다.
 * Material NavigationBar 의 알약 모양 표시 대신 설계서의 점 표시를 쓰기 위해 직접 그린다.
 */
@Composable
private fun RouteAlarmBottomBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        HorizontalDivider(thickness = 1.dp, color = colors.divider)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .selectableGroup(),
        ) {
            topLevelDestinations.forEach { dest ->
                val selected = isSelected(dest)
                val tint = if (selected) colors.textPrimary else colors.textTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(dest) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(12.dp))
                    Icon(dest.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(dest.labelRes),
                        style = AppType.Tab,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = tint,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(5.dp).background(if (selected) colors.accent else Color.Transparent, CircleShape))
                }
            }
        }
    }
}
