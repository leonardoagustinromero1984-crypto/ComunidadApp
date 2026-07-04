package com.comunidapp.app.data.remote.firestore

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetReminder
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.VaccinationRecord
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

object FirestoreCollections {
    const val USERS = "users"
    const val PETS = "pets"
    const val POSTS = "posts"
}

fun DocumentSnapshot.toUser(): User? {
    if (!exists()) return null
    return parseUser(id, data ?: return null)
}

fun parseUser(id: String, data: Map<String, Any?>): User? {
    val email = data["email"] as? String ?: return null
    return User(
        id = data["id"] as? String ?: id,
        name = data["name"] as? String ?: "",
        email = email,
        accountType = AccountType.fromString(data["accountType"] as? String),
        profileImageUrl = data["profileImageUrl"] as? String,
        bio = data["bio"] as? String,
        locationText = data["locationText"] as? String,
        phone = data["phone"] as? String,
        phonePublic = data["phonePublic"] as? Boolean ?: false,
        emailVerified = data["emailVerified"] as? Boolean ?: false,
        fosterHomeActive = data["fosterHomeActive"] as? Boolean ?: false,
        createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.time,
        updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time
    )
}

fun User.toFirestoreMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>(
        "id" to id,
        "name" to name,
        "email" to email,
        "accountType" to accountType.name,
        "emailVerified" to emailVerified,
        "phonePublic" to phonePublic,
        "fosterHomeActive" to fosterHomeActive
    )
    profileImageUrl?.let { map["profileImageUrl"] = it }
    bio?.let { map["bio"] = it }
    locationText?.let { map["locationText"] = it }
    phone?.let { map["phone"] = it }
    createdAt?.let { map["createdAt"] = Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) }
    updatedAt?.let { map["updatedAt"] = Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) }
    return map
}

fun DocumentSnapshot.toFeedPost(): FeedPost? {
    if (!exists()) return null
    return parseFeedPost(id, data ?: return null)
}

fun parseFeedPost(id: String, data: Map<String, Any?>): FeedPost? {
    val authorId = data["authorId"] as? String ?: return null
    return FeedPost(
        id = data["id"] as? String ?: id,
        authorId = authorId,
        authorName = data["authorName"] as? String ?: "",
        authorImageUrl = data["authorImageUrl"] as? String,
        type = PostType.fromString(data["type"] as? String),
        title = data["title"] as? String ?: "",
        content = data["content"] as? String ?: "",
        imageUrl = data["imageUrl"] as? String,
        locationText = data["locationText"] as? String,
        likeCount = (data["likeCount"] as? Number)?.toInt() ?: 0,
        commentCount = (data["commentCount"] as? Number)?.toInt() ?: 0,
        createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.time,
        updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time
    )
}

fun FeedPost.toFirestoreMap(): Map<String, Any?> {
    val now = Timestamp.now()
    val map = mutableMapOf<String, Any?>(
        "id" to id,
        "authorId" to authorId,
        "authorName" to authorName,
        "type" to type.name,
        "title" to title,
        "content" to content,
        "likeCount" to likeCount,
        "commentCount" to commentCount,
        "createdAt" to Timestamp(
            (createdAt ?: now.toDate().time) / 1000,
            (((createdAt ?: now.toDate().time) % 1000) * 1_000_000).toInt()
        ),
        "updatedAt" to Timestamp(
            (updatedAt ?: now.toDate().time) / 1000,
            (((updatedAt ?: now.toDate().time) % 1000) * 1_000_000).toInt()
        )
    )
    authorImageUrl?.let { map["authorImageUrl"] = it }
    imageUrl?.let { map["imageUrl"] = it }
    locationText?.let { map["locationText"] = it }
    return map
}

fun DocumentSnapshot.toPet(): Pet? {
    if (!exists()) return null
    return parsePet(id, data ?: return null)
}

fun parsePet(id: String, data: Map<String, Any?>): Pet? {
    val ownerId = data["ownerId"] as? String ?: return null
    val species = data["species"] as? String ?: return null
    val sex = data["sex"] as? String ?: return null
    val size = data["size"] as? String ?: return null
    return Pet(
        id = data["id"] as? String ?: id,
        ownerId = ownerId,
        name = data["name"] as? String ?: "",
        photoUrl = data["photoUrl"] as? String,
        species = enumValueOrDefault(species, PetSpecies.OTHER),
        sex = enumValueOrDefault(sex, PetSex.UNKNOWN),
        ageYears = (data["ageYears"] as? Number)?.toInt() ?: 0,
        ageMonths = (data["ageMonths"] as? Number)?.toInt() ?: 0,
        size = enumValueOrDefault(size, PetSize.MEDIUM),
        description = data["description"] as? String ?: "",
        vaccinations = parseVaccinations(data["vaccinations"]),
        lastDeworming = data["lastDeworming"] as? String,
        lastFleaTreatment = data["lastFleaTreatment"] as? String,
        reminders = parseReminders(data["reminders"]),
        createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.time,
        updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time
    )
}

fun Pet.toFirestoreMap(): Map<String, Any?> {
    val now = Timestamp.now()
    val map = mutableMapOf<String, Any?>(
        "id" to id,
        "ownerId" to ownerId,
        "name" to name,
        "species" to species.name,
        "sex" to sex.name,
        "ageYears" to ageYears,
        "ageMonths" to ageMonths,
        "size" to size.name,
        "description" to description,
        "createdAt" to Timestamp(
            (createdAt ?: now.toDate().time) / 1000,
            (((createdAt ?: now.toDate().time) % 1000) * 1_000_000).toInt()
        ),
        "updatedAt" to Timestamp(
            (updatedAt ?: now.toDate().time) / 1000,
            (((updatedAt ?: now.toDate().time) % 1000) * 1_000_000).toInt()
        )
    )
    photoUrl?.let { map["photoUrl"] = it }
    lastDeworming?.let { map["lastDeworming"] = it }
    lastFleaTreatment?.let { map["lastFleaTreatment"] = it }
    if (vaccinations.isNotEmpty()) {
        map["vaccinations"] = vaccinations.map {
            mapOf(
                "name" to it.name,
                "date" to it.date,
                "nextDueDate" to it.nextDueDate
            )
        }
    }
    if (reminders.isNotEmpty()) {
        map["reminders"] = reminders.map {
            mapOf(
                "id" to it.id,
                "title" to it.title,
                "date" to it.date,
                "type" to it.type
            )
        }
    }
    return map
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().find { it.name == value } ?: default

@Suppress("UNCHECKED_CAST")
private fun parseVaccinations(raw: Any?): List<VaccinationRecord> {
    val list = raw as? List<Map<String, Any?>> ?: return emptyList()
    return list.mapNotNull { item ->
        val name = item["name"] as? String ?: return@mapNotNull null
        val date = item["date"] as? String ?: return@mapNotNull null
        VaccinationRecord(name, date, item["nextDueDate"] as? String)
    }
}

@Suppress("UNCHECKED_CAST")
private fun parseReminders(raw: Any?): List<PetReminder> {
    val list = raw as? List<Map<String, Any?>> ?: return emptyList()
    return list.mapNotNull { item ->
        val id = item["id"] as? String ?: return@mapNotNull null
        val title = item["title"] as? String ?: return@mapNotNull null
        val date = item["date"] as? String ?: return@mapNotNull null
        val type = item["type"] as? String ?: return@mapNotNull null
        PetReminder(id, title, date, type)
    }
}
