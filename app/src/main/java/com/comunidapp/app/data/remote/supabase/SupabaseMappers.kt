package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.BadgeType
import com.comunidapp.app.data.model.ChatContextType
import com.comunidapp.app.data.model.ChatMessage
import com.comunidapp.app.data.model.Conversation
import com.comunidapp.app.data.model.DonationCampaign
import com.comunidapp.app.data.model.DonationType
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.model.ShelterNeed
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.UserBadge
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundStatus
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetReminder
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.SterilizationStatus
import com.comunidapp.app.data.model.VaccinationRecord
import com.comunidapp.app.domain.LeoverModule
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
    @SerialName("active_modules") val activeModules: List<String> = emptyList(),
    @SerialName("reputation_score") val reputationScore: Int = 0,
    @SerialName("profile_private") val profilePrivate: Boolean = true,
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
    @SerialName("weight_kg") val weightKg: Float? = null,
    val color: String? = null,
    val breed: String? = null,
    val personality: String? = null,
    @SerialName("location_text") val locationText: String? = null,
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
    @SerialName("active_modules") val activeModules: List<String> = emptyList(),
    @SerialName("profile_private") val profilePrivate: Boolean = true,
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
    activeModules = row.activeModules.toLeoverModules(),
    reputationScore = row.reputationScore,
    profilePrivate = row.profilePrivate,
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
    activeModules = (activeModules ?: resolvedModules).map { it.name },
    profilePrivate = profilePrivate,
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
    weightKg = row.weightKg,
    color = row.color,
    breed = row.breed,
    personality = row.personality,
    locationText = row.locationText,
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
    weightKg = weightKg,
    color = color,
    breed = breed,
    personality = personality,
    locationText = locationText,
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

@Serializable
data class AdoptionRow(
    val id: String,
    @SerialName("publisher_id") val publisherId: String,
    @SerialName("publisher_name") val publisherName: String,
    @SerialName("shelter_id") val shelterId: String? = null,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val species: String,
    val sex: String,
    @SerialName("age_years") val ageYears: Int = 0,
    @SerialName("age_months") val ageMonths: Int = 0,
    val size: String,
    val location: String,
    val description: String,
    val status: String = AdoptionStatus.AVAILABLE.name,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class LostFoundRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    val type: String,
    @SerialName("pet_name") val petName: String? = null,
    val species: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    val location: String,
    val description: String,
    @SerialName("contact_info") val contactInfo: String,
    val status: String = LostFoundStatus.ACTIVE.name,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

fun parseAdoption(row: AdoptionRow): AdoptionPost = AdoptionPost(
    id = row.id,
    publisherId = row.publisherId,
    shelterId = row.shelterId,
    shelterName = row.publisherName,
    name = row.name,
    photoUrl = row.photoUrl,
    species = enumValueOrDefault(row.species, PetSpecies.OTHER),
    sex = enumValueOrDefault(row.sex, PetSex.UNKNOWN),
    ageYears = row.ageYears,
    ageMonths = row.ageMonths,
    size = enumValueOrDefault(row.size, PetSize.MEDIUM),
    location = row.location,
    description = row.description,
    status = enumValueOrDefault(row.status, AdoptionStatus.AVAILABLE),
    createdAt = row.createdAt.toEpochMillis()
)

fun AdoptionPost.toAdoptionRow(now: Instant = Instant.now()): AdoptionRow = AdoptionRow(
    id = id,
    publisherId = publisherId.orEmpty(),
    publisherName = shelterName,
    shelterId = shelterId,
    name = name,
    photoUrl = photoUrl,
    species = species.name,
    sex = sex.name,
    ageYears = ageYears,
    ageMonths = ageMonths,
    size = size.name,
    location = location,
    description = description,
    status = status.name,
    createdAt = (createdAt?.let { Instant.ofEpochMilli(it) } ?: now).toString(),
    updatedAt = now.toString()
)

fun parseLostFound(row: LostFoundRow): LostFoundPost {
    val createdAt = row.createdAt.toEpochMillis()
    val displayDate = createdAt?.let {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(it))
    } ?: ""
    return LostFoundPost(
        id = row.id,
        authorId = row.authorId,
        authorName = row.authorName,
        type = enumValueOrDefault(row.type, LostFoundType.LOST),
        petName = row.petName,
        species = enumValueOrDefault(row.species, PetSpecies.OTHER),
        photoUrl = row.photoUrl,
        location = row.location,
        description = row.description,
        contactInfo = row.contactInfo,
        status = LostFoundStatus.fromString(row.status),
        date = displayDate,
        latitude = row.latitude,
        longitude = row.longitude,
        createdAt = createdAt
    )
}

fun LostFoundPost.toLostFoundRow(now: Instant = Instant.now()): LostFoundRow = LostFoundRow(
    id = id,
    authorId = authorId,
    authorName = authorName,
    type = type.name,
    petName = petName,
    species = species.name,
    photoUrl = photoUrl,
    location = location,
    description = description,
    contactInfo = contactInfo,
    status = status.name,
    latitude = latitude,
    longitude = longitude,
    createdAt = (createdAt?.let { Instant.ofEpochMilli(it) } ?: now).toString(),
    updatedAt = now.toString()
)

private fun List<String>.toLeoverModules(): Set<LeoverModule>? =
    mapNotNull { runCatching { LeoverModule.valueOf(it) }.getOrNull() }
        .toSet()
        .takeIf { it.isNotEmpty() }

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().find { it.name == value } ?: default

private fun String?.toEpochMillis(): Long? =
    this?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

fun nowIso(): String = Instant.now().toString()

fun decodeRpcUuid(raw: String): String = raw.trim().removeSurrounding("\"").trim()

@Serializable
data class ShelterNeedDto(
    val item: String,
    val quantity: String
)

@Serializable
data class ShelterRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val location: String,
    val description: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
    @SerialName("contact_email") val contactEmail: String? = null,
    @SerialName("adoption_pet_ids") val adoptionPetIds: List<String> = emptyList(),
    val needs: List<ShelterNeedDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FosterHomeRow(
    val id: String,
    @SerialName("host_id") val hostId: String,
    @SerialName("host_name") val hostName: String,
    val location: String,
    val capacity: Int = 1,
    @SerialName("accepted_species") val acceptedSpecies: List<String> = emptyList(),
    val notes: String = "",
    val available: Boolean = true,
    @SerialName("contact_info") val contactInfo: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CommunityEventRow(
    val id: String,
    @SerialName("organizer_id") val organizerId: String,
    val title: String,
    val location: String,
    @SerialName("event_date") val eventDate: String,
    @SerialName("organizer_name") val organizerName: String,
    val description: String,
    @SerialName("contact_info") val contactInfo: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("event_type") val eventType: String = "ADOPTION_FAIR",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class DonationCampaignRow(
    val id: String,
    @SerialName("organizer_id") val organizerId: String,
    val title: String,
    val description: String,
    val location: String,
    @SerialName("goal_amount") val goalAmount: Double? = null,
    @SerialName("raised_amount") val raisedAmount: Double = 0.0,
    @SerialName("donation_type") val donationType: String = DonationType.MONEY.name,
    @SerialName("photo_url") val photoUrl: String? = null,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserBadgeRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("badge_type") val badgeType: String,
    @SerialName("earned_at") val earnedAt: String? = null
)

@Serializable
data class ConversationRow(
    val id: String,
    @SerialName("last_message_text") val lastMessageText: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("context_type") val contextType: String? = null,
    @SerialName("context_id") val contextId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ConversationParticipantRow(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("peer_user_id") val peerUserId: String,
    @SerialName("peer_name") val peerName: String
)

@Serializable
data class MessageRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("sender_name") val senderName: String,
    val content: String,
    @SerialName("created_at") val createdAt: String? = null
)

fun parseShelter(row: ShelterRow): Shelter = Shelter(
    id = row.id,
    ownerId = row.ownerId,
    name = row.name,
    photoUrl = row.photoUrl,
    location = row.location,
    description = row.description,
    contactPhone = row.contactPhone,
    contactEmail = row.contactEmail,
    adoptionPetIds = row.adoptionPetIds,
    needs = row.needs.map { ShelterNeed(it.item, it.quantity) }
)

fun Shelter.toShelterRow(): ShelterRow = ShelterRow(
    id = id,
    ownerId = ownerId,
    name = name,
    location = location,
    description = description,
    photoUrl = photoUrl,
    contactPhone = contactPhone,
    contactEmail = contactEmail,
    adoptionPetIds = adoptionPetIds,
    needs = needs.map { ShelterNeedDto(it.item, it.quantity) }
)

fun parseFosterHome(row: FosterHomeRow): FosterHomeListing = FosterHomeListing(
    id = row.id,
    hostId = row.hostId,
    hostName = row.hostName,
    photoUrl = row.photoUrl,
    location = row.location,
    capacity = row.capacity,
    acceptedSpecies = row.acceptedSpecies.map { enumValueOrDefault(it, PetSpecies.OTHER) },
    notes = row.notes,
    available = row.available,
    contactInfo = row.contactInfo
)

fun FosterHomeListing.toFosterHomeRow(hostId: String): FosterHomeRow = FosterHomeRow(
    id = id,
    hostId = hostId,
    hostName = hostName,
    location = location,
    capacity = capacity,
    acceptedSpecies = acceptedSpecies.map { it.name },
    notes = notes,
    available = available,
    contactInfo = contactInfo,
    photoUrl = photoUrl
)

fun parseCommunityEvent(row: CommunityEventRow): AdoptionEvent = AdoptionEvent(
    id = row.id,
    organizerId = row.organizerId,
    title = row.title,
    photoUrl = row.photoUrl,
    location = row.location,
    date = row.eventDate,
    organizerName = row.organizerName,
    description = row.description,
    contactInfo = row.contactInfo
)

fun AdoptionEvent.toCommunityEventRow(organizerId: String): CommunityEventRow = CommunityEventRow(
    id = id,
    organizerId = organizerId,
    title = title,
    location = location,
    eventDate = date,
    organizerName = organizerName,
    description = description,
    contactInfo = contactInfo,
    photoUrl = photoUrl
)

fun parseDonationCampaign(row: DonationCampaignRow): DonationCampaign = DonationCampaign(
    id = row.id,
    organizerId = row.organizerId,
    title = row.title,
    description = row.description,
    location = row.location,
    goalAmount = row.goalAmount,
    raisedAmount = row.raisedAmount,
    donationType = DonationType.fromString(row.donationType),
    photoUrl = row.photoUrl,
    active = row.active,
    createdAt = row.createdAt.toEpochMillis()
)

fun DonationCampaign.toDonationCampaignRow(): DonationCampaignRow = DonationCampaignRow(
    id = id,
    organizerId = organizerId,
    title = title,
    description = description,
    location = location,
    goalAmount = goalAmount,
    raisedAmount = raisedAmount,
    donationType = donationType.name,
    photoUrl = photoUrl,
    active = active
)

fun parseUserBadge(row: UserBadgeRow): UserBadge? {
    val type = BadgeType.fromString(row.badgeType) ?: return null
    return UserBadge(
        id = row.id,
        userId = row.userId,
        badgeType = type,
        earnedAt = row.earnedAt.toEpochMillis()
    )
}

fun parseConversation(participant: ConversationParticipantRow, row: ConversationRow): Conversation =
    Conversation(
        id = row.id,
        peerUserId = participant.peerUserId,
        peerName = participant.peerName,
        lastMessageText = row.lastMessageText,
        lastMessageAt = row.lastMessageAt.toEpochMillis(),
        contextType = ChatContextType.fromString(row.contextType),
        contextId = row.contextId
    )

fun parseChatMessage(row: MessageRow): ChatMessage = ChatMessage(
    id = row.id,
    conversationId = row.conversationId,
    senderId = row.senderId,
    senderName = row.senderName,
    content = row.content,
    createdAt = row.createdAt.toEpochMillis()
)

fun ChatMessage.toMessageRow(): MessageRow = MessageRow(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    senderName = senderName,
    content = content,
    createdAt = createdAt?.let { Instant.ofEpochMilli(it).toString() }
)
