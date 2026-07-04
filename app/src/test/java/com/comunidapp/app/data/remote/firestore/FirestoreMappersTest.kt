package com.comunidapp.app.data.remote.firestore

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.User
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreMappersTest {

    @Test
    fun parseUser_mapsAllFields() {
        val data = mapOf(
            "id" to "uid_1",
            "email" to "maria@email.com",
            "name" to "María",
            "accountType" to "PERSON",
            "locationText" to "Palermo, CABA",
            "emailVerified" to true,
            "createdAt" to Timestamp(1_700_000_000, 0)
        )

        val user = parseUser("uid_1", data)

        assertNotNull(user)
        assertEquals("uid_1", user!!.id)
        assertEquals("maria@email.com", user.email)
        assertEquals(AccountType.PERSON, user.accountType)
        assertEquals("Palermo, CABA", user.locationText)
        assertEquals(true, user.emailVerified)
        assertEquals(1_700_000_000_000L, user.createdAt)
    }

    @Test
    fun parseUser_returnsNullWithoutEmail() {
        val user = parseUser("uid_1", mapOf("name" to "Sin email"))
        assertNull(user)
    }

    @Test
    fun parseFeedPost_mapsCountersAndLocation() {
        val data = mapOf(
            "id" to "post_1",
            "authorId" to "uid_1",
            "authorName" to "María",
            "type" to "GENERAL",
            "title" to "Hola",
            "content" to "Contenido",
            "locationText" to "CABA",
            "likeCount" to 10L,
            "commentCount" to 3L,
            "createdAt" to Timestamp(1_700_000_000, 0)
        )

        val post = parseFeedPost("post_1", data)

        assertNotNull(post)
        assertEquals(PostType.GENERAL, post!!.type)
        assertEquals("CABA", post.locationText)
        assertEquals(10, post.likeCount)
        assertEquals(3, post.commentCount)
    }

    @Test
    fun userToFirestoreMap_roundTripPreservesAccountType() {
        val user = User(
            id = "uid_1",
            name = "Refugio",
            email = "refugio@email.com",
            accountType = AccountType.SHELTER,
            locationText = "Villa Crespo"
        )

        val map = user.toFirestoreMap()
        val parsed = parseUser("uid_1", map)

        assertEquals(AccountType.SHELTER, parsed?.accountType)
        assertEquals("Villa Crespo", parsed?.locationText)
    }

    @Test
    fun parsePet_mapsSpeciesAndOwner() {
        val data = mapOf(
            "id" to "pet_1",
            "ownerId" to "uid_1",
            "name" to "Luna",
            "species" to "DOG",
            "sex" to "FEMALE",
            "size" to "MEDIUM",
            "ageYears" to 2L,
            "description" to "Dócil"
        )

        val pet = parsePet("pet_1", data)

        assertNotNull(pet)
        assertEquals(PetSpecies.DOG, pet!!.species)
        assertEquals(PetSex.FEMALE, pet.sex)
        assertEquals(PetSize.MEDIUM, pet.size)
        assertEquals("uid_1", pet.ownerId)
    }

    @Test
    fun feedPostToFirestoreMap_includesAuthorAndType() {
        val post = FeedPost(
            id = "post_1",
            authorId = "uid_1",
            authorName = "María",
            type = PostType.URGENT,
            title = "Urgente",
            content = "Ayuda",
            locationText = "CABA",
            likeCount = 1,
            commentCount = 2
        )

        val map = post.toFirestoreMap()

        assertEquals("uid_1", map["authorId"])
        assertEquals("URGENT", map["type"])
        assertEquals("CABA", map["locationText"])
        assertEquals(1, map["likeCount"])
    }
}
