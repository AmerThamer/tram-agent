package com.amerthamer.tramagent.ui.nav

import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amerthamer.tramagent.ui.components.TramArriveEasing
import com.amerthamer.tramagent.ui.components.TramDepartEasing
import com.amerthamer.tramagent.ui.home.HomeScreen

private const val HOME = "home"

/**
 * Képernyőváltások „villamososan”: az új oldal beáll (jobbról becsúszik és fékez),
 * a régi kigördül balra. Visszalépéskor fordítva.
 */
@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = nav,
        startDestination = HOME,
        enterTransition = { slideInHorizontally(tween(600, easing = TramArriveEasing)) { it } + fadeIn(tween(300)) },
        exitTransition = { slideOutHorizontally(tween(450, easing = TramDepartEasing)) { -it / 3 } + fadeOut(tween(450)) },
        popEnterTransition = { slideInHorizontally(tween(600, easing = TramArriveEasing)) { -it / 3 } + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(tween(450, easing = TramDepartEasing)) { it } + fadeOut(tween(450)) },
    ) {
        composable(HOME) {
            HomeScreen(onOpen = { feature ->
                val legacy = feature.legacyActivity
                if (legacy != null) context.startActivity(Intent(context, legacy))
                else nav.navigate(feature.route)
            })
        }
        // Ide kerülnek az átírt képernyők, pl.:
        // composable(Feature.Alerts.route) { AlertsScreen(onBack = nav::popBackStack) }
    }
}
