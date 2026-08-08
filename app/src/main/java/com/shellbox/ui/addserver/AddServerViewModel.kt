package com.shellbox.ui.addserver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shellbox.data.model.AuthType
import com.shellbox.data.model.Server
import com.shellbox.data.repository.ServerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val serverRepository: ServerRepository
) : ViewModel() {

    suspend fun getServer(id: Long): Server? = serverRepository.getServerById(id)

    fun saveServer(server: Server, onDone: () -> Unit) {
        viewModelScope.launch {
            serverRepository.saveServer(server)
            onDone()
        }
    }
}
