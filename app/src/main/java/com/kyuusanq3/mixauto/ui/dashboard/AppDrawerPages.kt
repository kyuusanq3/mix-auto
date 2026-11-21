package com.kyuusanq3.mixauto.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
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
import com.kyuusanq3.mixauto.ui.firstparty.FirstPartyAppEntry
import com.kyuusanq3.mixauto.ui.settings.AppDrawerPagingMode
import com.kyuusanq3.mixauto.ui.settings.LauncherPreferences
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarLabelText

internal val AppDrawerCellHeight = CarDimensions.AppIconSize + CarDimensions.PaneGap * 2

private fun drawerCellHeight(available: Dp, rows: Int, gap: Dp): Dp {
    val safeRows = rows.coerceAtLeast(1)
    return ((available - gap * (safeRows - 1)) / safeRows)
        .coerceAtLeast(AppDrawerCellHeight)
}

private fun drawerCellWidth(available: Dp, columns: Int, gap: Dp): Dp {
    val safeColumns = columns.coerceAtLeast(1)
    return ((available - gap * (safeColumns - 1)) / safeColumns)
        .coerceAtLeast(AppDrawerCellHeight)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppDrawerAppGrid(
    apps: List<LaunchableAppEntry>,
    columnCount: Int,
    rowCount: Int,
    pagingMode: AppDrawerPagingMode,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onLaunch: (LaunchableAppEntry) -> Unit,
    mixAutoApps: List<FirstPartyAppEntry> = emptyList(),
    onLaunchMixAuto: (FirstPartyAppEntry) -> Unit = {},
    labelSizeSp: Int = LauncherPreferences.DEFAULT_APP_DRAWER_LABEL_SP,
    modifier: Modifier = Modifier,
) {
    val columns = columnCount.coerceIn(
        LauncherPreferences.MIN_APP_DRAWER_COLUMNS,
        LauncherPreferences.MAX_APP_DRAWER_COLUMNS,
    )
    val preferredRows = rowCount.coerceIn(
        LauncherPreferences.MIN_APP_DRAWER_ROWS,
        LauncherPreferences.MAX_APP_DRAWER_ROWS,
    )
    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val gap = CarDimensions.PaneGap
            val reserveIndicator = pagingMode.isSwipePaged
            val indicatorHeight = if (reserveIndicator) CarDimensions.PaneGap * 3 else 0.dp
            val gridHeight = (maxHeight - indicatorHeight).coerceAtLeast(AppDrawerCellHeight)
            val fittedFull = rowsThatFit(gridHeight, gap, AppDrawerCellHeight).coerceAtLeast(1)
            val fullRows = preferredRows.coerceAtMost(fittedFull).coerceAtLeast(1)
            val cellHeight = drawerCellHeight(gridHeight, fullRows, gap)
            val cellWidth = drawerCellWidth(maxWidth, columns, gap)
            val pageSlots = columns * fullRows
            val firstInstalledSlots = (pageSlots - mixAutoApps.size).coerceAtLeast(0)
            val pages = remember(apps, columns, fullRows, mixAutoApps.size) {
                drawerPages(
                    apps = apps,
                    firstPageSlots = firstInstalledSlots,
                    laterPageSlots = pageSlots,
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
                        AppDrawerPagingMode.SCROLL_VERTICAL -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                items(
                                    items = mixAutoApps,
                                    key = { "mix_${it.id}" },
                                ) { app ->
                                    MixAutoDrawerItem(
                                        app = app,
                                        labelSizeSp = labelSizeSp,
                                        onLaunch = { onLaunchMixAuto(app) },
                                        modifier = Modifier.height(cellHeight),
                                    )
                                }
                                items(apps, key = { it.packageName }) { app ->
                                    DrawerGridItem(
                                        app = app,
                                        cellHeight = cellHeight,
                                        labelSizeSp = labelSizeSp,
                                        dockPinnedPackages = dockPinnedPackages,
                                        maxDockPinnedApps = maxDockPinnedApps,
                                        onToggleDockPin = onToggleDockPin,
                                        onLaunch = onLaunch,
                                    )
                                }
                            }
                        }
                        AppDrawerPagingMode.SCROLL_HORIZONTAL -> {
                            val scrollCells = remember(mixAutoApps, apps, fullRows) {
                                columnMajorItems(
                                    items = mixAutoApps.map { DrawerPageCell.MixAuto(it) } +
                                        apps.map { DrawerPageCell.Installed(it) },
                                    rows = fullRows,
                                )
                            }
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(fullRows),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                items(
                                    items = scrollCells,
                                    key = { cell ->
                                        when (cell) {
                                            is DrawerPageCell.MixAuto -> "mix_${cell.app.id}"
                                            is DrawerPageCell.Installed -> cell.app.packageName
                                        }
                                    },
                                ) { cell ->
                                    val itemModifier = Modifier
                                        .width(cellWidth)
                                        .fillMaxHeight()
                                    when (cell) {
                                        is DrawerPageCell.MixAuto -> MixAutoDrawerItem(
                                            app = cell.app,
                                            labelSizeSp = labelSizeSp,
                                            onLaunch = { onLaunchMixAuto(cell.app) },
                                            modifier = itemModifier,
                                        )
                                        is DrawerPageCell.Installed -> DrawerGridItem(
                                            app = cell.app,
                                            cellHeight = null,
                                            labelSizeSp = labelSizeSp,
                                            dockPinnedPackages = dockPinnedPackages,
                                            maxDockPinnedApps = maxDockPinnedApps,
                                            onToggleDockPin = onToggleDockPin,
                                            onLaunch = onLaunch,
                                            modifier = itemModifier,
                                        )
                                    }
                                }
                            }
                        }
                        AppDrawerPagingMode.SWIPE_HORIZONTAL -> {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                pageSpacing = gap,
                            ) { page ->
                                AppDrawerPage(
                                    apps = pages.getOrElse(page) { emptyList() },
                                    mixAutoApps = if (page == 0) mixAutoApps else emptyList(),
                                    columnCount = columns,
                                    pageRowSlots = fullRows,
                                    labelSizeSp = labelSizeSp,
                                    dockPinnedPackages = dockPinnedPackages,
                                    maxDockPinnedApps = maxDockPinnedApps,
                                    onToggleDockPin = onToggleDockPin,
                                    onLaunch = onLaunch,
                                    onLaunchMixAuto = onLaunchMixAuto,
                                )
                            }
                        }
                        AppDrawerPagingMode.SWIPE_VERTICAL -> {
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                pageSpacing = gap,
                            ) { page ->
                                AppDrawerPage(
                                    apps = pages.getOrElse(page) { emptyList() },
                                    mixAutoApps = if (page == 0) mixAutoApps else emptyList(),
                                    columnCount = columns,
                                    pageRowSlots = fullRows,
                                    labelSizeSp = labelSizeSp,
                                    dockPinnedPackages = dockPinnedPackages,
                                    maxDockPinnedApps = maxDockPinnedApps,
                                    onToggleDockPin = onToggleDockPin,
                                    onLaunch = onLaunch,
                                    onLaunchMixAuto = onLaunchMixAuto,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class DrawerPageCell {
    data class MixAuto(val app: FirstPartyAppEntry) : DrawerPageCell()
    data class Installed(val app: LaunchableAppEntry) : DrawerPageCell()
}

@Composable
private fun AppDrawerPage(
    apps: List<LaunchableAppEntry>,
    mixAutoApps: List<FirstPartyAppEntry>,
    columnCount: Int,
    pageRowSlots: Int,
    labelSizeSp: Int,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onLaunch: (LaunchableAppEntry) -> Unit,
    onLaunchMixAuto: (FirstPartyAppEntry) -> Unit,
) {
    val cells = mixAutoApps.map { DrawerPageCell.MixAuto(it) } +
        apps.map { DrawerPageCell.Installed(it) }
    val rows = cells.chunked(columnCount)
    val slots = pageRowSlots.coerceAtLeast(1)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        repeat(slots) { index ->
            val rowCells = rows.getOrNull(index)
            if (rowCells != null) {
                DrawerMixedRow(
                    rowCells = rowCells,
                    columnCount = columnCount,
                    labelSizeSp = labelSizeSp,
                    dockPinnedPackages = dockPinnedPackages,
                    maxDockPinnedApps = maxDockPinnedApps,
                    onToggleDockPin = onToggleDockPin,
                    onLaunch = onLaunch,
                    onLaunchMixAuto = onLaunchMixAuto,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DrawerMixedRow(
    rowCells: List<DrawerPageCell>,
    columnCount: Int,
    labelSizeSp: Int,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onLaunch: (LaunchableAppEntry) -> Unit,
    onLaunchMixAuto: (FirstPartyAppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        rowCells.forEach { cell ->
            when (cell) {
                is DrawerPageCell.MixAuto -> MixAutoDrawerItem(
                    app = cell.app,
                    labelSizeSp = labelSizeSp,
                    onLaunch = { onLaunchMixAuto(cell.app) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                is DrawerPageCell.Installed -> DrawerGridItem(
                    app = cell.app,
                    cellHeight = null,
                    labelSizeSp = labelSizeSp,
                    dockPinnedPackages = dockPinnedPackages,
                    maxDockPinnedApps = maxDockPinnedApps,
                    onToggleDockPin = onToggleDockPin,
                    onLaunch = onLaunch,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(columnCount - rowCells.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun <T> columnMajorItems(items: List<T>, rows: Int): List<T> {
    if (rows <= 1 || items.isEmpty()) return items
    val columns = (items.size + rows - 1) / rows
    val ordered = ArrayList<T>(items.size)
    for (column in 0 until columns) {
        for (row in 0 until rows) {
            val index = row * columns + column
            if (index < items.size) {
                ordered.add(items[index])
            }
        }
    }
    return ordered
}

private fun rowsThatFit(height: Dp, gap: Dp, cellHeight: Dp): Int {
    if (height < cellHeight) return 0
    return ((height + gap) / (cellHeight + gap)).toInt()
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
    cellHeight: Dp?,
    labelSizeSp: Int,
    dockPinnedPackages: List<String>,
    maxDockPinnedApps: Int,
    onToggleDockPin: (String) -> Unit,
    onLaunch: (LaunchableAppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeModifier = if (cellHeight != null) {
        modifier.height(cellHeight)
    } else {
        modifier.fillMaxHeight()
    }
    AppDrawerItem(
        app = app,
        isPinnedToDock = app.packageName in dockPinnedPackages,
        canAddToDock = app.packageName in dockPinnedPackages ||
            dockPinnedPackages.size < maxDockPinnedApps,
        onToggleDockPin = { onToggleDockPin(app.packageName) },
        onLaunch = { onLaunch(app) },
        labelSizeSp = labelSizeSp,
        modifier = sizeModifier,
    )
}
