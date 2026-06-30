package com.shellbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.ui.addserver.AddServerScreen
import com.shellbox.ui.home.HomeScreen
import com.shellbox.ui.settings.SettingsScreen
import com.shellbox.ui.terminal.TerminalScreen
import com.shellbox.ui.terminal.TerminalViewModel
import com.shellbox.ui.theme.ShellBoxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShellBoxTheme {
                ShellBoxNavGraph()
            }
        }
    }
}

@Composable
fun ShellBoxNavGraph() {
    val navController = rememberNavController()
    // Shared TerminalViewModel scoped to the nav back stack entry for "terminal"
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onConnect = { quickConnect ->
                    // Pass quick connect params via nav args (simplified: store in singleton/shared vm)
                    navController.currentBackStackEntry?.savedStateHandle?.set("quickConnect", quickConnect)
                    navController.navigate("terminal")
                },
                onConnectServer = { server ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("server", server)
                    navController.navigate("terminal")
                },
                onAddServer = { navController.navigate("add_server") },
                onEditServer = { server -> navController.navigate("add_server?id=${server.id}") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "add_server?id={id}",
            arguments = listOf(navArgument("id") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStack ->
            val id = backStack.arguments?.getLong("id")?.takeIf { it > 0 }
            AddServerScreen(
                editServerId = id,
                onBack = { navController.popBackStack() }
            )
        }

        composable("terminal") { backStack ->
            val terminalVm: TerminalViewModel = hiltViewModel()

            // Consume pending connection from previous screen
            val prevEntry = navController.previousBackStackEntry
            val quickConnect = prevEntry?.savedStateHandle?.remove<QuickConnect>("quickConnect")
            val server = prevEntry?.savedStateHandle?.remove<Server>("server")

            androidx.compose.runtime.LaunchedEffect(Unit) {
                when {
                    quickConnect != null -> terminalVm.connectQuick(quickConnect)
                    server != null -> terminalVm.connectServer(server)
                }
            }

            TerminalScreen(
                onBack = { navController.popBackStack() },
                viewModel = terminalVm
            )
        }
    }
}
