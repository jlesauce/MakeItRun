package org.jls.makeitrun.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.jls.makeitrun.R
import org.jls.makeitrun.about.AboutScreen
import org.jls.makeitrun.debug.DebugScreen
import org.jls.makeitrun.history.detail.SessionReportScreen
import org.jls.makeitrun.history.list.HistoryListScreen
import org.jls.makeitrun.session.SessionScreen
import org.jls.makeitrun.settings.SettingsScreen
import org.jls.makeitrun.workout.detail.WorkoutDetailScreen
import org.jls.makeitrun.workout.editor.WorkoutEditorScreen
import org.jls.makeitrun.workout.list.WorkoutListScreen

private object Routes {
    const val WORKOUTS = "workouts"
    const val HISTORY = "history"
    const val DEBUG = "debug"
    const val WORKOUT_DETAIL = "workout/{workoutId}"
    const val EDITOR = "editor/{workoutId}"
    const val SESSION = "session/{workoutId}"
    const val SESSION_RESUME = "session/resume/{sessionId}"
    const val SESSION_REPORT = "history/{sessionId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    val SESSION_ROUTES = setOf(SESSION, SESSION_RESUME)

    fun workoutDetail(id: Long) = "workout/$id"
    fun editor(id: Long) = "editor/$id"
    fun session(id: Long) = "session/$id"
    fun resumeSession(id: Long) = "session/resume/$id"
    fun sessionReport(id: Long) = "history/$id"

    const val NEW_WORKOUT = 0L
}

private enum class Tab(
    val route: String,
    val labelResId: Int,
    val icon: ImageVector,
) {
    WORKOUTS(Routes.WORKOUTS, R.string.tab_workouts, Icons.AutoMirrored.Filled.List),
    HISTORY(Routes.HISTORY, R.string.tab_history, Icons.Default.History),
    DEBUG(Routes.DEBUG, R.string.tab_debug, Icons.Default.Build),
}

@Composable
fun MakeItRunApp(runningSessionViewModel: RunningSessionViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val runningWorkoutId by runningSessionViewModel.runningWorkoutId
        .collectAsStateWithLifecycle()

    val openSettings = { navController.navigate(Routes.SETTINGS) }
    val openAbout = { navController.navigate(Routes.ABOUT) }

    LaunchedEffect(runningWorkoutId, currentRoute) {
        val workoutId = runningWorkoutId ?: return@LaunchedEffect
        if (currentRoute == null || currentRoute in Routes.SESSION_ROUTES) return@LaunchedEffect
        navController.navigate(Routes.session(workoutId)) {
            popUpTo(Routes.WORKOUTS)
            launchSingleTop = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute in Tab.entries.map { it.route }) {
                BottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WORKOUTS,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.WORKOUTS) {
                WorkoutListScreen(
                    contentPadding = innerPadding,
                    onCreateWorkout = {
                        navController.navigate(Routes.editor(Routes.NEW_WORKOUT))
                    },
                    onOpenWorkout = { id -> navController.navigate(Routes.workoutDetail(id)) },
                    onOpenSettings = openSettings,
                    onOpenAbout = openAbout,
                )
            }

            composable(Routes.HISTORY) {
                HistoryListScreen(
                    contentPadding = innerPadding,
                    onOpenSession = { id -> navController.navigate(Routes.sessionReport(id)) },
                    onResumeSession = { id ->
                        navController.navigate(Routes.resumeSession(id)) {
                            popUpTo(Routes.HISTORY)
                        }
                    },
                    onOpenSettings = openSettings,
                    onOpenAbout = openAbout,
                )
            }

            composable(Routes.SESSION_REPORT) { entry ->
                SessionReportScreen(
                    sessionId = entry.sessionId(),
                    onBack = navController::popBackStack,
                )
            }

            composable(Routes.DEBUG) {
                DebugScreen(
                    contentPadding = innerPadding,
                    onOpenSettings = openSettings,
                    onOpenAbout = openAbout,
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = navController::popBackStack)
            }

            composable(Routes.ABOUT) {
                AboutScreen(onBack = navController::popBackStack)
            }

            composable(Routes.WORKOUT_DETAIL) { entry ->
                WorkoutDetailScreen(
                    workoutId = entry.workoutId(),
                    onBack = navController::popBackStack,
                    onEdit = { id -> navController.navigate(Routes.editor(id)) },
                    onStart = { id ->
                        navController.navigate(Routes.session(id)) {
                            popUpTo(Routes.WORKOUTS)
                        }
                    },
                )
            }

            composable(Routes.EDITOR) { entry ->
                WorkoutEditorScreen(
                    workoutId = entry.workoutId(),
                    onDone = navController::popBackStack,
                )
            }

            composable(Routes.SESSION) { entry ->
                SessionScreen(
                    contentPadding = innerPadding,
                    onFinished = navController::popBackStack,
                    workoutId = entry.workoutId(),
                )
            }

            composable(Routes.SESSION_RESUME) { entry ->
                SessionScreen(
                    contentPadding = innerPadding,
                    onFinished = navController::popBackStack,
                    resumeSessionId = entry.sessionId(),
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    val backStackEntry by navController.currentBackStackEntryAsState()

    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = backStackEntry?.destination?.hierarchy?.any { it.route == tab.route }
                    ?: (currentRoute == tab.route),
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelResId)) },
            )
        }
    }
}

private fun androidx.navigation.NavBackStackEntry.workoutId(): Long =
    arguments?.getString("workoutId")?.toLongOrNull() ?: Routes.NEW_WORKOUT

private fun androidx.navigation.NavBackStackEntry.sessionId(): Long =
    arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
