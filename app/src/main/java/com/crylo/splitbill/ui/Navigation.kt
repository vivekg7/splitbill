package com.crylo.splitbill.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID

/**
 * The app has four screens and no deep links, so a hand-rolled back stack is less code
 * and less dependency surface than a navigation library. Each entry gets its own
 * ViewModelStore, cleared when the entry is popped, so screen state survives rotation
 * but an editor reopened later starts fresh.
 */
sealed interface Route {
    data object Groups : Route
    data class EditGroup(val groupId: String?) : Route
    data class GroupDetail(val groupId: String) : Route
    data class EditExpense(val groupId: String, val expenseId: String?) : Route

    fun encode(): String = when (this) {
        Groups -> "groups"
        is EditGroup -> "editGroup/${groupId.orEmpty()}"
        is GroupDetail -> "group/$groupId"
        is EditExpense -> "expense/$groupId/${expenseId.orEmpty()}"
    }

    companion object {
        fun decode(text: String): Route {
            val p = text.split('/')
            return when (p[0]) {
                "editGroup" -> EditGroup(p[1].ifEmpty { null })
                "group" -> GroupDetail(p[1])
                "expense" -> EditExpense(p[1], p[2].ifEmpty { null })
                else -> Groups
            }
        }
    }
}

data class Entry(val key: String, val route: Route)

class Navigator(val stack: SnapshotStateList<Entry>, private val stores: EntryStores) {
    val top: Entry get() = stack.last()

    fun push(route: Route) {
        stack += Entry(UUID.randomUUID().toString(), route)
    }

    fun pop() {
        if (stack.size > 1) stores.clear(stack.removeAt(stack.lastIndex).key)
    }

    /** Pops back to the first entry matching [predicate] (kept), or to the root. */
    fun popTo(predicate: (Route) -> Boolean) {
        while (stack.size > 1 && !predicate(top.route)) pop()
    }
}

/** Activity-scoped holder of the per-entry ViewModelStores. */
class EntryStores : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun owner(key: String): ViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = stores.getOrPut(key) { ViewModelStore() }
    }

    fun clear(key: String) {
        stores.remove(key)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

@Composable
fun rememberNavigator(): Navigator {
    val stores: EntryStores = viewModel()
    val stack = rememberSaveable(
        saver = listSaver<SnapshotStateList<Entry>, String>(
            save = { list -> list.map { "${it.key}|${it.route.encode()}" } },
            restore = { saved ->
                mutableStateListOf<Entry>().apply {
                    saved.forEach { addAll(listOf(Entry(it.substringBefore('|'), Route.decode(it.substringAfter('|'))))) }
                }
            },
        ),
    ) { mutableStateListOf(Entry(UUID.randomUUID().toString(), Route.Groups)) }
    return remember(stack, stores) { Navigator(stack, stores) }
}

@Composable
fun NavHost(navigator: Navigator, content: @Composable (Route) -> Unit) {
    BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }
    val entry = navigator.top
    val stores: EntryStores = viewModel()
    val owner = remember(entry.key) { stores.owner(entry.key) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        content(entry.route)
    }
}
