package com.crylo.splitbill.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import com.crylo.splitbill.R
import com.crylo.splitbill.SplitBillApp
import com.crylo.splitbill.core.Category
import com.crylo.splitbill.data.Repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The app-wide repository. Read in composition and captured by ViewModel initializers,
 * because the per-screen ViewModelStores carry no Application in their CreationExtras.
 */
@Composable
fun repository(): Repository = (LocalContext.current.applicationContext as SplitBillApp).repository

@StringRes
fun Category.label(): Int = when (this) {
    Category.GENERAL -> R.string.category_general
    Category.FOOD -> R.string.category_food
    Category.GROCERIES -> R.string.category_groceries
    Category.TRAVEL -> R.string.category_travel
    Category.STAY -> R.string.category_stay
    Category.FUEL -> R.string.category_fuel
    Category.SHOPPING -> R.string.category_shopping
    Category.ENTERTAINMENT -> R.string.category_entertainment
    Category.RENT -> R.string.category_rent
    Category.UTILITIES -> R.string.category_utilities
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFormat)

fun today(): Long = LocalDate.now().toEpochDay()

/** A read-only field that opens a menu of [options]; the M3 exposed-dropdown, minus its churny API. */
@Composable
fun <T> Picker(
    label: String,
    selected: T?,
    options: List<T>,
    text: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selected?.let { text(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A transparent layer over the field catches the tap (a read-only text field
        // would otherwise swallow it to place the cursor).
        Box(
            Modifier
                .matchParentSize()
                .then(Modifier.clickableNoRipple { open = true }),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = {
                    open = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 16 && new.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(new) },
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Shows [epochDay] as a field; tapping opens the Material date picker. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DateField(epochDay: Long, onChange: (Long) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = formatDate(epochDay),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickableNoRipple { open = true })
    }
    if (open) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = epochDay * DAY_MILLIS)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it / DAY_MILLIS) }
                    open = false
                }) { Text(androidx.compose.ui.res.stringResource(R.string.ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { open = false }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.cancel))
                }
            },
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
}

// The date picker works in UTC midnight millis, which maps exactly onto epoch days.
private const val DAY_MILLIS = 86_400_000L
