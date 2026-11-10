package com.crylo.splitbill.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crylo.splitbill.R
import com.crylo.splitbill.core.Category
import com.crylo.splitbill.core.Expense
import com.crylo.splitbill.core.Member
import com.crylo.splitbill.core.Money
import com.crylo.splitbill.core.Portion
import com.crylo.splitbill.core.SplitMode
import com.crylo.splitbill.core.SplitOutcome
import com.crylo.splitbill.core.SplitProblem
import com.crylo.splitbill.core.Splitter
import com.crylo.splitbill.data.Repository
import kotlinx.coroutines.launch
import java.util.UUID

class ExpenseEditViewModel(private val repo: Repository, private val groupId: String, private val expenseId: String?) : ViewModel() {
    var loaded by mutableStateOf(false)
        private set
    var members by mutableStateOf(emptyList<Member>())
        private set
    var currency by mutableStateOf("₹")
        private set
    private var original: Expense? = null

    var description by mutableStateOf("")
    var amountText by mutableStateOf("")
    var date by mutableLongStateOf(today())
    var category by mutableStateOf(Category.GENERAL)
    var multiPayer by mutableStateOf(false)
    var payerId by mutableStateOf<String?>(null)
    val payerAmounts = mutableStateMapOf<String, String>()
    var mode by mutableStateOf(SplitMode.EQUAL)
    val included = mutableStateMapOf<String, Boolean>()
    val exact = mutableStateMapOf<String, String>()
    val shares = mutableStateMapOf<String, String>()
    val percent = mutableStateMapOf<String, String>()
    var showErrors by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val s = repo.snapshotOnce(groupId) ?: return@launch
            members = s.members
            currency = s.group.currency
            s.members.forEach {
                included[it.id] = true
                shares[it.id] = "1"
            }
            payerId = s.members.firstOrNull()?.id
            s.expenses.firstOrNull { it.id == expenseId }?.let(::fill)
            loaded = true
        }
    }

    private fun fill(e: Expense) {
        original = e
        description = e.description
        amountText = Money.plain(e.amount)
        date = e.date
        category = e.category
        if (e.payers.size == 1) {
            payerId = e.payers[0].memberId
        } else {
            multiPayer = true
            e.payers.forEach { payerAmounts[it.memberId] = Money.plain(it.amount) }
        }
        mode = e.splitMode
        val inputs = e.splits.associate { it.memberId to it.input }
        when (e.splitMode) {
            SplitMode.EQUAL -> members.forEach { included[it.id] = (inputs[it.id] ?: 0) > 0 }
            SplitMode.EXACT -> inputs.forEach { (id, v) -> if (v > 0) exact[id] = Money.plain(v) }
            SplitMode.SHARES -> members.forEach { shares[it.id] = inputs[it.id]?.takeIf { v -> v > 0 }?.let(Money::compact).orEmpty() }
            SplitMode.PERCENT -> inputs.forEach { (id, v) -> if (v > 0) percent[id] = Money.compact(v) }
        }
    }

    val isNew get() = expenseId == null
    val amount: Long? get() = Money.parse(amountText)?.takeIf { it > 0 }

    /** Unparseable text in a split field; shown as a field error and counted as zero. */
    fun fieldInvalid(text: String?, max: Long): Boolean = !text.isNullOrBlank() && Money.parse(text, max) == null

    private fun parsed(text: String?, max: Long): Long = text?.let { Money.parse(it, max) } ?: 0L

    val payers: List<Portion>
        get() = if (multiPayer) {
            members.mapNotNull { m -> parsed(payerAmounts[m.id], Money.MAX_AMOUNT).takeIf { it > 0 }?.let { Portion(m.id, it) } }
        } else {
            listOfNotNull(payerId?.let { Portion(it, amount ?: 0L) })
        }

    val unpaid: Long get() = Splitter.unpaid(amount ?: 0L, payers)

    val outcome: SplitOutcome
        get() = Splitter.split(
            amount ?: 0L,
            mode,
            members.map { m ->
                m.id to when (mode) {
                    SplitMode.EQUAL -> if (included[m.id] == true) 1L else 0L
                    SplitMode.EXACT -> parsed(exact[m.id], Money.MAX_AMOUNT)
                    SplitMode.SHARES -> parsed(shares[m.id], Money.MAX_WEIGHT)
                    SplitMode.PERCENT -> parsed(percent[m.id], Splitter.FULL_PERCENT)
                }
            },
        )

    val descriptionError get() = showErrors && description.isBlank()
    val amountError get() = showErrors && amount == null

    fun setAllIncluded(value: Boolean) = members.forEach { included[it.id] = value }

    fun save(onDone: () -> Unit) {
        showErrors = true
        val total = amount ?: return
        val split = outcome as? SplitOutcome.Valid ?: return
        if (description.isBlank() || unpaid != 0L || payers.isEmpty()) return
        val now = repo.now()
        val entries = split.entries.filter { it.input > 0 || it.share > 0 }
        val e = original?.copy(
            description = description.trim(), amount = total, date = date, category = category,
            payers = payers, splitMode = mode, splits = entries, updatedAt = now,
        ) ?: Expense(
            UUID.randomUUID().toString(), groupId, description.trim(), total, date, category,
            payers, mode, entries, createdAt = now, updatedAt = now,
        )
        viewModelScope.launch {
            repo.saveExpense(e)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val e = original ?: return
        viewModelScope.launch {
            repo.deleteExpense(e)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(groupId: String, expenseId: String?, onClose: () -> Unit) {
    val repo = repository()
    val vm: ExpenseEditViewModel = viewModel { ExpenseEditViewModel(repo, groupId, expenseId) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isNew) R.string.add_expense else R.string.edit_expense)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                },
                actions = {
                    if (!vm.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    TextButton(onClick = { vm.save(onClose) }, enabled = vm.loaded) { Text(stringResource(R.string.save)) }
                },
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
                value = vm.description,
                onValueChange = { vm.description = it.take(80) },
                label = { Text(stringResource(R.string.description)) },
                placeholder = { Text(stringResource(R.string.description_hint)) },
                isError = vm.descriptionError,
                supportingText = if (vm.descriptionError) ({ Text(stringResource(R.string.error_required)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyField(
                value = vm.amountText,
                onValueChange = { vm.amountText = it },
                label = stringResource(R.string.amount),
                prefix = vm.currency,
                isError = vm.amountError || (vm.amountText.isNotBlank() && Money.parse(vm.amountText) == null),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(vm.date, { vm.date = it }, stringResource(R.string.date), Modifier.weight(1f))
                Picker(
                    label = stringResource(R.string.category),
                    selected = vm.category,
                    options = Category.entries,
                    text = { stringResource(it.label()) },
                    onSelect = { vm.category = it },
                    modifier = Modifier.weight(1f),
                )
            }
            PaidBySection(vm)
            SplitSection(vm)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_expense_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onClose)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Sentinel for the "several people paid" option in the payer picker. */
private const val MULTIPLE = "\u0000multiple"

@Composable
private fun PaidBySection(vm: ExpenseEditViewModel) {
    val multipleLabel = stringResource(R.string.paid_by_multiple)
    Picker(
        label = stringResource(R.string.paid_by),
        selected = if (vm.multiPayer) MULTIPLE else vm.payerId,
        options = vm.members.map { it.id } + MULTIPLE,
        text = { id -> if (id == MULTIPLE) multipleLabel else vm.members.first { it.id == id }.name },
        onSelect = { id ->
            if (id == MULTIPLE) {
                vm.multiPayer = true
            } else {
                vm.multiPayer = false
                vm.payerId = id
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (vm.multiPayer) {
        vm.members.forEach { m ->
            MemberAmountRow(m.name) {
                MoneyField(
                    value = vm.payerAmounts[m.id].orEmpty(),
                    onValueChange = { vm.payerAmounts[m.id] = it },
                    label = stringResource(R.string.paid),
                    prefix = vm.currency,
                    isError = vm.fieldInvalid(vm.payerAmounts[m.id], Money.MAX_AMOUNT),
                    modifier = Modifier.width(170.dp),
                )
            }
        }
        val unpaid = vm.unpaid
        Hint(
            text = when {
                vm.amount == null -> stringResource(R.string.hint_enter_amount)
                unpaid > 0 -> stringResource(R.string.hint_left_to_pay, Money.format(unpaid, vm.currency))
                unpaid < 0 -> stringResource(R.string.hint_over_paid, Money.format(-unpaid, vm.currency))
                else -> stringResource(R.string.hint_paid_ok)
            },
            error = unpaid != 0L && vm.amount != null,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitSection(vm: ExpenseEditViewModel) {
    Text(stringResource(R.string.split), style = MaterialTheme.typography.titleMedium)
    val labels = listOf(R.string.split_equal, R.string.split_exact, R.string.split_shares, R.string.split_percent)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SplitMode.entries.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = vm.mode == mode,
                onClick = { vm.mode = mode },
                shape = SegmentedButtonDefaults.itemShape(i, SplitMode.entries.size),
                label = { Text(stringResource(labels[i]), maxLines = 1) },
            )
        }
    }
    val outcome = vm.outcome
    val shareOf: Map<String, Long> = (outcome as? SplitOutcome.Valid)?.entries?.associate { it.memberId to it.share }.orEmpty()
    if (vm.mode == SplitMode.EQUAL) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.setAllIncluded(true) }) { Text(stringResource(R.string.select_all)) }
            TextButton(onClick = { vm.setAllIncluded(false) }) { Text(stringResource(R.string.select_none)) }
        }
    }
    vm.members.forEach { m ->
        val preview = shareOf[m.id]?.takeIf { it > 0 }?.let { Money.format(it, vm.currency) }
        when (vm.mode) {
            SplitMode.EQUAL -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = vm.included[m.id] == true, onCheckedChange = { vm.included[m.id] = it })
                Text(m.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(preview.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SplitMode.EXACT -> MemberAmountRow(m.name) {
                MoneyField(
                    value = vm.exact[m.id].orEmpty(),
                    onValueChange = { vm.exact[m.id] = it },
                    label = stringResource(R.string.amount),
                    prefix = vm.currency,
                    isError = vm.fieldInvalid(vm.exact[m.id], Money.MAX_AMOUNT),
                    modifier = Modifier.width(170.dp),
                )
            }
            SplitMode.SHARES -> MemberAmountRow(m.name, preview) {
                MoneyField(
                    value = vm.shares[m.id].orEmpty(),
                    onValueChange = { vm.shares[m.id] = it },
                    label = stringResource(R.string.shares),
                    isError = vm.fieldInvalid(vm.shares[m.id], Money.MAX_WEIGHT),
                    modifier = Modifier.width(110.dp),
                )
            }
            SplitMode.PERCENT -> MemberAmountRow(m.name, preview) {
                MoneyField(
                    value = vm.percent[m.id].orEmpty(),
                    onValueChange = { vm.percent[m.id] = it },
                    label = stringResource(R.string.percent),
                    suffix = "%",
                    isError = vm.fieldInvalid(vm.percent[m.id], Splitter.FULL_PERCENT),
                    modifier = Modifier.width(110.dp),
                )
            }
        }
    }
    val (text, error) = when (outcome) {
        is SplitOutcome.Valid -> when (vm.mode) {
            SplitMode.EQUAL -> {
                val count = outcome.entries.count { it.input > 0 }
                stringResource(R.string.hint_equal, count) to false
            }
            else -> stringResource(R.string.hint_split_ok) to false
        }
        is SplitOutcome.Invalid -> when (val p = outcome.problem) {
            SplitProblem.NoAmount -> stringResource(R.string.hint_enter_amount) to vm.showErrors
            SplitProblem.NoParticipants -> stringResource(R.string.hint_no_participants) to true
            is SplitProblem.AmountMismatch ->
                if (p.remaining > 0) {
                    stringResource(R.string.hint_left_to_split, Money.format(p.remaining, vm.currency)) to true
                } else {
                    stringResource(R.string.hint_over_split, Money.format(-p.remaining, vm.currency)) to true
                }
            is SplitProblem.PercentMismatch ->
                if (p.remaining > 0) {
                    stringResource(R.string.hint_percent_left, Money.compact(p.remaining)) to true
                } else {
                    stringResource(R.string.hint_percent_over, Money.compact(-p.remaining)) to true
                }
        }
    }
    Hint(text, error)
}

@Composable
private fun MemberAmountRow(name: String, preview: String? = null, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (preview != null) {
                Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        field()
    }
}

@Composable
private fun Hint(text: String, error: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}
