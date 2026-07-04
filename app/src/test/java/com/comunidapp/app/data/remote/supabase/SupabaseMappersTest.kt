package com.comunidapp.app.data.remote.supabase

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SupabaseMappersTest {

    @Test
    fun parseUser_mapsAllFields() {
        val row = UserRow(
            id = "uid_1",
            email = "maria@email.com",
            name = "María",
            accountType = "PERSON",
            locationText = "Palermo, CABA",
            emailVerified = true,
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L).toString()
        )

        val user = parseUser(row)

        assertEquals("uid_1", user.id)
        assertEquals("maria@email.com", user.email)
        assertEquals(AccountType.PERSON, user.accountType)
        assertEquals("Palermo, CABA", user.locationText)
        assertEquals(true, user.emailVerified)
        assertEquals(1_700_000_000_000L, user.createdAt)
    }

    @Test
    fun userRow_roundTripPreservesAccountType() {
        val user = User(
            id = "uid_1",
            name = "Refugio",
            email = "refugio@email.com",
            accountType = AccountType.SHELTER,
            locationText = "Villa Crespo"
        )

        val parsed = parseUser(user.toUserRow())

        assertEquals(AccountType.SHELTER, parsed.accountType)
        assertEquals("Villa Crespo", parsed.locationText)
    }

    @Test
    fun parseFeedPost_mapsCountersAndLocation() {
        val row = PostRow(
            id = "post_1",
            authorId = "uid_1",
            authorName = "María",
            type = "GENERAL",
            title = "Hola",
            content = "Contenido",
            locationText = "CABA",
            likeCount = 10,
            commentCount = 3,
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L).toString()
        )

        val post = parseFeedPost(row)

        assertEquals(PostType.GENERAL, post.type)
        assertEquals("CABA", post.locationText)
        assertEquals(10, post.likeCount)
        assertEquals(3, post.commentCount)
    }

    @Test
    fun parsePet_mapsSpeciesAndOwner() {
        val row = PetRow(
            id = "pet_1",
            ownerId = "uid_1",
            name = "Luna",
            species = "DOG",
            sex = "FEMALE",
            size = "MEDIUM",
            ageYears = 2,
            description = "Dócil"
        )

        val pet = parsePet(row)

        assertNotNull(pet)
        assertEquals(PetSpecies.DOG, pet.species)
        assertEquals(PetSex.FEMALE, pet.sex)
        assertEquals(PetSize.MEDIUM, pet.size)
        assertEquals("uid_1", pet.ownerId)
    }

    @Test
    fun postRow_includesAuthorAndType() {
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

        val row = post.toPostRow()

        assertEquals("uid_1", row.authorId)
        assertEquals("URGENT", row.type)
        assertEquals("CABA", row.locationText)
        assertEquals(1, row.likeCount)
    }

    @Test
    fun userRow_preservesEmailOnRoundTrip() {
        val user = User(
            id = "uid_1",
            name = "María",
            email = "maria@email.com",
            accountType = AccountType.PERSON
        )
        val parsed = parseUser(user.toUserRow())
        assertEquals("maria@email.com", parsed.email)
    }
}
