package com.comunidapp.app.domain.chat

import com.comunidapp.app.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageMergeTest {

    private fun msg(id: String, content: String = "hola", sender: String = "me") = ChatMessage(
        id = id,
        conversationId = "c1",
        senderId = sender,
        senderName = "Ana",
        content = content,
        createdAt = 1L
    )

    @Test
    fun confirmed_send_appears_for_sender() {
        val merged = ChatMessageMerge.appendConfirmed(emptyList(), msg("m1"))
        assertEquals(listOf("m1"), merged.map { it.id })
    }

    @Test
    fun later_realtime_does_not_duplicate() {
        val first = ChatMessageMerge.appendConfirmed(emptyList(), msg("m1"))
        val second = ChatMessageMerge.replaceFromServer(first + msg("m1"))
        assertEquals(1, second.size)
        val appendedAgain = ChatMessageMerge.appendConfirmed(second, msg("m1"))
        assertEquals(1, appendedAgain.size)
    }

    @Test
    fun pending_local_twin_is_replaced() {
        val local = msg("local:1")
        val merged = ChatMessageMerge.appendConfirmed(listOf(local), msg("m1"))
        assertEquals(listOf("m1"), merged.map { it.id })
    }

    @Test
    fun union_by_id_does_not_duplicate_pages() {
        val older = msg("m0").copy(createdAt = 1L)
        val recent = msg("m1").copy(createdAt = 2L)
        val overlap = msg("m1").copy(createdAt = 2L)
        val merged = ChatMessageMerge.unionById(listOf(older, recent), listOf(overlap, older))
        assertEquals(listOf("m0", "m1"), merged.map { it.id })
    }
}
