package com.example.routealarm.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.routealarm.R
import com.example.routealarm.domain.model.Place
import com.example.routealarm.presentation.alarmlist.AlarmListRoute
import com.example.routealarm.presentation.home.HomeRoute
import com.example.routealarm.presentation.onboarding.OnboardingScreen
import com.example.routealarm.presentation.permission.PermissionScreen
import com.example.routealarm.presentation.place.PlaceSearchRoute
import com.example.routealarm.presentation.schedule.create.CreateScheduleRoute
import com.example.routealarm.presentation.schedule.detail.ScheduleDetailRoute
import com.example.routealarm.presentation.settings.SettingsRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val TRANSITION_MILLIS = 220

/** 하단 탭 3개: 홈 / 알람 / 설정 */
private data class TopLevelDestination(
    val route: Route,
    val labelRes: Int,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Route.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelDestination(Route.AlarmList, R.string.nav_alarms, Icons.Filled.Alarm, Icons.Outlined.Alarm),
    TopLevelDestination(Route.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

/** 장소 검색 결과를 SavedStateHandle(Bundle) 로 넘기기 위한 java.io.Serializable 모델 */
data class PickedPlace(val id: Long, val name: String, val address: String, val latitude: Double, val longitude: Double, val type: String) : java.io.Serializable {
    fun toPlace() = Place(id, name, address, latitude, longitude, com.example.routealarm.data.mapper.enumValueOrDefault(type, com.example.routealarm.domain.model.PlaceType.CUSTOM))
    companion object {
        fun from(place: Place) = PickedPlace(place.id, place.name, place.address, place.latitude, place.longitude, place.type.name)
    }
}

@Composable
fun RouteAlarmApp(
    onboardingCompleted: Boolean,
    onCompleteOnboarding: () -> Unit,
    initialScheduleId: Long?,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { dest -> currentDestination?.hasRoute(dest.route::class) == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    topLevelDestinations.forEach { dest ->
                        val selected = currentDestination?.hasRoute(dest.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted) Route.Home else Route.Onboarding,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(TRANSITION_MILLIS)) { it / 6 } },
            exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
            popExitTransition = { fadeOut(tween(TRANSITION_MILLIS)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(TRANSITION_MILLIS)) { it / 6 } },
        ) {
            composable<Route.Onboarding> {
                OnboardingScreen(onFinish = {
                    navController.navigate(Route.Permission) { popUpTo(Route.Onboarding) { inclusive = true } }
                })
            }
            composable<Route.Permission> {
                PermissionScreen(onContinue = {
                    onCompleteOnboarding()
                    navController.navigate(Route.Home) { popUpTo(Route.Permission) { inclusive = true } }
                })
            }
            composable<Route.Home> {
                HomeRoute(
                    onCreateSchedule = { navController.navigate(Route.CreateSchedule()) },
                    onOpenSchedule = { navController.navigate(Route.ScheduleDetail(it)) },
                )
            }
            composable<Route.AlarmList> {
                AlarmListRoute(onOpenSchedule = { navController.navigate(Route.ScheduleDetail(it)) })
            }
            composable<Route.Settings> { SettingsRoute() }

            composable<Route.CreateSchedule> { entry ->
                val handle = entry.savedStateHandle
                val pickedOrigin by handle.getStateFlow<PickedPlace?>(PlaceSearchResult.KEY_ORIGIN, null).collectAsStateWithLifecycle()
                val pickedDestination by handle.getStateFlow<PickedPlace?>(PlaceSearchResult.KEY_DESTINATION, null).collectAsStateWithLifecycle()
                CreateScheduleRoute(
                    pickedOrigin = pickedOrigin?.toPlace(),
                    pickedDestination = pickedDestination?.toPlace(),
                    onConsumePicked = {
                        handle[PlaceSearchResult.KEY_ORIGIN] = null
                        handle[PlaceSearchResult.KEY_DESTINATION] = null
                    },
                    onSearchOrigin = { navController.navigate(Route.PlaceSearch(PlaceSearchResult.KEY_ORIGIN)) },
                    onSearchDestination = { navController.navigate(Route.PlaceSearch(PlaceSearchResult.KEY_DESTINATION)) },
                    onSaved = { id ->
                        navController.navigate(Route.ScheduleDetail(id)) { popUpTo<Route.CreateSchedule> { inclusive = true } }
                    },
                    onClose = { navController.popBackStack() },
                )
            }
            composable<Route.PlaceSearch> { entry ->
                val resultKey = entry.toRoute<Route.PlaceSearch>().resultKey
                PlaceSearchRoute(
                    onPlaceSelected = { place ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(resultKey, PickedPlace.from(place))
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Route.ScheduleDetail> {
                ScheduleDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Route.CreateSchedule(editScheduleId = it)) },
                )
            }
        }
    }

    // 알림을 눌러 들어온 경우 해당 일정 상세로 이동
    androidx.compose.runtime.LaunchedEffect(initialScheduleId, onboardingCompleted) {
        if (initialScheduleId != null && onboardingCompleted) {
            navController.navigate(Route.ScheduleDetail(initialScheduleId)) { launchSingleTop = true }
        }
    }
}
