package com.crylo.splitbill.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.crylo.splitbill.R
import com.crylo.splitbill.core.Money
import com.crylo.splitbill.core.Transfer
import com.crylo.splitbill.core.UpiLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onEditGroup: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
) {
    val repo = repository()
    val vm: GroupViewModel = viewModel { GroupViewModel(repo, groupId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var shareMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state) { if (state is GroupState.Gone) onBack() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = vm.exportJson()
        if (uri != null && json != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) } }.isSuccess
                }
                Toast.makeText(context, if (ok) R.string.export_done else R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val ui = (state as? GroupState.Ready)?.ui
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui?.snapshot?.group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (ui != null) {
                        Box {
                            IconButton(onClick = { shareMenu = true }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_summary))
                            }
                            DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.share_as_text)) }, onClick = {
                                    shareMenu = false
                                    Sharing.shareText(context, ui.snapshot)
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.share_as_image)) }, onClick = {
                                    shareMenu = false
                                    Sharing.shareImage(context, ui.snapshot)
                                })
                            }
                        }
                        Box {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.edit_group)) }, onClick = {
                                    moreMenu = false
                                    onEditGroup()
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.send_group_file)) }, onClick = {
                                    moreMenu = false
                                    vm.exportJson()?.let { Sharing.shareGroupFile(context, vm.exportFileName(), it) }
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.export_group)) }, onClick = {
                                    moreMenu = false
                                    exporter.launch(vm.exportFileName())
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete_group)) }, onClick = {
                                    moreMenu = false
                                    confirmDelete = true
                                })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (ui != null) {
                if (tab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = onAddExpense,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.add_expense)) },
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = { vm.startSettlement() },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.record_payment)) },
                    )
                }
            }
        },
    ) { padding ->
        if (ui == null) return@Scaffold
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.tab_expenses)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.tab_balances)) })
            }
            val bottom = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp)
            if (tab == 0) {
                ActivityList(ui, bottom, onEditExpense, vm::editSettlement)
            } else {
                BalancesPane(
                    ui = ui,
                    bottom = bottom,
                    onRecord = { vm.startSettlement(it) },
                    onUpi = { t ->
                        val receiver = ui.members.getValue(t.to)
                        val link = UpiLink.build(
                            receiver.upiId!!,
                            receiver.name,
                            t.amount,
                            resources.getString(R.string.upi_note, ui.snapshot.group.name),
                        )
                        // Open the record dialog behind the UPI app, so coming back
                        // offers to log the payment that was just made.
                        if (Sharing.openUpi(context, link)) vm.startSettlement(t)
                    },
                )
            }
        }
    }

    vm.settlement?.let { draft ->
        SettlementDialog(
            draft = draft,
            ui = ui ?: return@let,
            onChange = vm::updateSettlement,
            onSave = vm::saveSettlement,
            onDelete = vm::deleteSettlement,
            onDismiss = vm::dismissSettlement,
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_group_title)) },
            text = { Text(stringResource(R.string.delete_group_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteGroup()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ActivityList(
    ui: GroupUi,
    bottom: PaddingValues,
    onEditExpense: (String) -> Unit,
    onEditSettlement: (com.crylo.splitbill.core.Settlement) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottom) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.total_spent), style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(ui.total, ui.currency), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.members_list, ui.snapshot.members.joinToString(", ") { it.name }),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (ui.activity.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.empty_expenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        items(ui.activity, key = { it.id }) { item ->
            when (item) {
                is ActivityItem.Spent -> {
                    val e = item.expense
                    val payer = ui.name(e.payers.maxBy { it.amount }.memberId)
                    ListItem(
                        headlineContent = { Text(e.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (e.payers.size > 1) R.string.paid_by_many else R.string.paid_by_one,
                                    payer,
                                ) + " · " + formatDate(e.date) + " · " + stringResource(e.category.label()),
                            )
                        },
                        trailingContent = { Text(Money.format(e.amount, ui.currency), style = MaterialTheme.typography.titleSmall) },
                        modifier = Modifier.clickable { onEditExpense(e.id) },
                    )
                }
                is ActivityItem.Paid -> {
                    val s = item.settlement
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settlement_line, ui.name(s.fromId), ui.name(s.toId))) },
                        supportingContent = {
                            Text(listOf(formatDate(s.date), s.note).filter { it.isNotBlank() }.joinToString(" · "))
                        },
                        trailingContent = {
                            Text(
                                Money.format(s.amount, ui.currency),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        },
                        modifier = Modifier.clickable { onEditSettlement(s) },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun BalancesPane(ui: GroupUi, bottom: PaddingValues, onRecord: (Transfer) -> Unit, onUpi: (Transfer) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottom) {
        item { SectionTitle(stringResource(R.string.balances)) }
        items(ui.balances.entries.toList(), key = { "b" + it.key }) { (id, value) ->
            ListItem(
                headlineContent = { Text(ui.name(id)) },
                trailingContent = {
                    Text(
                        when {
                            value > 0 -> stringResource(R.string.gets_back, Money.format(value, ui.currency))
                            value < 0 -> stringResource(R.string.owes, Money.format(-value, ui.currency))
                            else -> stringResource(R.string.settled_up)
                        },
                        color = when {
                            value > 0 -> MaterialTheme.colorScheme.primary
                            value < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (value != 0L) FontWeight.Medium else null,
                    )
                },
            )
        }
        item { SectionTitle(stringResource(R.string.settle_up)) }
        if (ui.plan.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.all_settled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            item {
                Text(
                    stringResource(R.string.settle_up_explainer, ui.plan.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(ui.plan, key = { "t" + it.from + it.to }) { t ->
            val canUpi = Money.isRupee(ui.currency) && !ui.members[t.to]?.upiId.isNullOrBlank()
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.transfer_line, ui.name(t.from), ui.name(t.to)), style = MaterialTheme.typography.bodyLarge)
                    Text(Money.format(t.amount, ui.currency), style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onRecord(t) }) { Text(stringResource(R.string.mark_paid)) }
                        if (canUpi) {
                            FilledTonalButton(onClick = { onUpi(t) }) { Text(stringResource(R.string.pay_with_upi)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettlementDialog(
    draft: SettlementDraft,
    ui: GroupUi,
    onChange: (SettlementDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val members = ui.snapshot.members
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.existing == null) R.string.record_payment else R.string.edit_payment)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(
                    label = stringResource(R.string.payment_from),
                    selected = members.firstOrNull { it.id == draft.fromId },
                    options = members,
                    text = { it.name },
                    onSelect = { onChange(draft.copy(fromId = it.id)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Picker(
                    label = stringResource(R.string.payment_to),
                    selected = members.firstOrNull { it.id == draft.toId },
                    options = members.filter { it.id != draft.fromId },
                    text = { it.name },
                    onSelect = { onChange(draft.copy(toId = it.id)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(
                    value = draft.amount,
                    onValueChange = { onChange(draft.copy(amount = it)) },
                    label = stringResource(R.string.amount),
                    prefix = ui.currency,
                    isError = draft.showErrors && draft.parsedAmount == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(draft.date, { onChange(draft.copy(date = it)) }, stringResource(R.string.date))
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { onChange(draft.copy(note = it.take(80))) },
                    label = { Text(stringResource(R.string.note_optional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draft.showErrors && !draft.valid) {
                    Text(stringResource(R.string.error_payment), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (draft.existing != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
