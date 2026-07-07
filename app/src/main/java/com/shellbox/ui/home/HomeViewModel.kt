package com.shellbox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shellbox.data.model.QuickConnect
import com.shellbox.data.model.Server
import com.shellbox.data.model.toServer
import com.shellbox.data.repository.ServerRepository
import com.shellbox.ssh.SshManager
import com.shellbox.ssh.TestConnectionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val sshManager: SshManager
) : ViewModel() {

    val servers: StateFlow<List<Server>> = serverRepository.getAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteServer(server: Server) {
        viewModelScope.launch {
            serverRepository.deleteServer(server)
        }
    }

    fun markUsed(serverId: Long) {
        viewModelScope.launch {
            serverRepository.updateLastUsed(serverId)
        }
    }

    /** Tests connectivity for the given quick-connect info without opening a terminal. */
    fun testConnection(quickConnect: QuickConnect, onResult: (TestConnectionResult) -> Unit) {
        viewModelScope.launch {
            val result = sshManager.testConnection(quickConnect)
            onResult(result)
        }
    }

    /** Saves the quick-connect info as a named server. */
    fun saveServer(quickConnect: QuickConnect, name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            serverRepository.saveServer(quickConnect.toServer(name))
            onDone()
        }
    }
}
