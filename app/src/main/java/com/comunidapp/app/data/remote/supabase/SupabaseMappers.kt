package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetReminder
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.VaccinationRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class VaccinationRecordDto(
    val name: String,
    val date: String,
    @SerialName("next_due_date") val nextDueDate: String? = null
)

@Serializable
data class PetReminderDto(
    val id: String,
    val title: String,
    val date: String,
    val type: String
)

@Serializable
data class UserRow(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("account_type") val accountType: String = AccountType.PERSON.name,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    val bio: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    val phone: String? = null,
    @SerialName("phone_public") val phonePublic: Boolean = false,
    @SerialName("email_verified") val emailVerified: Boolean = false,
    @SerialName("foster_home_active") val fosterHomeActive: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PetRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val species: String,
    val sex: String,
    @SerialName("age_years") val ageYears: Int = 0,
    @SerialName("age_months") val ageMonths: Int = 0,
    val size: String,
    val description: String,
    val vaccinations: List<VaccinationRecordDto> = emptyList(),
    @SerialName("last_deworming") val lastDeworming: String? = null,
    @SerialName("deworming_product") val dewormingProduct: String? = null,
    @SerialName("last_flea_treatment") val lastFleaTreatment: String? = null,
    @SerialName("flea_treatment_product") val fleaTreatmentProduct: String? = null,
    val sterilized: String? = null,
    @SerialName("microchip_id") val microchipId: String? = null,
    @SerialName("last_vet_visit") val lastVetVisit: String? = null,
    @SerialName("health_notes") val healthNotes: String? = null,
    val reminders: List<PetReminderDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PostRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_image_url") val authorImageUrl: String? = null,
    val type: String,
    val title: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UserUpdateRow(
    val name: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    val bio: String? = null,
    @SerialName("location_text") val locationText: String? = null,
    val phone: String? = null,
    @SerialName("phone_public") val phonePublic: Boolean = false,
    @SerialName("foster_home_active") val fosterHomeActive: Boolean = false,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class UserEmailVerifiedUpdateRow(
    @SerialName("email_verified") val emailVerified: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class IdRow(val id: String)

fun parseUser(row: UserRow): User = User(
    id = row.id,
    email = row.email,
    name = row.name,
    accountType = AccountType.fromString(row.accountType),
    profileImageUrl = row.profileImageUrl,
    bio = row.bio,
    locationText = row.locationText,
    phone = row.phone,
    phonePublic = row.phonePublic,
    emailVerified = row.emailVerified,
    fosterHomeActive = row.fosterHomeActive,
    createdAt = row.createdAt.toEpochMillis(),
    updatedAt = row.updatedAt.toEpochMillis()
)

fun User.toUserUpdateRow(now: Instant = Instant.now()): UserUpdateRow = UserUpdateRow(
    name = name,
    accountType = accountType.name,
    profileImageUrl = profileImageUrl,
    bio = bio,
    locationText = locationText,
    phone = phone,
    phonePublic = phonePublic,
    fosterHomeActive = fosterHomeActive,
    updatedAt = now.toString()
)

fun User.toUserRow(now: Instant = Instant.now()): UserRow = UserRow(
    id = id,
    email = email,
    name = name,
    accountType = accountType.name,
    profileImageUrl = profileImageUrl,
    bio = bio,
    locationText = locationText,
    phone = phone,
    phonePublic = phonePublic,
    emailVerified = emailVerified,
    fosterHomeActive = fosterHomeActive,
    createdAt = (createdAt?.let { Instant.ofEpochMilli(it) } ?: now).toString(),
    updatedAt = now.toString()
)

fun parsePet(row: PetRow): Pet = Pet(
    id = row.id,
    ownerId = row.ownerId,
    name = row.name,
    photoUrl = row.photoUrl,
    species = enumValueOrDefault(row.species, PetSpecies.OTHER),
    sex = enumValueOrDefault(row.sex, PetSex.UNKNOWN),
    ageYears = row.ageYears,
    ageMonths = row.ageMonths,
    size = enumValueOrDefault(row.size, PetSize.MEDIUM),
    description = row.description,
    vaccinations = row.vaccinations.map { VaccinationRecord(it.name, it.date, it.nextDueDate) },
    lastDeworming = row.lastDeworming,
    dewormingProduct = row.dewormingProduct,
    lastFleaTreatment = row.lastFleaTreatment,
    fleaTreatmentProduct = row.fleaTreatmentProduct,
    sterilized = SterilizationStatus.fromString(row.sterilized),
    microchipId = row.microchipId,
    lastVetVisit = row.lastVetVisit,
    healthNotes = row.healthNotes,
    reminders = row.reminders.map { PetReminder(it.id, it.title, it.date, it.type) },
    createdAt = row.createdAt.toEpochMillis(),
    updatedAt = row.updatedAt.toEpochMillis()
)

fun Pet.toPetRow(now: Instant = Instant.now()): PetRow = PetRow(
    id = id,
    ownerId = ownerId,
    name = name,
    photoUrl = photoUrl,
    species = species.name,
    sex = sex.name,
    ageYears = ageYears,
    ageMonths = ageMonths,
    size = size.name,
    description = description,
    vaccinations = vaccinations.map { VaccinationRecordDto(it.name, it.date, it.nextDueDate) },
    lastDeworming = lastDeworming,
    dewormingProduct = dewormingProduct,
    lastFleaTreatment = lastFleaTreatment,
    fleaTreatmentProduct = fleaTreatmentProduct,
    sterilized = sterilized?.name,
    microchipId = microchipId,
    lastVetVisit = lastVetVisit,
    healthNotes = healthNotes,
    reminders = reminders.map { PetReminderDto(it.id, it.title, it.date, it.type) },
    createdAt = (createdAt?.let { Instant.ofEpochMilli(it) } ?: now).toString(),
    updatedAt = now.toString()
)

fun parseFeedPost(row: PostRow): FeedPost = FeedPost(
    id = row.id,
    authorId = row.authorId,
    authorName = row.authorName,
    authorImageUrl = row.authorImageUrl,
    type = PostType.fromString(row.type),
    title = row.title,
    content = row.content,
    imageUrl = row.imageUrl,
    locationText = row.locationText,
    likeCount = row.likeCount,
    commentCount = row.commentCount,
    createdAt = row.createdAt.toEpochMillis(),
    updatedAt = row.updatedAt.toEpochMillis()
)

fun FeedPost.toPostRow(now: Instant = Instant.now()): PostRow = PostRow(
    id = id,
    authorId = authorId,
    authorName = authorName,
    authorImageUrl = authorImageUrl,
    type = type.name,
    title = title,
    content = content,
    imageUrl = imageUrl,
    locationText = locationText,
    likeCount = likeCount,
    commentCount = commentCount,
    createdAt = (createdAt?.let { Instant.ofEpochMilli(it) } ?: now).toString(),
    updatedAt = now.toString()
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().find { it.name == value } ?: default

private fun String?.toEpochMillis(): Long? =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

fun nowIso(): String = Instant.now().toString()
