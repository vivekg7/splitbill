package com.crylo.splitbill

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crylo.splitbill.ui.ExpenseEditScreen
import com.crylo.splitbill.ui.GroupEditScreen
import com.crylo.splitbill.ui.GroupListScreen
import com.crylo.splitbill.ui.GroupScreen
import com.crylo.splitbill.ui.NavHost
import com.crylo.splitbill.ui.Route
import com.crylo.splitbill.ui.SplitBillTheme
import com.crylo.splitbill.ui.rememberNavigator

class MainActivity : ComponentActivity() {
    /** A group file opened from another app (file manager, WhatsApp "Open with"). */
    private var pendingImport by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) takeImport(intent)
        setContent {
            SplitBillTheme {
                val nav = rememberNavigator()
                LaunchedEffect(pendingImport) {
                    if (pendingImport != null) nav.popTo { it == Route.Groups }
                }
                NavHost(nav) { route ->
                    when (route) {
                        Route.Groups -> GroupListScreen(
                            pendingImport = pendingImport,
                            onImportConsumed = { pendingImport = null },
                            onOpenGroup = { nav.push(Route.GroupDetail(it)) },
                            onNewGroup = { nav.push(Route.EditGroup(null)) },
                        )
                        is Route.EditGroup -> GroupEditScreen(
                            groupId = route.groupId,
                            onClose = nav::pop,
                            onSaved = { id ->
                                nav.pop()
                                if (route.groupId == null) nav.push(Route.GroupDetail(id))
                            },
                        )
                        is Route.GroupDetail -> GroupScreen(
                            groupId = route.groupId,
                            onBack = { nav.popTo { it == Route.Groups } },
                            onEditGroup = { nav.push(Route.EditGroup(route.groupId)) },
                            onAddExpense = { nav.push(Route.EditExpense(route.groupId, null)) },
                            onEditExpense = { nav.push(Route.EditExpense(route.groupId, it)) },
                        )
                        is Route.EditExpense -> ExpenseEditScreen(route.groupId, route.expenseId, onClose = nav::pop)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeImport(intent)
    }

    private fun takeImport(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { pendingImport = it }
    }
}
