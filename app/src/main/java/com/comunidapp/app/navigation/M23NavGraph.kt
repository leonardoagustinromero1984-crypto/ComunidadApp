package com.comunidapp.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.comunidapp.app.data.model.M23MockOfferingIds
import com.comunidapp.app.data.model.M23MockProviderRefs
import com.comunidapp.app.data.model.M23SlotQuery
import com.comunidapp.app.ui.screens.m23.*
import com.comunidapp.app.viewmodel.M23ViewModelFactories
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.ZoneId

fun NavGraphBuilder.m23BookingRoutes(navController: NavHostController) {
    composable(NavRoutes.M23_HOME) { M23HomeScreen({ navController.popBackStack() }, { navController.navigate(NavRoutes.M23_BOOKINGS) }, { navController.navigate(NavRoutes.M23_MANAGE) }) }
    composable(NavRoutes.M23_PROVIDER) { M23AvailabilityScreen({ navController.popBackStack() }, viewModel(factory = M23ViewModelFactories.availability(M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.now(), LocalDate.now().plusDays(7), ZoneId.of("America/Argentina/Buenos_Aires"))))) }
    composable(NavRoutes.M23_AVAILABILITY) { M23AvailabilityScreen({ navController.popBackStack() }, viewModel(factory = M23ViewModelFactories.availability(M23SlotQuery(M23MockProviderRefs.ACTIVE_MULTI_BRANCH, M23MockOfferingIds.BATH, LocalDate.now(), LocalDate.now().plusDays(7), ZoneId.of("America/Argentina/Buenos_Aires"))))) }
    composable(NavRoutes.M23_BOOKINGS) { M23MyBookingsScreen({ navController.popBackStack() }, { navController.navigate(NavRoutes.m23BookingDetail(it)) }) }
    composable(NavRoutes.M23_BOOKING_DETAIL, arguments = listOf(navArgument(NavRoutes.ARG_M23_BOOKING_ID) { type = NavType.StringType })) { entry -> val id = entry.arguments?.getString(NavRoutes.ARG_M23_BOOKING_ID).orEmpty(); M23BookingDetailScreen({ navController.popBackStack() }, viewModel(factory = M23ViewModelFactories.detail(id))) }
    composable(NavRoutes.M23_MANAGE) { M23ManageScreen({ navController.popBackStack() }, { navController.navigate(NavRoutes.M23_MANAGE_CALENDAR) }, { navController.navigate(NavRoutes.M23_MANAGE_BOOKING_DETAIL) }) }
    composable(NavRoutes.M23_MANAGE_CALENDAR) { M23ManageCalendarScreen({ navController.popBackStack() }, viewModel(factory = M23ViewModelFactories.calendar(M23MockProviderRefs.ACTIVE_MULTI_BRANCH))) }
    composable(NavRoutes.M23_MANAGE_BOOKING_DETAIL) { M23ManageBookingsScreen({ navController.popBackStack() }, { navController.navigate(NavRoutes.m23BookingDetail(it)) }, viewModel(factory = M23ViewModelFactories.bookings(M23MockProviderRefs.ACTIVE_MULTI_BRANCH))) }
}
