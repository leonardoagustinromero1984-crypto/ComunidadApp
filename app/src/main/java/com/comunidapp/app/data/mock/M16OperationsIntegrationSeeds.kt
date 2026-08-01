package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.M15FosterHomeStatus
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M16MockOrganizations
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetPlacement
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterProfile
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.repository.M11ShelterMemoryStore
import com.comunidapp.app.data.repository.M15MemoryStore
import com.comunidapp.app.data.repository.M16MemoryStore

/** IDs compartidos M16 Bloque 3 — integración mock M08/M09/M11/M15. */
object M16IntegrationPetIds {
    const val PET_HOUSED = "pet_shelter_norte_1"
    const val PET_FOSTER = "pet_shelter_norte_2"
    const val PET_HOUSED_ADOPTION = "pet_shelter_norte_3"
    const val PET_ADOPTED = "pet_shelter_norte_4"
    const val PET_INCONSISTENT = "pet_shelter_norte_5"
    const val PET_RESERVED = "pet_shelter_norte_6"
}

/**
 * Seeds deterministas para proyección operativa M16 Bloque 3 (mock only).
 */
object M16OperationsIntegrationSeeds {
    private var seeded = false

    fun seedIfNeeded(
        m16Store: M16MemoryStore,
        m11Store: M11ShelterMemoryStore,
        m15Store: M15MemoryStore,
        actorUserId: String = "mock_user_admin"
    ) {
        if (seeded) return
        seeded = true
        m16Store.seedDefaults(actorUserId)
        seedPets()
        seedM11(m11Store, actorUserId)
        seedM15(m15Store, actorUserId)
        seedAdoptions()
    }

    private fun seedPets() {
        val now = System.currentTimeMillis()
        val pets = listOf(
            pet(M16IntegrationPetIds.PET_HOUSED, "Bruno", PetSpecies.DOG),
            pet(M16IntegrationPetIds.PET_FOSTER, "Lola", PetSpecies.DOG),
            pet(M16IntegrationPetIds.PET_HOUSED_ADOPTION, "Milo", PetSpecies.CAT),
            pet(M16IntegrationPetIds.PET_ADOPTED, "Nina", PetSpecies.CAT, status = "ARCHIVED"),
            pet(M16IntegrationPetIds.PET_INCONSISTENT, "Tito", PetSpecies.DOG),
            pet(M16IntegrationPetIds.PET_RESERVED, "Rocco", PetSpecies.DOG)
        )
        pets.forEach { p ->
            if (InMemoryDataStore.getPetById(p.id) == null) {
                InMemoryDataStore.addPet(p)
            }
        }
    }

    private fun pet(
        id: String,
        name: String,
        species: PetSpecies,
        status: String = "ACTIVE"
    ): Pet = Pet(
        id = id,
        ownerId = null,
        name = name,
        species = species,
        sex = PetSex.MALE,
        ageYears = 2,
        size = PetSize.MEDIUM,
        description = "Mascota integración M16 Bloque 3",
        status = status
    )

    private fun seedM11(store: M11ShelterMemoryStore, actor: String) {
        val org = M16MockOrganizations.ORG_NORTE
        store.organizationStatus.value = store.organizationStatus.value + (org to "ACTIVE")
        store.orgManagers.value = store.orgManagers.value + (org to setOf(actor))
        val profileId = "m11_shelter_norte_ops"
        if (store.profiles.value.none { it.organizationId == org }) {
            store.profiles.value = store.profiles.value + ShelterProfile(
                id = profileId,
                organizationId = org,
                displayName = "Operaciones Norte M11",
                status = ShelterStatus.ACTIVE,
                totalCapacity = 40,
                currentOccupancy = 2,
                reservedCapacity = 0,
                acceptedSpecies = setOf("DOG", "CAT"),
                publicZoneText = "Zona norte",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        val placements = listOf(
            placement(M16IntegrationPetIds.PET_HOUSED, profileId, actor, ShelterPetPlacementStatus.ACTIVE),
            placement(M16IntegrationPetIds.PET_HOUSED_ADOPTION, profileId, actor, ShelterPetPlacementStatus.ACTIVE),
            placement(
                M16IntegrationPetIds.PET_INCONSISTENT,
                profileId,
                actor,
                ShelterPetPlacementStatus.ACTIVE
            ),
            placement(
                M16IntegrationPetIds.PET_RESERVED,
                profileId,
                actor,
                ShelterPetPlacementStatus.RESERVED
            )
        )
        store.placements.value = store.placements.value.filterNot { p ->
            p.petId in placements.map { it.petId }
        } + placements
    }

    private fun placement(
        petId: String,
        shelterId: String,
        actor: String,
        status: ShelterPetPlacementStatus
    ): ShelterPetPlacement = ShelterPetPlacement(
        id = "m11_plc_$petId",
        shelterProfileId = shelterId,
        petId = petId,
        intakeType = ShelterIntakeType.RESCUE,
        status = status,
        admittedAt = System.currentTimeMillis(),
        admittedBy = actor
    )

    private fun seedM15(store: M15MemoryStore, actor: String) {
        val org = M16MockOrganizations.ORG_NORTE
        val homeId = "m15_home_demo"
        if (store.homes.value.isEmpty()) {
            store.upsertHome(
                com.comunidapp.app.data.model.M15FosterHome(
                    id = homeId,
                    ownerUserId = "user_5",
                    displayName = "Hogar tránsito demo",
                    status = M15FosterHomeStatus.ACTIVE,
                    totalCapacity = 3,
                    currentOccupancy = 1,
                    acceptedSpecies = setOf("DOG"),
                    zoneText = "GBA norte",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        val reqId = "m15_req_demo"
        val fosterPlacement = M15FosterPlacement(
            id = "m15_plc_foster_1",
            fosterRequestId = reqId,
            fosterHomeId = homeId,
            petId = M16IntegrationPetIds.PET_FOSTER,
            fosterUserId = "user_5",
            requesterUserId = actor,
            requesterOrganizationId = org,
            status = M15FosterPlacementStatus.ACTIVE,
            startedAt = System.currentTimeMillis()
        )
        val inconsistentFoster = M15FosterPlacement(
            id = "m15_plc_inconsistent",
            fosterRequestId = "$reqId/inc",
            fosterHomeId = homeId,
            petId = M16IntegrationPetIds.PET_INCONSISTENT,
            fosterUserId = "user_5",
            requesterUserId = actor,
            requesterOrganizationId = org,
            status = M15FosterPlacementStatus.ACTIVE,
            startedAt = System.currentTimeMillis()
        )
        store.upsertPlacement(fosterPlacement)
        store.upsertPlacement(inconsistentFoster)
        store.upsertPlacement(
            M15FosterPlacement(
                id = "m15_plc_closed",
                fosterRequestId = "$reqId/closed",
                fosterHomeId = homeId,
                petId = M16IntegrationPetIds.PET_ADOPTED,
                fosterUserId = "user_5",
                requesterUserId = actor,
                requesterOrganizationId = org,
                status = M15FosterPlacementStatus.COMPLETED,
                startedAt = System.currentTimeMillis() - 86_400_000L,
                endedAt = System.currentTimeMillis() - 43_200_000L
            )
        )
    }

    private fun seedAdoptions() {
        val org = M16MockOrganizations.ORG_NORTE
        val now = System.currentTimeMillis()
        val posts = listOf(
            AdoptionPost(
                id = "adopt_m16_1",
                petId = M16IntegrationPetIds.PET_HOUSED_ADOPTION,
                publisherOrganizationId = org,
                publisherId = "mock_user_admin",
                shelterId = org,
                shelterName = "Refugio Comunitario Norte",
                name = "Milo",
                species = PetSpecies.CAT,
                sex = PetSex.MALE,
                ageYears = 1,
                size = PetSize.SMALL,
                location = "CABA",
                description = "Busca hogar",
                status = AdoptionStatus.PUBLISHED
            ),
            AdoptionPost(
                id = "adopt_m16_2",
                petId = M16IntegrationPetIds.PET_ADOPTED,
                publisherOrganizationId = org,
                publisherId = "mock_user_admin",
                shelterId = org,
                shelterName = "Refugio Comunitario Norte",
                name = "Nina",
                species = PetSpecies.CAT,
                sex = PetSex.FEMALE,
                ageYears = 3,
                size = PetSize.SMALL,
                location = "CABA",
                description = "Adoptada recientemente",
                status = AdoptionStatus.ADOPTED,
                updatedAt = now - 5 * 86_400_000L
            ),
            AdoptionPost(
                id = "adopt_m16_3",
                petId = "pet_shelter_norte_old",
                publisherOrganizationId = org,
                publisherId = "mock_user_admin",
                shelterId = org,
                shelterName = "Refugio Comunitario Norte",
                name = "Viejo",
                species = PetSpecies.DOG,
                sex = PetSex.MALE,
                ageYears = 8,
                size = PetSize.MEDIUM,
                location = "CABA",
                description = "Adoptada hace meses",
                status = AdoptionStatus.ADOPTED,
                updatedAt = now - 60 * 86_400_000L
            )
        )
        posts.forEach { post ->
            if (InMemoryDataStore.getAdoptionPostById(post.id) == null) {
                InMemoryDataStore.addAdoptionPost(post)
            }
        }
    }
}
