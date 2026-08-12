package com.comunidapp.shared.poc.m22.data

import com.comunidapp.shared.poc.m22.domain.M22PocPrivacy
import com.comunidapp.shared.poc.m22.domain.M22PocPricing
import com.comunidapp.shared.poc.m22.model.M22PocBranch
import com.comunidapp.shared.poc.m22.model.M22PocCategory
import com.comunidapp.shared.poc.m22.model.M22PocDetail
import com.comunidapp.shared.poc.m22.model.M22PocListing
import com.comunidapp.shared.poc.m22.model.M22PocOffering
import com.comunidapp.shared.poc.m22.model.M22PocPriceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Read-only contract for the KMP POC (subset of production M22ProviderRepository). */
interface M22PocCatalogRepository {
    fun observeCatalog(): Flow<List<M22PocListing>>
    fun observeDetail(providerId: String): Flow<M22PocDetail?>
}

class FakeM22PocCatalogRepository(
    private val seed: List<M22PocDetail> = defaultSeed(),
    private val failCatalog: Boolean = false,
    private val failDetail: Boolean = false
) : M22PocCatalogRepository {

    override fun observeCatalog(): Flow<List<M22PocListing>> = flow {
        if (failCatalog) error("M22_CATALOG_UNAVAILABLE")
        emit(
            seed.map { detail ->
                M22PocPrivacy.sanitizeListing(
                    M22PocListing(
                        id = detail.id,
                        displayName = detail.displayName,
                        category = detail.category,
                        description = detail.description,
                        city = detail.city,
                        branchCount = detail.branches.size,
                        priceSummary = M22PocPricing.summary(detail.offerings)
                    )
                )
            }
        )
    }

    override fun observeDetail(providerId: String): Flow<M22PocDetail?> = flow {
        if (failDetail) error("M22_PROVIDER_NOT_FOUND")
        emit(seed.firstOrNull { it.id == providerId || it.displayName == providerId }?.let(M22PocPrivacy::sanitizeDetail))
    }

    companion object {
        fun defaultSeed(): List<M22PocDetail> = listOf(
            M22PocDetail(
                id = "m22_provider_grooming",
                displayName = "Patitas Centro",
                category = M22PocCategory.GROOMING,
                description = "Baño y peluquería. Contacto demo@patitas.test o +54 11 5555-5555.",
                city = "CABA",
                branches = listOf(
                    M22PocBranch("Sede Centro", "CABA", "Balvanera", "Barrio: Balvanera")
                ),
                offerings = listOf(
                    M22PocOffering("Baño completo", "Baño y secado", M22PocPriceType.FIXED, 18000)
                )
            ),
            M22PocDetail(
                id = "m22_provider_vet",
                displayName = "Clínica Animal Sur",
                category = M22PocCategory.VET,
                description = "Atención clínica general.",
                city = "Avellaneda",
                branches = listOf(
                    M22PocBranch("Consultorio Sur", "Avellaneda", null, "Ciudad: Avellaneda")
                ),
                offerings = listOf(
                    M22PocOffering("Consulta clínica", "Consulta general", M22PocPriceType.FIXED, 25000)
                )
            )
        )
    }
}
