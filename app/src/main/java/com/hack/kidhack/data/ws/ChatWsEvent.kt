package com.hack.kidhack.data.ws

import com.hack.kidhack.domain.model.ChatMessage
import kotlinx.serialization.Serializable

sealed class ChatWsEvent {
    data class Send(val roomId: String, val content: String) : ChatWsEvent()
    data class Message(val message: ChatMessage) : ChatWsEvent()
    data class Read(val roomId: String, val messageId: String?) : ChatWsEvent()
    data class Error(val message: String) : ChatWsEvent()
}

@Serializable
data class ChatSocketFrame(
    val type: String,
    val roomId: String? = null,
    val content: String? = null,
    val text: String? = null,
    val id: String? = null,
    val messageId: String? = null,
    val fromRole: String? = null,
    val sentAt: String? = null,
    val message: String? = null,
) {
    companion object {
        const val SEND = "SEND"
        const val MESSAGE = "MESSAGE"
        const val READ = "READ"
        const val ERROR = "ERROR"
    }
}
