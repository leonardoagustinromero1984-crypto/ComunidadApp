package com.comunidapp.app.data.repository

import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.capability.PersonCapabilityCode
import com.comunidapp.app.domain.capability.PersonCapabilityRecord
import com.comunidapp.app.domain.capability.RescuerCapabilityRules
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface PersonCapabilityRepository {
    suspend fun listMine(): List<PersonCapabilityRecord>
    suspend fun setActive(code: PersonCapabilityCode, active: Boolean): Result<Boolean>
    fun cachedActive(): Set<PersonCapabilityCode>
    fun hasActive(code: PersonCapabilityCode): Boolean = code in cachedActive()
}

class InMemoryPersonCapabilityRepository : PersonCapabilityRepository {
    private val active = mutableSetOf<PersonCapabilityCode>()

    override suspend fun listMine(): List<PersonCapabilityRecord> =
        PersonCapabilityCode.entries.map { PersonCapabilityRecord(it, it in active) }

    override suspend fun setActive(code: PersonCapabilityCode, active: Boolean): Result<Boolean> {
        if (code == PersonCapabilityCode.RESCUER && !active && !RescuerCapabilityRules.canDeactivate()) {
            return Result.failure(IllegalStateException("RESCUER_DEACTIVATION_BLOCKED"))
        }
        if (active) this.active += code else this.active -= code
        return Result.success(active)
    }

    override fun cachedActive(): Set<PersonCapabilityCode> = active.toSet()
}

@Serializable
private data class PersonCapabilityRow(
    val capability: String,
    val active: Boolean = true,
    @SerialName("verification_status") val verificationStatus: String = "NOT_REQUESTED",
    @SerialName("updated_at") val updatedAt: String? = null
)

class CanonicalPersonCapabilityRepository : PersonCapabilityRepository {
    @Volatile
    private var cache: Set<PersonCapabilityCode> = emptySet()

    override suspend fun listMine(): List<PersonCapabilityRecord> {
        val element: JsonElement = supabase.postgrest
            .rpc(CanonicalBackend.RPC_LIST_MY_PERSON_CAPABILITIES)
            .decodeAs()
        val rows = M08RpcDecoding.decodeRows<PersonCapabilityRow>(element)
        val records = rows.mapNotNull { row ->
            val code = PersonCapabilityCode.fromStorage(row.capability) ?: return@mapNotNull null
            PersonCapabilityRecord(code, row.active, row.verificationStatus)
        }
        cache = records.filter { it.active }.map { it.code }.toSet()
        return records
    }

    override suspend fun setActive(code: PersonCapabilityCode, active: Boolean): Result<Boolean> =
        runCatching {
            if (code == PersonCapabilityCode.RESCUER && !active && !RescuerCapabilityRules.canDeactivate()) {
                error("RESCUER_DEACTIVATION_BLOCKED")
            }
            val stored: Boolean = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_SET_PERSON_CAPABILITY,
                parameters = buildJsonObject {
                    put("p_capability", code.storageValue)
                    put("p_active", active)
                }
            ).decodeAs()
            if (active) cache = cache + code else cache = cache - code
            stored
        }

    override fun cachedActive(): Set<PersonCapabilityCode> = cache
}
