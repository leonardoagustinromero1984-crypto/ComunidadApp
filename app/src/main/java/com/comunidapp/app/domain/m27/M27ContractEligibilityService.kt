package com.comunidapp.app.domain.m27

import com.comunidapp.app.data.model.M27ApiContract
import com.comunidapp.app.data.model.M27ContractStatus
import com.comunidapp.app.data.model.M27PublicContract

object M27ContractEligibilityService {
    fun isEligibleForDisplay(contract: M27ApiContract): Boolean =
        contract.status == M27ContractStatus.PUBLISHED

    fun filterEligiblePublic(contracts: List<M27ApiContract>): List<M27PublicContract> =
        contracts.filter { isEligibleForDisplay(it) }.map { it.toPublic() }
}
