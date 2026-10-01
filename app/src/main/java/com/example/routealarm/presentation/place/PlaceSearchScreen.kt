package com.example.routealarm.presentation.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.ErrorState
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.PlaceSearchBar
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.presentation.common.messageRes

@Composable
fun PlaceSearchRoute(
    onPlaceSelected: (Place) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaceSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.savedPlaces.collectAsStateWithLifecycle()
    PlaceSearchScreen(
        state = state,
        savedPlaces = saved,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::searchNow,
        onSelect = onPlaceSelected,
        onSave = viewModel::startSaving,
        onDeleteSaved = viewModel::deleteSaved,
        onCancelSave = viewModel::cancelSaving,
        onConfirmSave = viewModel::confirmSave,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchScreen(
    state: PlaceSearchUiState,
    savedPlaces: List<Place>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (Place) -> Unit,
    onSave: (Place) -> Unit,
    onDeleteSaved: (Long) -> Unit,
    onCancelSave: () -> Unit,
    onConfirmSave: (String, PlaceType) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_search_place)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md)) {
            PlaceSearchBar(query = state.query, onQueryChange = onQueryChange, onSearch = onSearch)
            Spacer(Modifier.height(Spacing.md))
            when (val results = state.results) {
                SearchResultState.Idle -> SavedPlacesList(savedPlaces, onSelect, onDeleteSaved)
                SearchResultState.Loading -> LoadingState()
                is SearchResultState.Error -> ErrorState(
                    message = stringResource(results.error.messageRes()),
                    onRetry = onSearch,
                    retryText = stringResource(R.string.common_retry),
                )
                is SearchResultState.Results -> if (results.places.isEmpty()) {
                    EmptyState(title = stringResource(R.string.place_search_empty), body = "", icon = Icons.Outlined.Place)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                        items(results.places) { place ->
                            val alreadySaved = savedPlaces.any { it.isSameLocationAs(place) }
                            PlaceRow(
                                place = place,
                                onClick = { onSelect(place) },
                                trailing = {
                                    if (alreadySaved) {
                                        Text(stringResource(R.string.place_search_saved), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        IconButton(onClick = { onSave(place) }) {
                                            Icon(Icons.Outlined.BookmarkBorder, contentDescription = stringResource(R.string.place_search_save))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    state.saving?.let { place ->
        SavePlaceDialog(place = place, onDismiss = onCancelSave, onConfirm = onConfirmSave)
    }
}

@Composable
private fun SavedPlacesList(places: List<Place>, onSelect: (Place) -> Unit, onDelete: (Long) -> Unit) {
    Text(stringResource(R.string.create_saved_places), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.sm))
    if (places.isEmpty()) {
        Text(stringResource(R.string.create_no_saved_places), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(bottom = Spacing.xl)) {
        items(places, key = { it.id }) { place ->
            PlaceRow(
                place = place,
                onClick = { onSelect(place) },
                trailing = {
                    IconButton(onClick = { onDelete(place.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.common_delete))
                    }
                },
            )
        }
    }
}

@Composable
fun PlaceRow(place: Place, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    AppCard(onClick = onClick) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(placeIcon(place.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleSmall)
                if (place.address.isNotBlank()) {
                    Text(place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun placeIcon(type: PlaceType) = when (type) {
    PlaceType.HOME -> Icons.Outlined.Home
    PlaceType.SCHOOL -> Icons.Outlined.School
    PlaceType.WORK -> Icons.Outlined.Work
    PlaceType.CUSTOM -> Icons.Outlined.Place
}

@Composable
fun placeTypeName(type: PlaceType): String = stringResource(
    when (type) {
        PlaceType.HOME -> R.string.place_type_home
        PlaceType.SCHOOL -> R.string.place_type_school
        PlaceType.WORK -> R.string.place_type_work
        PlaceType.CUSTOM -> R.string.place_type_custom
    },
)

@Composable
private fun SavePlaceDialog(place: Place, onDismiss: () -> Unit, onConfirm: (String, PlaceType) -> Unit) {
    var name by remember { mutableStateOf(place.name) }
    var type by remember { mutableStateOf(PlaceType.CUSTOM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.place_save_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.place_save_type_label), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PlaceType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(placeTypeName(t)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, type) }) { Text(stringResource(R.string.common_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
