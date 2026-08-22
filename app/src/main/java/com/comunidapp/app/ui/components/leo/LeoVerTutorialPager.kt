package com.comunidapp.app.ui.components.leo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comunidapp.app.domain.onboarding.onb02.TutorialDefinition
import com.comunidapp.app.domain.onboarding.onb02.TutorialStep
import com.comunidapp.app.domain.onboarding.onb02.TutorialVisual
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoChip
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoPageTitle
import com.comunidapp.app.ui.theme.MutedText
import com.comunidapp.app.ui.theme.leoVisual

/**
 * Canonical LeoVer tutorial renderer (UI-01 / UX-05).
 *
 * Future tutorials are data ([TutorialDefinition]) + this pager.
 * Do not invent one-off tutorial layouts per flow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeoVerTutorialPager(
    definition: TutorialDefinition,
    stepIndex: Int,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    onPageChange: (Int) -> Unit = {}
) {
    val visual = leoVisual()
    val pagerState = rememberPagerState(
        initialPage = stepIndex.coerceIn(0, definition.steps.lastIndex),
        pageCount = { definition.steps.size }
    )
    LaunchedEffect(stepIndex) {
        if (pagerState.currentPage != stepIndex) {
            pagerState.animateScrollToPage(stepIndex.coerceIn(0, definition.steps.lastIndex))
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != stepIndex) {
            onPageChange(pagerState.currentPage)
        }
    }
    val step = definition.steps.getOrElse(stepIndex) { definition.steps.last() }
    val lastPage = stepIndex >= definition.steps.lastIndex
    Scaffold(
        containerColor = visual.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(visual.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = LeoDimens.SpaceL, vertical = LeoDimens.SpaceL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceM)
            ) {
                TutorialProgressDots(
                    count = definition.steps.size,
                    selected = stepIndex
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.semantics { contentDescription = "Omitir tutorial" }
                    ) {
                        Text("Omitir", color = MutedText)
                    }
                    Spacer(Modifier.weight(1f))
                    LeoPrimaryButton(
                        text = step.primaryCta,
                        onClick = onPrimary,
                        fillMaxWidth = false,
                        modifier = Modifier.semantics {
                            contentDescription = if (lastPage) "Acción final del tutorial" else "Siguiente"
                        }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(padding)
                .tutorialTapNavigation(
                    page = stepIndex,
                    onLeft = {
                        if (stepIndex > 0) onPageChange(stepIndex - 1)
                    },
                    onRight = {
                        if (!lastPage) onPageChange(stepIndex + 1)
                    }
                )
        ) { page ->
            val pageStep = definition.steps[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LeoDimens.SpaceL)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceM)
            ) {
                TutorialVisualComposition(pageStep)
                if (pageStep.titleIsVitacoraWordmark) {
                    VitaCoraWordmarkTitle()
                } else {
                    Text(
                        text = pageStep.title,
                        style = LeoPageTitle.copy(fontSize = 28.sp, lineHeight = 34.sp),
                        color = visual.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = pageStep.body,
                    style = LeoCaption.copy(fontSize = 16.sp, lineHeight = 23.sp),
                    color = visual.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!pageStep.highlight.isNullOrBlank()) {
                    Text(
                        text = pageStep.highlight,
                        style = LeoCaption.copy(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = visual.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (pageStep.chips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pageStep.chips.forEachIndexed { index, chip ->
                            TutorialFeatureChip(label = chip, accent = index == 0)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(LeoDimens.SpaceS))
            }
        }
    }
}

@Composable
private fun VitaCoraWordmarkTitle() {
    val visual = leoVisual()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "VitaCora" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Vit",
            style = LeoPageTitle.copy(fontSize = 28.sp, lineHeight = 34.sp),
            color = visual.textPrimary
        )
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = visual.primary,
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .size(22.dp)
                .offset(y = 1.dp)
        )
        Text(
            text = "Cora",
            style = LeoPageTitle.copy(fontSize = 28.sp, lineHeight = 34.sp),
            color = visual.textPrimary
        )
    }
}

@Composable
fun LeoVerTutorialPage(
    definition: TutorialDefinition,
    stepIndex: Int,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    onPageChange: (Int) -> Unit = {}
) = LeoVerTutorialPager(definition, stepIndex, onPrimary, onSkip, onPageChange)

private fun Modifier.tutorialTapNavigation(
    page: Int,
    onLeft: () -> Unit,
    onRight: () -> Unit
): Modifier = pointerInput(page) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downPos = down.position
        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
        val travel = (up.position - downPos).getDistance()
        if (travel > viewConfiguration.touchSlop) return@awaitEachGesture
        val width = size.width.toFloat().coerceAtLeast(1f)
        when (com.comunidapp.app.domain.onboarding.onb02.TutorialTapPolicy.zone(downPos.x, width)) {
            com.comunidapp.app.domain.onboarding.onb02.TutorialTapZone.PREVIOUS -> onLeft()
            com.comunidapp.app.domain.onboarding.onb02.TutorialTapZone.NEXT -> onRight()
            com.comunidapp.app.domain.onboarding.onb02.TutorialTapZone.IGNORE -> Unit
        }
    }
}

@Composable
private fun TutorialProgressDots(count: Int, selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == selected) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) leoVisual().primary else leoVisual().borderSoft)
            )
        }
    }
}

@Composable
private fun TutorialFeatureChip(label: String, accent: Boolean) {
    val visual = leoVisual()
    Surface(
        shape = RoundedCornerShape(LeoDimens.RadiusCard),
        color = visual.surface,
        border = BorderStroke(1.dp, visual.borderSoft),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = LeoDimens.SpaceM, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LeoDimens.SpaceS)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (accent) visual.accent else visual.primary)
            )
            Text(
                text = label,
                style = LeoChip,
                color = visual.textPrimary
            )
        }
    }
}

private data class TutorialScene(
    val icons: List<ImageVector>,
    val grid: Boolean = false
)

@Composable
private fun TutorialVisualComposition(step: TutorialStep) {
    val scene = sceneFor(step.visual)
    val visual = leoVisual()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(188.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = 10.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(visual.primarySoft.copy(alpha = 0.85f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-14).dp, y = (-6).dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(visual.accentSoft)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
            shape = RoundedCornerShape(28.dp),
            color = visual.surface,
            border = BorderStroke(1.dp, visual.borderSoft),
            shadowElevation = 2.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(visual.primarySoft.copy(alpha = 0.55f))
                )
                if (scene.grid) {
                    val rows = scene.icons.chunked(2)
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        rows.forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEachIndexed { colIndex, icon ->
                                    TutorialIconBadge(
                                        icon = icon,
                                        filled = rowIndex == 0 && colIndex == 0,
                                        compact = true
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy((-6).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        scene.icons.take(3).forEachIndexed { index, icon ->
                            TutorialIconBadge(icon = icon, filled = index == 1)
                        }
                    }
                }
            }
        }
    }
}

private fun sceneFor(visual: TutorialVisual): TutorialScene = when (visual) {
    TutorialVisual.HOME -> TutorialScene(
        listOf(Icons.Default.Person, Icons.Default.Pets, Icons.Default.Groups)
    )
    TutorialVisual.VITACORA -> TutorialScene(
        listOf(Icons.Default.Favorite, Icons.Default.Pets, Icons.Default.Home)
    )
    TutorialVisual.COMMUNITY_HELP -> TutorialScene(
        listOf(Icons.Default.VolunteerActivism, Icons.Default.Pets, Icons.Default.Groups)
    )
    TutorialVisual.SERVICES -> TutorialScene(
        listOf(Icons.Default.LocalHospital, Icons.Default.Storefront, Icons.Default.ContentCut)
    )
    TutorialVisual.FUNCTIONS -> TutorialScene(
        icons = listOf(
            Icons.Default.Person,
            Icons.Default.VolunteerActivism,
            Icons.Default.Home,
            Icons.Default.Storefront
        ),
        grid = true
    )
    TutorialVisual.PERSONAL -> TutorialScene(
        listOf(Icons.Default.Pets, Icons.Default.Person, Icons.Default.Groups)
    )
    TutorialVisual.RESCUER -> TutorialScene(
        listOf(Icons.Default.Groups, Icons.Default.VolunteerActivism, Icons.Default.Pets)
    )
    TutorialVisual.FOSTER -> TutorialScene(
        listOf(Icons.Default.Pets, Icons.Default.Home, Icons.Default.Favorite)
    )
    TutorialVisual.VET -> TutorialScene(
        listOf(Icons.Default.Pets, Icons.Default.LocalHospital, Icons.Default.Favorite)
    )
    TutorialVisual.PROVIDER -> TutorialScene(
        listOf(Icons.Default.Person, Icons.Default.Pets, Icons.Default.School)
    )
    TutorialVisual.DAYCARE -> TutorialScene(
        listOf(Icons.Default.Pets, Icons.Default.Home, Icons.Default.Groups)
    )
    TutorialVisual.ORGANIZATION -> TutorialScene(
        listOf(Icons.Default.Groups, Icons.Default.Storefront, Icons.Default.LocalHospital)
    )
    TutorialVisual.SWITCHER -> TutorialScene(
        listOf(Icons.Default.Person, Icons.Default.SwapHoriz, Icons.Default.Home)
    )
    TutorialVisual.GENERIC -> TutorialScene(
        listOf(Icons.Default.Pets, Icons.Default.Person, Icons.Default.Favorite)
    )
}

@Composable
private fun TutorialIconBadge(
    icon: ImageVector,
    filled: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val visual = leoVisual()
    val size = when {
        compact && filled -> 56.dp
        compact -> 48.dp
        filled -> 72.dp
        else -> 56.dp
    }
    val iconSize = when {
        compact && filled -> 28.dp
        compact -> 22.dp
        filled -> 34.dp
        else -> 24.dp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(LeoDimens.RadiusLarge))
            .background(if (filled) visual.primary else visual.surface)
            .border(
                1.dp,
                if (filled) visual.primary else visual.borderSoft,
                RoundedCornerShape(LeoDimens.RadiusLarge)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) visual.onPrimary else visual.textPrimary,
            modifier = Modifier.size(iconSize)
        )
    }
}
