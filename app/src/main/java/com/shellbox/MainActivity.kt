package com.shellbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.service.SshKeepAliveService
import com.shellbox.ssh.SshManager
import com.shellbox.ui.addserver.AddServerScreen
import com.shellbox.ui.home.HomeScreen
import com.shellbox.ui.settings.KeySettingsScreen
import com.shellbox.ui.settings.KnownHostsScreen
import com.shellbox.ui.settings.SettingsScreen
import com.shellbox.ui.sftp.SftpScreen
import com.shellbox.ui.terminal.ConnectionSource
import com.shellbox.ui.terminal.TerminalScreen
import com.shellbox.ui.terminal.TerminalSettingsStore
import com.shellbox.ui.terminal.TerminalViewModel
import com.shellbox.ui.theme.ShellBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sshManager: SshManager

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way — service still runs, just without a guaranteed-visible notification pre-33 behavior differs */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShellBoxTheme {
                com.shellbox.ui.util.WithWindowSizeClass {
                    ShellBoxNavGraph(
                        sshManager = sshManager,
                        onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun ShellBoxNavGraph(
    sshManager: SshManager,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settingsStore = androidx.compose.runtime.remember { TerminalSettingsStore.getInstance(context) }
    val keepAliveEnabled by settingsStore.keepAliveServiceEnabled.collectAsState()
    val sessions by sshManager.sessions.collectAsState()

    // Starts/stops the opt-in foreground keep-alive service based on the user's
    // preference and whether there's actually anything to keep alive. Runs at
    // the nav-graph level (not tied to any single screen) so backgrounding the
    // app from any screen still keeps connections alive when the user asked for it.
    DisposableEffect(keepAliveEnabled, sessions.isEmpty()) {
        if (keepAliveEnabled && sessions.isNotEmpty()) {
            onRequestNotificationPermission()
            SshKeepAliveService.start(context)
        } else if (!keepAliveEnabled) {
            SshKeepAliveService.stop(context)
        }
        onDispose { }
    }

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
                onOpenSettings = { navController.navigate("settings") },
                onOpenFiles = { server ->
                    navController.currentBackStackEntry?.savedStateHandle?.set("sftpServer", server)
                    navController.navigate("sftp")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenKeySettings = { navController.navigate("key_settings") },
                onOpenKnownHosts = { navController.navigate("known_hosts") }
            )
        }

        composable("key_settings") {
            KeySettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("known_hosts") {
            KnownHostsScreen(onBack = { navController.popBackStack() })
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

            LaunchedEffect(Unit) {
                when {
                    quickConnect != null -> terminalVm.connectQuick(quickConnect)
                    server != null -> terminalVm.connectServer(server)
                }
            }

            TerminalScreen(
                onBack = { navController.popBackStack() },
                onOpenSftp = { source ->
                    when (source) {
                        is ConnectionSource.FromServer ->
                            navController.currentBackStackEntry?.savedStateHandle?.set("sftpServer", source.server)
                        is ConnectionSource.FromQuickConnect ->
                            navController.currentBackStackEntry?.savedStateHandle?.set("sftpQuickConnect", source.quickConnect)
                    }
                    navController.navigate("sftp")
                },
                viewModel = terminalVm
            )
        }

        composable("sftp") {
            // Consume whichever connection info was passed — HomeScreen's "文件" button
            // sends a saved Server, while TerminalScreen's SFTP icon may send either
            // a saved Server or ad-hoc QuickConnect credentials.
            val prevEntry = navController.previousBackStackEntry
            val server = prevEntry?.savedStateHandle?.remove<Server>("sftpServer")
            val quickConnect = prevEntry?.savedStateHandle?.remove<QuickConnect>("sftpQuickConnect")

            SftpScreen(
                server = server,
                quickConnect = quickConnect,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
