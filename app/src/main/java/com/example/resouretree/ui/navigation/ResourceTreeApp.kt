package com.example.resouretree.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
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
    NavHost(navController = nav, startDestination = "browser") {
        composable("browser") {
            BrowserScreen(browser,
                onCreate = { type, parent -> nav.navigate("editor?type=${type.name}&parent=${Uri.encode(parent.orEmpty())}") },
                onEdit = { node -> nav.navigate("editor?id=${Uri.encode(node.id)}&type=${node.type.name}") })
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
                EditorViewModel(app.repository, createSavedStateHandle(), id, parent, type)
            } })
            EditorScreen(editor, editing = id != null, onBack = { nav.popBackStack() })
        }
    }
}
