package com.comunidapp.app.ui.screens.sumate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.screens.adoptions.AdoptionsContent
import com.comunidapp.app.ui.screens.lostfound.LostFoundContent
import com.comunidapp.app.ui.screens.sumate.tabs.AdoptionEventsContent
import com.comunidapp.app.ui.screens.sumate.tabs.DonationsContent
import com.comunidapp.app.ui.screens.sumate.tabs.FosterHomesContent
import com.comunidapp.app.ui.screens.sumate.tabs.SheltersContent
import kotlinx.coroutines.launch

private val sumateTabs = listOf(
    "Adopciones",
    "Perdidos",
    "Tránsito",
    "Eventos",
    "Refugios",
    "Donaciones"
)

@Composable
fun SumateScreen(
    onAdoptionClick: (String) -> Unit,
    onShelterClick: (String) -> Unit,
    onNavigateToMap: () -> Unit = {},
    onMyApplications: () -> Unit = {},
    onReceivedApplications: () -> Unit = {},
    onFosterHomes: () -> Unit = {},
    onShelterOps: () -> Unit = {}
) {
    val pagerState = rememberPagerState(pageCount = { sumateTabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column {
                ComunidappTopBar(title = "Sumate")
                Text(
                    text = "Adopciones, rescate, refugios y causas solidarias",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 16.dp
                ) {
                    sumateTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> AdoptionsContent(
                    onAdoptionClick = onAdoptionClick,
                    onMyApplications = onMyApplications,
                    onReceivedApplications = onReceivedApplications
                )
                1 -> LostFoundContent(onNavigateToMap = onNavigateToMap)
                2 -> FosterHomesContent(onOpenFosterHomes = onFosterHomes)
                3 -> AdoptionEventsContent()
                4 -> SheltersContent(onShelterClick = onShelterClick, onShelterOps = onShelterOps)
                5 -> DonationsContent()
            }
        }
    }
}
