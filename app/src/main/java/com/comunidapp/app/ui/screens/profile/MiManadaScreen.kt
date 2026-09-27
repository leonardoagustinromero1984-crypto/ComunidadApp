package com.comunidapp.app.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.viewmodel.FriendsListViewModel
import com.comunidapp.app.viewmodel.SearchFriendsViewModel
import kotlinx.coroutines.launch

private val MANADA_TABS = listOf("Conexiones", "Solicitudes", "Encontrar personas")

@Composable
fun MiManadaScreen(
    onNavigateBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onMessageClick: (userId: String, name: String) -> Unit,
    initialTab: Int = 0
) {
    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, MANADA_TABS.lastIndex)) { MANADA_TABS.size }
    val scope = rememberCoroutineScope()
    val friendsViewModel: FriendsListViewModel = viewModel()
    val friendsState by friendsViewModel.uiState.collectAsState()
    val incomingCount = friendsState.incomingCount

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Mi manada",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Tu red cercana de personas y mascotas con las que te conectás en LeoVer.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = BrandBackground,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                MANADA_TABS.forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            if (index == 1 && incomingCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(incomingCount.coerceAtMost(99).toString())
                                        }
                                    }
                                ) {
                                    Text(label)
                                }
                            } else {
                                Text(label)
                            }
                        }
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> FriendsConnectionsTab(
                        onUserClick = onUserClick,
                        onMessageClick = onMessageClick,
                        viewModel = friendsViewModel
                    )
                    1 -> FriendRequestsTab(
                        onUserClick = onUserClick,
                        viewModel = friendsViewModel
                    )
                    else -> SearchPeopleTab(
                        onUserClick = onUserClick,
                        viewModel = viewModel()
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendsConnectionsTab(
    onUserClick: (String) -> Unit,
    onMessageClick: (userId: String, name: String) -> Unit,
    viewModel: FriendsListViewModel
) {
    FriendsListScreen(
        onNavigateBack = {},
        onUserClick = onUserClick,
        onMessageClick = onMessageClick,
        showIncomingRequests = false,
        showTopBar = false,
        connectionsOnly = true,
        viewModel = viewModel
    )
}

@Composable
private fun FriendRequestsTab(
    onUserClick: (String) -> Unit,
    viewModel: FriendsListViewModel
) {
    FriendsListScreen(
        onNavigateBack = {},
        onUserClick = onUserClick,
        onMessageClick = { _, _ -> },
        showIncomingRequests = true,
        showOutgoingRequests = true,
        showTopBar = false,
        connectionsOnly = false,
        requestsOnly = true,
        viewModel = viewModel
    )
}

@Composable
private fun SearchPeopleTab(
    onUserClick: (String) -> Unit,
    viewModel: SearchFriendsViewModel
) {
    SearchFriendsScreen(
        onNavigateBack = {},
        onUserClick = onUserClick,
        showTopBar = false,
        viewModel = viewModel
    )
}
