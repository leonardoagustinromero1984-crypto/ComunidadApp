package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.ChatContextType
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.participation.ParticipationMessaging
import java.util.Collections

object ParticipationCoordinator {
    private val sentKeys: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    suspend fun notifyCampaignOffer(
        campaignId: String,
        campaignTitle: String,
        organizationDisplayName: String,
        organizationId: String?,
        offerSummary: String
    ) {
        val user = AuthProvider.repository.getCurrentUser() ?: return
        val key = ParticipationMessaging.idempotencyKey("campaign", user.id, campaignId)
        if (!sentKeys.add(key)) return
        val body = ParticipationMessaging.campaignOffer(campaignTitle, offerSummary)
        sendToOrganizer(
            peerUserId = organizationId?.takeIf { it.isNotBlank() } ?: "org:$campaignId",
            peerName = organizationDisplayName.ifBlank { "Organizador" },
            contextId = campaignId,
            body = body,
            sender = user
        )
    }

    suspend fun notifyEventRegistration(
        eventId: String,
        eventTitle: String,
        organizationDisplayName: String,
        organizationId: String?
    ) {
        val user = AuthProvider.repository.getCurrentUser() ?: return
        val key = ParticipationMessaging.idempotencyKey("event", user.id, eventId)
        if (!sentKeys.add(key)) return
        val body = ParticipationMessaging.eventRegistration(eventTitle)
        sendToOrganizer(
            peerUserId = organizationId?.takeIf { it.isNotBlank() } ?: "org:$eventId",
            peerName = organizationDisplayName.ifBlank { "Organizador" },
            contextId = eventId,
            body = body,
            sender = user
        )
    }

    fun alreadySent(kind: String, actorId: String, targetId: String): Boolean =
        ParticipationMessaging.idempotencyKey(kind, actorId, targetId) in sentKeys

    private suspend fun sendToOrganizer(
        peerUserId: String,
        peerName: String,
        contextId: String,
        body: String,
        sender: com.comunidapp.app.data.model.User
    ) {
        val chat = DataProvider.chatRepository
        val conversation = chat.getOrCreateConversation(
            currentUser = sender,
            peerUserId = peerUserId,
            peerName = peerName,
            contextType = ChatContextType.GENERAL,
            contextId = contextId
        ).getOrNull() ?: return
        chat.sendMessage(conversation, sender, body)
    }
}
