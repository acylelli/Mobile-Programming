package com.example.routealarm.presentation.alarmlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AlarmCard
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.presentation.common.messageRes

@Composable
fun AlarmListRoute(
    onOpenSchedule: (Long) -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(error) {
        val e = error ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(e.messageRes()))
        viewModel.consumeError()
    }
    AlarmListScreen(state = state, snackbarHostState = snackbar, onOpenSchedule = onOpenSchedule, onToggle = viewModel::toggle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    state: AlarmListUiState,
    snackbarHostState: SnackbarHostState,
    onOpenSchedule: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarms_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (state) {
            AlarmListUiState.Loading -> LoadingState(Modifier.padding(padding))
            AlarmListUiState.Empty -> EmptyState(
                title = stringResource(R.string.alarms_empty_title),
                body = stringResource(R.string.alarms_empty_body),
                modifier = Modifier.padding(padding),
            )
            is AlarmListUiState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm).let {
                    PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = 96.dp)
                },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(state.schedules, key = { it.id }) { schedule ->
                    AlarmCard(
                        schedule = schedule,
                        onClick = { onOpenSchedule(schedule.id) },
                        onToggle = { onToggle(schedule.id, it) },
                    )
                }
            }
        }
    }
}
