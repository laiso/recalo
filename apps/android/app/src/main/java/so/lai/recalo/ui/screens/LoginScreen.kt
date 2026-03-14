package so.lai.recalo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import so.lai.recalo.data.api.SessionManager

class LoginViewModel(
    private val sessionManager: SessionManager,
    initialOpenAIKey: String = ""
) : ViewModel() {
    var openAIKey by mutableStateOf(initialOpenAIKey)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun login(onSuccess: () -> Unit) {
        if (openAIKey.isBlank()) {
            errorMessage = "Please enter your OpenAI API Key"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                sessionManager.saveOpenAIKey(openAIKey)
                onSuccess()
            } catch (e: Exception) {
                errorMessage = e.message ?: "An error occurred"
            } finally {
                isLoading = false
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoginSuccess: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Recalo Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Enter your OpenAI API Key to start", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = viewModel.openAIKey,
            onValueChange = { viewModel.openAIKey = it },
            label = { Text("OpenAI API Key") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.errorMessage != null) {
            Text(text = viewModel.errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { viewModel.login(onLoginSuccess) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !viewModel.isLoading && viewModel.openAIKey.isNotBlank()
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Login")
            }
        }
    }
}
