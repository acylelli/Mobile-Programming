package com.example.routealarm.presentation.schedule.create

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppIcons
import com.example.routealarm.core.designsystem.component.ErrorState
import com.example.routealarm.core.designsystem.component.InlineAction
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.NoticeStrip
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.presentation.common.messageRes

/**
 * 새 일정 1·2단계: 출발지 선택(UI-C-001), 도착지 선택(UI-C-002).
 * 검색창, 즐겨찾기(학교·집·회사), 최근 검색을 한 화면에 두고, 고른 항목은 강조색 테두리로 표시한다.
 */
@Composable
internal fun PlaceStep(
    isOrigin: Boolean,
    state: CreateScheduleUiState,
    query: String,
    savedPlaces: List<Place>,
    recentSearches: List<Place>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (Place, Boolean) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onStartRegistering: (PlaceType) -> Unit,
    onCancelRegistering: () -> Unit,
    onDeleteFavorite: (Long) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var deleteTarget by remember { mutableStateOf<Place?>(null) }
    // 장소를 고르면 검색을 마친 것이므로 키보드와 커서를 내린다.
    val select: (Place, Boolean) -> Unit = { place, fromSearch ->
        focusManager.clearFocus()
        onSelect(place, fromSearch)
    }
    val selected = state.selectedPlace
    val registering = state.registeringType

    // 즐겨찾기 빈 칸을 누르면 바로 검색할 수 있게 검색창에 포커스를 준다.
    LaunchedEffect(registering) {
        if (registering != null) runCatching { focusRequester.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        StepTitle(stringResource(if (isOrigin) R.string.create_origin_question else R.string.create_destination_question))
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            focusRequester = focusRequester,
            placeholder = if (registering != null) {
                stringResource(R.string.create_register_hint, placeTypeName(registering))
            } else {
                stringResource(R.string.place_search_hint)
            },
        )
        if (registering != null) {
            Spacer(Modifier.height(8.dp))
            NoticeStrip(
                text = stringResource(R.string.create_register_notice, placeTypeName(registering)),
                action = { InlineAction(stringResource(R.string.common_cancel), onClick = onCancelRegistering) },
            )
        }
        Spacer(Modifier.height(Spacing.lg))

        if (query.isNotBlank()) {
            SearchResults(state.search, selected, onSelect = { select(it, true) }, onRetry = onSearch)
        } else {
            val slotOrder = if (isOrigin) ORIGIN_SLOTS else DESTINATION_SLOTS
            val slots = slotOrder.map { type -> type to savedPlaces.firstOrNull { it.type == type } }
            val slotPlaces = slots.mapNotNull { it.second }
            val customFavorites = savedPlaces.filter { place -> slotPlaces.none { it.id == place.id } }
            val isCurrentLocation = isOrigin && state.useCurrentLocation
            // 수정 중인 일정의 장소처럼 아래 목록 어디에도 없는 선택은 맨 위에 따로 보여준다.
            val listed = slotPlaces + customFavorites + recentSearches
            if (selected != null && !isCurrentLocation && listed.none { it.isSameLocationAs(selected) }) {
                SectionLabel(stringResource(R.string.create_selected_place))
                Spacer(Modifier.height(8.dp))
                PlaceRow(Icons.Outlined.Place, selected.name, selected.address.ifBlank { null }, selected = true, onClick = {})
                Spacer(Modifier.height(Spacing.lg))
            }

            if (isOrigin) {
                PlaceRow(
                    icon = Icons.Outlined.MyLocation,
                    title = stringResource(R.string.create_current_location_name),
                    subtitle = stringResource(R.string.create_current_location_body),
                    selected = isCurrentLocation,
                    onClick = onUseCurrentLocation,
                )
                Spacer(Modifier.height(Spacing.lg))
            }

            SectionLabel(stringResource(R.string.create_saved_places))
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                slots.forEach { (type, place) ->
                    val label = placeTypeName(type)
                    if (place != null) {
                        PlaceRow(
                            icon = placeIcon(type),
                            title = label,
                            subtitle = if (place.name == label) place.address else stringResource(R.string.place_name_address_format, place.name, place.address),
                            selected = !isCurrentLocation && selected?.isSameLocationAs(place) == true,
                            onClick = { select(place, false) },
                            onLongClick = { deleteTarget = place },
                        )
                    } else {
                        PlaceRow(
                            icon = placeIcon(type),
                            title = label,
                            subtitle = stringResource(R.string.create_register_place),
                            selected = false,
                            onClick = { onStartRegistering(type) },
                        )
                    }
                }
                customFavorites.forEach { place ->
                    PlaceRow(
                        icon = Icons.Outlined.Place,
                        title = place.name,
                        subtitle = place.address.ifBlank { null },
                        selected = !isCurrentLocation && selected?.isSameLocationAs(place) == true,
                        onClick = { select(place, false) },
                        onLongClick = { deleteTarget = place },
                    )
                }
            }

            if (recentSearches.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel(stringResource(R.string.create_recent_searches))
                Spacer(Modifier.height(4.dp))
                recentSearches.forEach { place ->
                    PlaceRow(
                        icon = Icons.Outlined.Schedule,
                        title = place.name,
                        subtitle = placeSubtitle(place),
                        selected = !isCurrentLocation && selected?.isSameLocationAs(place) == true &&
                            slotPlaces.none { it.isSameLocationAs(place) },
                        onClick = { select(place, false) },
                        plainIcon = true,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
    }

    deleteTarget?.let { place ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.create_delete_favorite_title)) },
            text = { Text(stringResource(R.string.create_delete_favorite_body, place.name)) },
            confirmButton = {
                TextButton(onClick = { onDeleteFavorite(place.id); deleteTarget = null }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

private val DESTINATION_SLOTS = listOf(PlaceType.SCHOOL, PlaceType.HOME, PlaceType.WORK)
private val ORIGIN_SLOTS = listOf(PlaceType.HOME, PlaceType.SCHOOL, PlaceType.WORK)

@Composable
private fun SearchResults(search: PlaceSearchState, selected: Place?, onSelect: (Place) -> Unit, onRetry: () -> Unit) {
    when (search) {
        PlaceSearchState.Idle, PlaceSearchState.Loading -> LoadingState()
        is PlaceSearchState.Error -> ErrorState(
            message = stringResource(search.error.messageRes()),
            onRetry = onRetry,
            retryText = stringResource(R.string.common_retry),
        )
        is PlaceSearchState.Results -> if (search.places.isEmpty()) {
            Text(
                stringResource(R.string.place_search_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = RouteAlarmTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = Spacing.lg),
            )
        } else {
            SectionLabel(stringResource(R.string.place_search_results))
            Spacer(Modifier.height(4.dp))
            search.places.forEach { place ->
                PlaceRow(
                    icon = Icons.Outlined.Place,
                    title = place.name,
                    subtitle = placeSubtitle(place),
                    selected = selected?.isSameLocationAs(place) == true,
                    onClick = { onSelect(place) },
                    plainIcon = true,
                )
            }
        }
    }
}

/** 장소 검색창(②). 키워드로 장소·주소를 찾는다. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    placeholder: String,
) {
    val colors = RouteAlarmTheme.colors
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(colors.surface, Radius.field)
            .focusRequester(focusRequester)
            // 안내 문구는 입력을 시작하면 사라지므로, 화면 낭독기에는 입력창의 이름으로 알려준다.
            .semantics { contentDescription = placeholder },
        // 회색 상자 전체가 입력창이 되도록 아이콘과 안내 문구를 입력창 안에 그린다.
        decorationBox = { innerTextField ->
            Row(
                Modifier.padding(start = 16.dp, end = if (query.isEmpty()) 16.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_search), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    )
}

/**
 * 장소 한 줄. 선택된 항목은 강조색 테두리·옅은 강조 배경·강조색 아이콘 타일로 표시한다(③).
 * [plainIcon] 은 최근 검색처럼 타일 없이 아이콘만 두는 가벼운 줄이다(④).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    plainIcon: Boolean = false,
) {
    val colors = RouteAlarmTheme.colors
    val shape = Radius.group
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (selected) Modifier.background(colors.accentContainer).border(1.5.dp, colors.accent, shape) else Modifier)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = if (plainIcon) 11.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (plainIcon) {
            Icon(icon, contentDescription = null, tint = if (selected) colors.accentText else colors.textTertiary, modifier = Modifier.size(22.dp))
        } else {
            Box(
                Modifier.size(42.dp).background(if (selected) colors.accent else colors.surface, Radius.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) colors.onAccent else colors.textPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!plainIcon) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = if (selected) colors.accentText else colors.textTertiary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 최근 검색·검색 결과의 둘째 줄: 노선 정보가 있으면 "서울 강남구 | 2호선, 신분당선", 없으면 주소 */
@Composable
private fun placeSubtitle(place: Place): String? = when {
    place.transitLines.isNotEmpty() -> stringResource(
        R.string.place_name_address_format,
        place.address.split(" ").take(2).joinToString(" "),
        place.transitLines.joinToString(", "),
    )
    else -> place.address.ifBlank { null }
}

@Composable
internal fun placeIcon(type: PlaceType): ImageVector = when (type) {
    PlaceType.HOME -> AppIcons.Home
    PlaceType.SCHOOL -> Icons.Outlined.School
    PlaceType.WORK -> Icons.Outlined.WorkOutline
    PlaceType.CUSTOM -> Icons.Outlined.Place
}

@Composable
internal fun placeTypeName(type: PlaceType): String = stringResource(
    when (type) {
        PlaceType.HOME -> R.string.place_type_home
        PlaceType.SCHOOL -> R.string.place_type_school
        PlaceType.WORK -> R.string.place_type_work
        PlaceType.CUSTOM -> R.string.place_type_custom
    },
)
