package com.crylo.splitbill.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crylo.splitbill.R
import com.crylo.splitbill.core.Backup
import com.crylo.splitbill.core.BackupException
import com.crylo.splitbill.core.Money
import com.crylo.splitbill.data.GroupRow
import com.crylo.splitbill.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

class GroupListViewModel(private val repo: Repository) : ViewModel() {
    val groups = repo.groupRows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed interface Message {
        data class Imported(val name: String, val created: Boolean) : Message
        data class Failed(val reason: Int) : Message
    }

    private val _messages = Channel<Message>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun import(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        // A real group file is kilobytes; refuse anything absurd rather than
                        // reading it all into memory.
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            if (out.size() > MAX_FILE_BYTES) throw IOException("too large")
                        }
                        out.toByteArray().decodeToString()
                    } ?: throw IOException("unreadable")
                }
                val snapshot = withContext(Dispatchers.Default) { Backup.decode(text) }
                val result = repo.import(snapshot)
                Message.Imported(snapshot.group.name, result == Repository.ImportResult.CREATED)
            } catch (e: BackupException) {
                Message.Failed(
                    when (e.reason) {
                        BackupException.Reason.NOT_A_BACKUP -> R.string.import_not_backup
                        BackupException.Reason.NEWER_VERSION -> R.string.import_newer
                        BackupException.Reason.CORRUPT -> R.string.import_corrupt
                    },
                )
            } catch (_: IOException) {
                Message.Failed(R.string.import_unreadable)
            } catch (_: SecurityException) {
                Message.Failed(R.string.import_unreadable)
            }
            _messages.send(message)
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    pendingImport: Uri?,
    onImportConsumed: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onNewGroup: () -> Unit,
) {
    val repo = repository()
    val vm: GroupListViewModel = viewModel { GroupListViewModel(repo) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(context.contentResolver, uri)
    }

    LaunchedEffect(pendingImport) {
        if (pendingImport != null) {
            vm.import(context.contentResolver, pendingImport)
            onImportConsumed()
        }
    }
    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            val text = when (m) {
                is GroupListViewModel.Message.Imported -> resources.getString(
                    if (m.created) R.string.import_created else R.string.import_updated,
                    m.name,
                )
                is GroupListViewModel.Message.Failed -> resources.getString(m.reason)
            }
            snackbar.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_group)) },
                            onClick = {
                                menuOpen = false
                                // Files arriving via chat apps often carry a generic MIME type.
                                picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewGroup,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_group)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = groups
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyGroups(Modifier.padding(padding))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.id }) { row -> GroupCard(row) { onOpenGroup(row.id) } }
            }
        }
    }
}

@Composable
private fun GroupCard(row: GroupRow, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.group_card_detail,
                    pluralStringResource(R.plurals.member_count, row.memberCount, row.memberCount),
                    Money.format(row.total, row.currency),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyGroups(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.empty_groups_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.empty_groups_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
