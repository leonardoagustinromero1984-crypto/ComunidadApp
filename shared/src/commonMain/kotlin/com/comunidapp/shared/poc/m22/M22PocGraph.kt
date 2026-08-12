package com.comunidapp.shared.poc.m22

import com.comunidapp.shared.poc.m22.data.FakeM22PocCatalogRepository
import com.comunidapp.shared.poc.m22.data.M22PocCatalogRepository
import com.comunidapp.shared.poc.m22.data.PocSupabaseConfig
import com.comunidapp.shared.poc.m22.data.SupabaseM22PocCatalogRepository

/**
 * Minimal wiring for the POC — intentionally NOT DataProvider.
 */
object M22PocGraph {
    fun repository(config: PocSupabaseConfig?): M22PocCatalogRepository =
        if (config != null && config.isUsable) {
            runCatching { SupabaseM22PocCatalogRepository.create(config) }
                .getOrElse { FakeM22PocCatalogRepository() }
        } else {
            FakeM22PocCatalogRepository()
        }
}
