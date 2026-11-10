package com.crylo.splitbill.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crylo.splitbill.R
import com.crylo.splitbill.core.Group
import com.crylo.splitbill.core.Member
import com.crylo.splitbill.core.UpiLink
import com.crylo.splitbill.data.Repository
import kotlinx.coroutines.launch
import java.util.UUID

class GroupEditViewModel(private val repo: Repository, private val groupId: String?) : ViewModel() {
    data class MemberDraft(val key: String, val name: String = "", val upi: String = "", val original: Member? = null)

    var loaded by mutableStateOf(groupId == null)
        private set
    var name by mutableStateOf("")
    var currency by mutableStateOf("₹")
    val members = mutableStateListOf<MemberDraft>()
    var showErrors by mutableStateOf(false)
        private set

    /** Members who appear in an expense or settlement; deleting them would break balances. */
    var referenced: Set<String> = emptySet()
        private set
    private var original: Group? = null
    private var originalIds: List<String> = emptyList()

    init {
        if (groupId == null) {
            repeat(2) { members += MemberDraft(UUID.randomUUID().toString()) }
        } else {
            viewModelScope.launch {
                val s = repo.snapshotOnce(groupId) ?: return@launch
                original = s.group
                name = s.group.name
                currency = s.group.currency
                members += s.members.map { MemberDraft(it.id, it.name, it.upiId.orEmpty(), it) }
                originalIds = s.members.map { it.id }
                referenced = buildSet {
                    s.expenses.forEach { e -> e.payers.forEach { add(it.memberId) }; e.splits.forEach { add(it.memberId) } }
                    s.settlements.forEach { add(it.fromId); add(it.toId) }
                }
                loaded = true
            }
        }
    }

    fun addMember() {
        members += MemberDraft(UUID.randomUUID().toString())
    }

    fun update(index: Int, draft: MemberDraft) {
        members[index] = draft
    }

    fun remove(index: Int) {
        members.removeAt(index)
    }

    private val named get() = members.filter { it.name.isNotBlank() }

    val nameError get() = showErrors && name.isBlank()
    val currencyError get() = showErrors && currency.isBlank()
    val tooFewMembers get() = showErrors && named.size < 2

    fun memberNameError(draft: MemberDraft): Int? {
        if (!showErrors) return null
        if (draft.name.isBlank()) return if (draft.original?.id in referenced) R.string.error_required else null
        val duplicates = named.count { it.name.trim().equals(draft.name.trim(), ignoreCase = true) }
        return if (duplicates > 1) R.string.error_duplicate_member else null
    }

    fun upiError(draft: MemberDraft): Boolean =
        draft.upi.isNotBlank() && !UpiLink.isValidVpa(draft.upi) && (showErrors || draft.upi.contains('@'))

    private fun valid(): Boolean {
        val names = named.map { it.name.trim().lowercase() }
        return name.isNotBlank() && currency.isNotBlank() && names.size >= 2 && names.toSet().size == names.size &&
            named.all { it.upi.isBlank() || UpiLink.isValidVpa(it.upi) } &&
            members.none { it.name.isBlank() && it.original?.id in referenced }
    }

    fun save(onSaved: (String) -> Unit) {
        showErrors = true
        if (!valid()) return
        val now = repo.now()
        val group = original?.let {
            if (it.name != name.trim() || it.currency != currency.trim()) {
                it.copy(name = name.trim(), currency = currency.trim(), updatedAt = now)
            } else {
                it
            }
        } ?: Group(UUID.randomUUID().toString(), name.trim(), currency.trim(), now, now)
        val drafts = named
        val saved = drafts.mapIndexed { i, d ->
            val upi = d.upi.trim().ifEmpty { null }
            val o = d.original
            when {
                o == null -> Member(d.key, group.id, d.name.trim(), upi, createdAt = now + i, updatedAt = now)
                o.name != d.name.trim() || o.upiId != upi -> o.copy(name = d.name.trim(), upiId = upi, updatedAt = now)
                else -> o
            }
        }
        val keptIds = drafts.map { it.key }.toSet()
        val removed = originalIds.filter { it !in keptIds }
        viewModelScope.launch {
            repo.saveGroup(group, saved, removed)
            onSaved(group.id)
        }
    }
}

private val currencies = listOf("₹", "$", "€", "£", "¥")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditScreen(groupId: String?, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val repo = repository()
    val vm: GroupEditViewModel = viewModel { GroupEditViewModel(repo, groupId) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (groupId == null) R.string.new_group else R.string.edit_group)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                },
                actions = { TextButton(onClick = { vm.save(onSaved) }, enabled = vm.loaded) { Text(stringResource(R.string.save)) } },
            )
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it.take(60) },
                label = { Text(stringResource(R.string.group_name)) },
                placeholder = { Text(stringResource(R.string.group_name_hint)) },
                isError = vm.nameError,
                supportingText = if (vm.nameError) ({ Text(stringResource(R.string.error_required)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.currency,
                    onValueChange = { vm.currency = it.take(4) },
                    label = { Text(stringResource(R.string.currency)) },
                    isError = vm.currencyError,
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                )
                currencies.forEach { symbol ->
                    FilterChip(selected = vm.currency == symbol, onClick = { vm.currency = symbol }, label = { Text(symbol) })
                }
            }
            Text(
                stringResource(R.string.currency_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.members), style = MaterialTheme.typography.titleMedium)
            if (vm.tooFewMembers) {
                Text(stringResource(R.string.error_two_members), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            vm.members.forEachIndexed { index, draft ->
                MemberRow(
                    draft = draft,
                    nameError = vm.memberNameError(draft),
                    upiError = vm.upiError(draft),
                    canDelete = draft.original?.id !in vm.referenced,
                    onChange = { vm.update(index, it) },
                    onDelete = { vm.remove(index) },
                )
            }
            TextButton(onClick = vm::addMember) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_member))
            }
        }
    }
}

@Composable
private fun MemberRow(
    draft: GroupEditViewModel.MemberDraft,
    nameError: Int?,
    upiError: Boolean,
    canDelete: Boolean,
    onChange: (GroupEditViewModel.MemberDraft) -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it.take(40))) },
                    label = { Text(stringResource(R.string.member_name)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(stringResource(it)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.upi,
                    onValueChange = { onChange(draft.copy(upi = it.trim().take(100))) },
                    label = { Text(stringResource(R.string.member_upi)) },
                    placeholder = { Text(stringResource(R.string.member_upi_hint)) },
                    isError = upiError,
                    supportingText = if (upiError) ({ Text(stringResource(R.string.error_upi)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!canDelete) {
                    Text(
                        stringResource(R.string.member_has_activity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_member))
            }
        }
    }
}
