package com.example.resouretree.ui.navigation

import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.resouretree.ResourceTreeApplication
import com.example.resouretree.domain.model.NodeType
import com.example.resouretree.ui.screens.*
import com.example.resouretree.ui.viewmodel.*

@Composable
fun ResourceTreeApp(app: ResourceTreeApplication) {
    val nav = rememberNavController()
    val browser: BrowserViewModel = viewModel(factory = viewModelFactory { initializer {
        BrowserViewModel(app.repository, app.documents, app.executor, createSavedStateHandle())
    } })
    val pending by app.clipboardDrafts.pending.collectAsStateWithLifecycle()
    val rules by app.clipboardRules.rules.collectAsStateWithLifecycle()
    val current by nav.currentBackStackEntryAsState()
    LaunchedEffect(pending?.token, current?.destination?.route, rules) {
        val offered = pending ?: return@LaunchedEffect
        if (current?.destination?.route != "browser") return@LaunchedEffect
        val draft = com.example.resouretree.domain.model.ClipboardDraftParser.parse(offered.text, offered.token, rules)
            ?: run { app.clipboardDrafts.clearPending(); return@LaunchedEffect }
        val target = try { app.appCatalog.resolveTarget(draft.targets) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { draft.targets.first() }
        withContext(Dispatchers.Main.immediate) {
            if (nav.currentBackStackEntry?.destination?.route != "browser" || app.clipboardDrafts.pending.value?.token != draft.token) return@withContext
            // Keep clipboard text out of navigation URLs; the existing editor owns the editable form.
            browser.open(null)
            nav.navigate("editor?type=ITEM&parent=")
            nav.currentBackStackEntry!!.savedStateHandle.apply {
                set("clipboardName", draft.name); set("clipboardText", draft.text); set("clipboardTarget", target)
            }
            app.clipboardDrafts.consumed(draft.token)
        }
    }
    NavHost(navController = nav, startDestination = "browser") {
        composable("browser") {
            BrowserScreen(browser,
                onCreate = { type, parent -> nav.navigate("editor?type=${type.name}&parent=${Uri.encode(parent.orEmpty())}") },
                onEdit = { node -> nav.navigate("editor?id=${Uri.encode(node.id)}&type=${node.type.name}") },
                onClipboardRules = { nav.navigate("clipboard-rules") })
        }
        composable("clipboard-rules") {
            ClipboardRulesScreen(app.clipboardRules, onBack = { nav.popBackStack() })
        }
        composable("editor?id={id}&type={type}&parent={parent}", arguments = listOf(
            navArgument("id") { type = NavType.StringType; defaultValue = "" },
            navArgument("type") { type = NavType.StringType; defaultValue = "ITEM" },
            navArgument("parent") { type = NavType.StringType; defaultValue = "" }
        )) { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it.isNotEmpty() }
            val parent = entry.arguments?.getString("parent")?.takeIf { it.isNotEmpty() }
            val type = NodeType.valueOf(entry.arguments?.getString("type") ?: "ITEM")
            val editor: EditorViewModel = viewModel(factory = viewModelFactory { initializer {
                val handle = createSavedStateHandle()
                val clipboardText = entry.savedStateHandle.get<String>("clipboardText")
                if (id == null && clipboardText != null && !handle.contains("contentType")) {
                    handle["name"] = entry.savedStateHandle.get<String>("clipboardName").orEmpty()
                    handle["contentType"] = "TEXT"; handle["contentText"] = clipboardText
                    handle["actionType"] = "COPY_AND_LAUNCH"
                    handle["target"] = entry.savedStateHandle.get<String>("clipboardTarget").orEmpty()
                }
                EditorViewModel(app.repository, handle, id, parent, type, app.appCatalog, app.documents)
            } })
            EditorScreen(editor, editing = id != null, fromClipboard = entry.savedStateHandle.contains("clipboardText"), onBack = { nav.popBackStack() })
        }
    }
}
