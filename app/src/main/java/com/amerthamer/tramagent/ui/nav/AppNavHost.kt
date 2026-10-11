package com.amerthamer.tramagent.ui.nav

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.amerthamer.tramagent.ui.alerts.AlertsScreen
import com.amerthamer.tramagent.ui.components.TramArriveEasing
import com.amerthamer.tramagent.ui.components.TramDepartEasing
import com.amerthamer.tramagent.ui.gtfs.GtfsScreen
import com.amerthamer.tramagent.ui.home.HomeScreen
import com.amerthamer.tramagent.ui.map.MapScreen
import com.amerthamer.tramagent.ui.nearby.NearbyScreen
import com.amerthamer.tramagent.ui.tracking.TrackingScreen
import com.amerthamer.tramagent.ui.vehicles.VehicleLookupScreen

private const val HOME = "home"
private const val ARG_VEHICLE = "vehicle"

/** Útvonal egy opcionális pályaszámmal (pl. „dk5?vehicle=1342”). */
private fun withVehicle(feature: Feature, vehicle: String) = "${feature.route}?$ARG_VEHICLE=${Uri.encode(vehicle)}"

/** Mint a dropUnlessResumed, de paraméteres callbackhez: csak RESUMED állapotban fut le. */
@Composable
private fun <T> dropUnlessResumedWith(block: (T) -> Unit): (T) -> Unit {
    val owner = LocalLifecycleOwner.current
    return { value -> if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(value) }
}

/**
 * Képernyőváltások „villamososan”: az új oldal beáll (jobbról becsúszik és fékez),
 * a régi kigördül balra. Visszalépéskor fordítva.
 */
@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    // dropUnlessResumed: átmenet közbeni dupla koppintás ne navigáljon kétszer / ne ürítse ki a vermet
    val back = dropUnlessResumed { nav.popBackStack() }
    val vehicleArg = listOf(navArgument(ARG_VEHICLE) { type = NavType.StringType; nullable = true; defaultValue = null })

    NavHost(
        navController = nav,
        startDestination = HOME,
        enterTransition = { slideInHorizontally(tween(600, easing = TramArriveEasing)) { it } + fadeIn(tween(300)) },
        exitTransition = { slideOutHorizontally(tween(450, easing = TramDepartEasing)) { -it / 3 } + fadeOut(tween(450)) },
        popEnterTransition = { slideInHorizontally(tween(600, easing = TramArriveEasing)) { -it / 3 } + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(tween(450, easing = TramDepartEasing)) { it } + fadeOut(tween(450)) },
    ) {
        composable(HOME) {
            HomeScreen(onOpen = dropUnlessResumedWith { feature: Feature -> nav.navigate(feature.route) })
        }
        composable(Feature.VehicleLookup.route) {
            VehicleLookupScreen(
                onBack = back,
                onTrack = { nav.navigate(withVehicle(Feature.Tracking, it)) },
                onShowOnMap = { nav.navigate(withVehicle(Feature.MapSearch, it)) },
            )
        }
        composable(Feature.Alerts.route) { AlertsScreen(onBack = back) }
        composable(Feature.Nearby.route) {
            NearbyScreen(onBack = back, onOpenGtfs = { nav.navigate(Feature.Gtfs.route) })
        }
        composable("${Feature.MapSearch.route}?$ARG_VEHICLE={$ARG_VEHICLE}", arguments = vehicleArg) { entry ->
            MapScreen(
                initialVehicle = entry.arguments?.getString(ARG_VEHICLE),
                onBack = back,
                onTrack = { nav.navigate(withVehicle(Feature.Tracking, it)) },
            )
        }
        composable("${Feature.Tracking.route}?$ARG_VEHICLE={$ARG_VEHICLE}", arguments = vehicleArg) { entry ->
            TrackingScreen(
                initialVehicle = entry.arguments?.getString(ARG_VEHICLE),
                onBack = back,
                onShowOnMap = { nav.navigate(withVehicle(Feature.MapSearch, it)) },
            )
        }
        composable(Feature.Gtfs.route) { GtfsScreen(onBack = back) }
    }
}
