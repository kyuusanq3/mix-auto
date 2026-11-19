package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyuusanq3.mixauto.data.apps.LaunchableAppEntry
import com.kyuusanq3.mixauto.ui.settings.AppDrawerPagingMode
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText

internal val AppDrawerCellHeight = CarDimensions.AppIconSize + CarDimensions.PaneGap * 2
internal val MixAutoLabelBlockHeight = 32.dp
internal val MixAutoGroupGap = CarDimensions.PaneGap * 3

internal fun mixAutoHeaderHeight(appCount: Int, columnCount: Int): Dp {
    if (appCount <= 0) return 0.dp
    val columns = columnCount.coerceAtLeast(1)
    val groupRows = (appCount + columns - 1) / columns
    val cells = AppDrawerCellHeight * groupRows +
        CarDimensions.PaneGap * (groupRows - 1).coerceAtLeast(0)
    return MixAutoLabelBlockHeight + cells
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppDrawerAppGrid(
    apps: List<LaunchableAppEntry>,
    columnCount: Int,
    pagingMode: AppDrawerPagingMode,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onLaunch: (LaunchableAppEntry) -> Unit,
    mixAutoAppCount: Int = 0,
    mixAutoHeader: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val columns = columnCount.coerceIn(
        LauncherPreferences.MIN_APP_DRAWER_COLUMNS,
        LauncherPreferences.MAX_APP_DRAWER_COLUMNS,
    )
    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val gap = CarDimensions.PaneGap
            val reserveIndicator = pagingMode != AppDrawerPagingMode.SCROLL
            val indicatorHeight = if (reserveIndicator) CarDimensions.PaneGap * 3 else 0.dp
            val gridHeight = (maxHeight - indicatorHeight).coerceAtLeast(AppDrawerCellHeight)
            val fullRows = rowsThatFit(gridHeight, gap).coerceAtLeast(1)
            val showMixAutoOnFirstPage = mixAutoHeader != null && mixAutoAppCount > 0 && reserveIndicator
            val firstPageHeight = if (showMixAutoOnFirstPage) {
                (gridHeight - mixAutoHeaderHeight(mixAutoAppCount, columns) - MixAutoGroupGap)
                    .coerceAtLeast(0.dp)
            } else {
                gridHeight
            }
            val firstRows = if (showMixAutoOnFirstPage) {
                rowsThatFit(firstPageHeight, gap)
            } else {
                fullRows
            }
            val pages = remember(apps, columns, firstRows, fullRows) {
                drawerPages(
                    apps = apps,
                    firstPageSlots = columns * firstRows,
                    laterPageSlots = columns * fullRows,
                )
            }
            val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
            LaunchedEffect(pages.size) {
                val lastPage = (pages.size - 1).coerceAtLeast(0)
                if (pagerState.currentPage > lastPage) {
                    pagerState.scrollToPage(lastPage)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                if (reserveIndicator) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(indicatorHeight),
                    ) {
                        if (pages.size > 1) {
                            CarLabelText(
                                text = "Page ${pagerState.currentPage + 1} of ${pages.size}",
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (pagingMode) {
                        AppDrawerPagingMode.SCROLL -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                if (mixAutoHeader != null && mixAutoAppCount > 0) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            mixAutoHeader()
                                            Spacer(modifier = Modifier.height(MixAutoGroupGap))
                                        }
                                    }
                                }
                                items(apps, key = { it.packageName }) { app ->
                                    DrawerGridItem(
                                        app = app,
                                        cellHeight = AppDrawerCellHeight,
                                        dockPinnedPackages = dockPinnedPackages,
                                        maxDockPinnedApps = maxDockPinnedApps,
                                        onToggleDockPin = onToggleDockPin,
                                        onLaunch = onLaunch,
                                    )
                                }
                            }
                        }
                        AppDrawerPagingMode.SWIPE_HORIZONTAL -> {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                AppDrawerPage(
                                    apps = pages.getOrElse(page) { emptyList() },
                                    columnCount = columns,
                                    cellHeight = AppDrawerCellHeight,
                                    header = if (page == 0 && showMixAutoOnFirstPage) mixAutoHeader else null,
                                    dockPinnedPackages = dockPinnedPackages,
                                    maxDockPinnedApps = maxDockPinnedApps,
                                    onToggleDockPin = onToggleDockPin,
                                    onLaunch = onLaunch,
                                )
                            }
                        }
                        AppDrawerPagingMode.SWIPE_VERTICAL -> {
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                AppDrawerPage(
                                    apps = pages.getOrElse(page) { emptyList() },
                                    columnCount = columns,
                                    cellHeight = AppDrawerCellHeight,
                                    header = if (page == 0 && showMixAutoOnFirstPage) mixAutoHeader else null,
                                    dockPinnedPackages = dockPinnedPackages,
                                    maxDockPinnedApps = maxDockPinnedApps,
                                    onToggleDockPin = onToggleDockPin,
                                    onLaunch = onLaunch,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDrawerPage(
    apps: List<LaunchableAppEntry>,
    columnCount: Int,
    cellHeight: Dp,
    header: (@Composable () -> Unit)?,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onLaunch: (LaunchableAppEntry) -> Unit,
) {
    val rows = apps.chunked(columnCount)
    Column(modifier = Modifier.fillMaxSize()) {
        if (header != null) {
            header()
            Spacer(modifier = Modifier.height(MixAutoGroupGap))
        }
        Column(verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap)) {
            rows.forEach { rowApps ->
                DrawerAppRow(
                    rowApps = rowApps,
                    columnCount = columnCount,
                    cellHeight = cellHeight,
                    dockPinnedPackages = dockPinnedPackages,
                    maxDockPinnedApps = maxDockPinnedApps,
                    onToggleDockPin = onToggleDockPin,
                    onLaunch = onLaunch,
                )
            }
        }
    }
}

@Composable
private fun DrawerAppRow(
    rowApps: List<LaunchableAppEntry>,
    columnCount: Int,
    cellHeight: Dp,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onLaunch: (LaunchableAppEntry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight),
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        rowApps.forEach { app ->
            DrawerGridItem(
                app = app,
                cellHeight = cellHeight,
                dockPinnedPackages = dockPinnedPackages,
                maxDockPinnedApps = maxDockPinnedApps,
                onToggleDockPin = onToggleDockPin,
                onLaunch = onLaunch,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(columnCount - rowApps.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun rowsThatFit(height: Dp, gap: Dp): Int {
    if (height < AppDrawerCellHeight) return 0
    return ((height + gap) / (AppDrawerCellHeight + gap)).toInt()
}

private fun drawerPages(
    apps: List<LaunchableAppEntry>,
    firstPageSlots: Int,
    laterPageSlots: Int,
): List<List<LaunchableAppEntry>> {
    if (apps.isEmpty()) return listOf(emptyList())
    val safeLater = laterPageSlots.coerceAtLeast(1)
    val firstCount = firstPageSlots.coerceAtLeast(0).coerceAtMost(apps.size)
    val first = apps.take(firstCount)
    val rest = apps.drop(firstCount)
    if (rest.isEmpty()) return listOf(first)
    return listOf(first) + rest.chunked(safeLater)
}

@Composable
private fun DrawerGridItem(
    app: LaunchableAppEntry,
    cellHeight: Dp,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onLaunch: (LaunchableAppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppDrawerItem(
        app = app,
        isPinnedToDock = app.packageName in dockPinnedPackages,
        canAddToDock = app.packageName in dockPinnedPackages ||
            dockPinnedPackages.size < maxDockPinnedApps,
        onToggleDockPin = { onToggleDockPin(app.packageName) },
        onLaunch = { onLaunch(app) },
        modifier = modifier.height(cellHeight),
    )
}
