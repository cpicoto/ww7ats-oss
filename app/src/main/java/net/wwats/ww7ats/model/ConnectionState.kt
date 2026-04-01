package net.wwats.ww7ats.model

enum class ConnectionState {
    IDLE,
    CONNECTING,
    STREAMING;
}

data class ConnectionStatus(
    val state: ConnectionState = ConnectionState.IDLE,
    val errorMessage: String? = null
) {
    val isStreaming: Boolean get() = state == ConnectionState.STREAMING
    val isFailed: Boolean get() = errorMessage != null && state == ConnectionState.IDLE
}
