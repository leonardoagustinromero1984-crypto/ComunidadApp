package com.comunidapp.shared.poc.m22.model

/**
 * POC models adapted from LeoVer M22 public catalog (read-only surface).
 * Kept in shared to avoid moving/app-breaking the production packages.
 */
enum class M22PocCategory { VET, GROOMING, TRAINING, WALKING, BOARDING, TRANSPORT, OTHER }

enum class M22PocPriceType { FIXED, FROM, QUOTE }

data class M22PocListing(
    val id: String,
    val displayName: String,
    val category: M22PocCategory,
    val description: String,
    val city: String,
    val branchCount: Int,
    val priceSummary: String? = null
)

data class M22PocBranch(
    val name: String,
    val city: String,
    val neighborhood: String? = null,
    val coverage: String
)

data class M22PocOffering(
    val name: String,
    val description: String,
    val priceType: M22PocPriceType,
    val priceAmount: Long? = null,
    val currency: String = "ARS"
)

data class M22PocDetail(
    val id: String,
    val displayName: String,
    val category: M22PocCategory,
    val description: String,
    val city: String,
    val branches: List<M22PocBranch>,
    val offerings: List<M22PocOffering>
)
