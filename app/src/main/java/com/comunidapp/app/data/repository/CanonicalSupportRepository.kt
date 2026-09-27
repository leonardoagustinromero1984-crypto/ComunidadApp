package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeArray
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.decodeObject
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.failureFromThrowable
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.longFromIso
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.requireString
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.string
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.support.SupportCategory
import com.comunidapp.app.domain.support.SupportMessage
import com.comunidapp.app.domain.support.SupportMessageVisibility
import com.comunidapp.app.domain.support.SupportPriority
import com.comunidapp.app.domain.support.SupportTicket
import com.comunidapp.app.domain.support.SupportTicketStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CanonicalSupportRepository : SupportRepository {

    override suspend fun createTicket(
        requesterUserId: String,
        category: SupportCategory,
        subject: String,
        description: String,
        nowEpochMs: Long
    ): AppResult<SupportTicket> = runRpc {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_SUPPORT_TICKET,
            parameters = buildJsonObject {
                put("p_category", category.name)
                put("p_subject", subject)
                put("p_description", description)
            }
        ).decodeAs<JsonElement>()
        val root = decodeObject(element) ?: error("CREATE_TICKET_EMPTY")
        val ticketObj = root["ticket"]?.let { decodeObject(it) } ?: error("CREATE_TICKET_EMPTY")
        parseTicket(ticketObj, fallbackRequester = requesterUserId, fallbackNow = nowEpochMs)
    }

    override suspend fun getMyTickets(requesterUserId: String): AppResult<List<SupportTicket>> = runRpc {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_MY_SUPPORT_TICKETS,
            parameters = buildJsonObject { put("p_limit", 50) }
        ).decodeAs<JsonElement>()
        decodeArray(element).mapNotNull { item ->
            decodeObject(item)?.let {
                parseTicket(it, fallbackRequester = requesterUserId, fallbackDescription = "")
            }
        }
    }

    override suspend fun getTicket(ticketId: String): AppResult<SupportTicket> =
        when (val detail = getTicketDetail(ticketId)) {
            is AppResult.Success -> AppResult.Success(detail.data.ticket)
            is AppResult.Failure -> detail
        }

    override suspend fun getTicketDetail(ticketId: String): AppResult<SupportTicketDetail> {
        val requesterAttempt = runCatching {
            val element = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_GET_SUPPORT_TICKET_REQUESTER,
                parameters = buildJsonObject { put("p_ticket_id", ticketId) }
            ).decodeAs<JsonElement>()
            parseTicketDetail(element)
        }
        if (requesterAttempt.isSuccess) return AppResult.Success(requesterAttempt.getOrThrow())
        val msg = requesterAttempt.exceptionOrNull()?.message.orEmpty().uppercase()
        if (!msg.contains("FORBIDDEN") && !msg.contains("NOT_FOUND")) {
            return failureFromThrowable(requesterAttempt.exceptionOrNull()!!)
        }
        return runRpc {
            val element = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_GET_SUPPORT_TICKET_STAFF,
                parameters = buildJsonObject { put("p_ticket_id", ticketId) }
            ).decodeAs<JsonElement>()
            parseTicketDetail(element)
        }
    }

    override suspend fun listSupportQueue(): AppResult<List<SupportTicket>> = runRpc {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_SUPPORT_QUEUE,
            parameters = buildJsonObject { put("p_limit", 50) }
        ).decodeAs<JsonElement>()
        decodeArray(element).mapNotNull { item ->
            decodeObject(item)?.let { parseTicket(it, fallbackRequester = "", fallbackDescription = "") }
        }
    }

    override suspend fun assignTicket(
        ticketId: String,
        assigneeUserId: String,
        nowEpochMs: Long
    ): AppResult<SupportTicket> = runRpc {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_ASSIGN_SUPPORT_TICKET,
            parameters = buildJsonObject {
                put("p_ticket_id", ticketId)
                put("p_assigned_to_user_id", assigneeUserId)
            }
        ).decodeAs<JsonElement>()
        parseTicket(decodeObject(element) ?: error("ASSIGN_TICKET_EMPTY"), "", nowEpochMs)
    }

    override suspend fun changeTicketStatus(
        ticketId: String,
        status: SupportTicketStatus,
        closeReasonCode: String?,
        nowEpochMs: Long
    ): AppResult<SupportTicket> = runRpc {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CHANGE_SUPPORT_TICKET_STATUS,
            parameters = buildJsonObject {
                put("p_ticket_id", ticketId)
                put("p_status", status.name)
                closeReasonCode?.let { put("p_close_reason_code", it) }
            }
        ).decodeAs<JsonElement>()
        parseTicket(decodeObject(element) ?: error("CHANGE_TICKET_EMPTY"), "", nowEpochMs)
    }

    override suspend fun addRequesterMessage(
        ticketId: String,
        authorUserId: String,
        body: String,
        nowEpochMs: Long
    ): AppResult<SupportMessage> = runRpc {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_ADD_SUPPORT_REQUESTER_MESSAGE,
            parameters = buildJsonObject {
                put("p_ticket_id", ticketId)
                put("p_body", body)
            }
        ).decodeAs<JsonElement>()
        parseMessage(decodeObject(element) ?: error("ADD_MESSAGE_EMPTY"), authorUserId, nowEpochMs)
    }

    override suspend fun addInternalMessage(
        ticketId: String,
        authorUserId: String,
        body: String,
        nowEpochMs: Long
    ): AppResult<SupportMessage> = runRpc {
        val element = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_ADD_SUPPORT_INTERNAL_MESSAGE,
            parameters = buildJsonObject {
                put("p_ticket_id", ticketId)
                put("p_body", body)
            }
        ).decodeAs<JsonElement>()
        parseMessage(decodeObject(element) ?: error("ADD_MESSAGE_EMPTY"), authorUserId, nowEpochMs)
    }

    private fun parseTicketDetail(element: JsonElement): SupportTicketDetail {
        val root = decodeObject(element) ?: error("NOT_FOUND")
        val ticketObj = root["ticket"]?.let { decodeObject(it) } ?: error("NOT_FOUND")
        val msgs = root["messages"]?.let { decodeArray(it) } ?: kotlinx.serialization.json.JsonArray(emptyList())
        return SupportTicketDetail(
            ticket = parseTicket(ticketObj, ""),
            messages = msgs.mapNotNull { item ->
                decodeObject(item)?.let { parseMessage(it, "", 0L) }
            }
        )
    }

    private fun parseTicket(
        obj: JsonObject,
        fallbackRequester: String,
        fallbackNow: Long = 0L,
        fallbackDescription: String = ""
    ): SupportTicket {
        val created = obj.longFromIso("created_at", fallbackNow)
        val category = runCatching {
            SupportCategory.valueOf(obj.string("category")?.trim()?.uppercase().orEmpty())
        }.getOrDefault(SupportCategory.OTHER)
        val status = runCatching {
            SupportTicketStatus.valueOf(obj.string("status")?.trim()?.uppercase().orEmpty())
        }.getOrDefault(SupportTicketStatus.OPEN)
        val priority = runCatching {
            SupportPriority.valueOf(obj.string("priority")?.trim()?.uppercase().orEmpty())
        }.getOrDefault(SupportPriority.NORMAL)
        return SupportTicket(
            id = obj.requireString("id"),
            requesterUserId = obj.string("requester_user_id") ?: fallbackRequester,
            category = category,
            subject = obj.string("subject").orEmpty(),
            description = obj.string("description") ?: fallbackDescription,
            priority = priority,
            status = status,
            assignedToUserId = obj.string("assigned_to_user_id"),
            createdAtEpochMs = created,
            updatedAtEpochMs = obj.longFromIso("updated_at", created),
            resolvedAtEpochMs = obj.string("resolved_at")?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            closedAtEpochMs = obj.string("closed_at")?.let {
                runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            },
            closeReasonCode = obj.string("close_reason_code"),
            linkedModerationCaseId = obj.string("linked_moderation_case_id")
        )
    }

    private fun parseMessage(obj: JsonObject, fallbackAuthor: String, fallbackNow: Long): SupportMessage {
        val visibility = runCatching {
            SupportMessageVisibility.valueOf(obj.string("visibility")?.trim()?.uppercase().orEmpty())
        }.getOrDefault(SupportMessageVisibility.REQUESTER_VISIBLE)
        return SupportMessage(
            id = obj.requireString("id"),
            ticketId = obj.string("ticket_id").orEmpty(),
            authorUserId = obj.string("author_user_id") ?: fallbackAuthor,
            visibility = visibility,
            body = obj.string("body").orEmpty(),
            createdAtEpochMs = obj.longFromIso("created_at", fallbackNow)
        )
    }
}
