package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetReminder
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.Shelter
import com.comunidapp.app.data.model.ShelterNeed
import com.comunidapp.app.data.model.User
import com.comunidapp.app.data.model.VaccinationRecord

object MockData {

    object Images {
        const val USER = "https://i.pravatar.cc/300?u=maria"
        const val DOG_1 = "https://placedog.net/400/300?id=1"
        const val DOG_2 = "https://placedog.net/400/300?id=2"
        const val DOG_3 = "https://placedog.net/400/300?id=3"
        const val DOG_4 = "https://placedog.net/400/300?id=4"
        const val DOG_5 = "https://placedog.net/400/300?id=5"
        const val CAT_1 = "https://placekitten.com/400/300"
        const val CAT_2 = "https://placekitten.com/401/300"
        const val CAT_3 = "https://placekitten.com/402/300"
        const val SHELTER_1 = "https://picsum.photos/seed/shelter1/400/300"
        const val SHELTER_2 = "https://picsum.photos/seed/shelter2/400/300"
        const val FEED_1 = "https://placedog.net/400/300?id=10"
        const val FEED_2 = "https://placedog.net/400/300?id=11"
        const val FEED_3 = "https://placedog.net/400/300?id=12"
    }

    val currentUser = User(
        id = "user_1",
        name = "María González",
        email = "maria@email.com",
        profileImageUrl = Images.USER,
        bio = "Amante de los animales. Adopté a Luna hace 2 años 🐾",
        location = "Buenos Aires, Argentina",
        petIds = listOf("pet_1", "pet_2")
    )

    val users = listOf(
        currentUser,
        User(
            id = "user_2",
            name = "Refugio Patitas",
            email = "contacto@patitas.org",
            bio = "Refugio sin fines de lucro. Rescatamos y damos en adopción.",
            location = "CABA, Argentina"
        ),
        User(
            id = "user_3",
            name = "Carlos Ruiz",
            email = "carlos@email.com",
            bio = "Rescatista voluntario",
            location = "La Plata, Argentina"
        )
    )

    val pets = listOf(
        Pet(
            id = "pet_1",
            ownerId = "user_1",
            name = "Luna",
            photoUrl = Images.DOG_1,
            species = PetSpecies.DOG,
            sex = PetSex.FEMALE,
            ageYears = 3,
            ageMonths = 2,
            size = PetSize.MEDIUM,
            description = "Mestiza dócil y juguetona. Le encanta correr en el parque.",
            vaccinations = listOf(
                VaccinationRecord("Rabia", "15/01/2025", "15/01/2026"),
                VaccinationRecord("Quintuple", "10/03/2025", "10/03/2026")
            ),
            lastDeworming = "01/06/2025",
            lastFleaTreatment = "15/05/2025",
            reminders = listOf(
                PetReminder("rem_1", "Vacuna Rabia", "15/01/2026", "Vacunación"),
                PetReminder("rem_2", "Desparasitación", "01/09/2025", "Salud")
            )
        ),
        Pet(
            id = "pet_2",
            ownerId = "user_1",
            name = "Michi",
            photoUrl = Images.CAT_1,
            species = PetSpecies.CAT,
            sex = PetSex.MALE,
            ageYears = 1,
            ageMonths = 6,
            size = PetSize.SMALL,
            description = "Gato curioso y cariñoso. Duerme todo el día.",
            vaccinations = listOf(
                VaccinationRecord("Triple Felina", "20/02/2025", "20/02/2026")
            ),
            lastDeworming = "10/05/2025",
            lastFleaTreatment = "10/05/2025",
            reminders = emptyList()
        )
    )

    val feedPosts = listOf(
        FeedPost(
            id = "feed_1",
            authorId = "user_2",
            authorName = "Refugio Patitas",
            type = PostType.URGENT,
            title = "🚨 Urgente: necesitamos traslado",
            content = "Tenemos 5 cachorros que necesitan traslado urgente a un hogar temporal este fin de semana.",
            imageUrl = Images.FEED_1,
            location = "CABA",
            date = "Hace 2 horas",
            likes = 45,
            comments = 12
        ),
        FeedPost(
            id = "feed_2",
            authorId = "user_1",
            authorName = "María González",
            type = PostType.GENERAL,
            title = "Día de parque con Luna",
            content = "Hoy Luna conoció a muchos amigos en el parque. ¡Qué feliz estaba!",
            imageUrl = Images.FEED_2,
            location = "Palermo, CABA",
            date = "Hace 5 horas",
            likes = 23,
            comments = 5
        ),
        FeedPost(
            id = "feed_3",
            authorId = "user_3",
            authorName = "Carlos Ruiz",
            type = PostType.LOST_FOUND,
            title = "Perro encontrado en La Plata",
            content = "Encontré un golden retriever cerca de la estación. Tiene collar rojo sin placa.",
            imageUrl = Images.FEED_3,
            location = "La Plata",
            date = "Ayer",
            likes = 67,
            comments = 18
        ),
        FeedPost(
            id = "feed_4",
            authorId = "user_2",
            authorName = "Refugio Patitas",
            type = PostType.ADOPTION,
            title = "Rocky busca familia",
            content = "Rocky es un labrador de 2 años, super sociable. Ideal para familias con niños.",
            location = "CABA",
            date = "Hace 1 día",
            likes = 89,
            comments = 24
        ),
        FeedPost(
            id = "feed_5",
            authorId = "user_2",
            authorName = "Refugio Patitas",
            type = PostType.GENERAL,
            title = "Jornada de castraciones gratuitas",
            content = "Este sábado realizamos castraciones gratuitas. Inscribite enviando un mensaje.",
            location = "Villa Crespo, CABA",
            date = "Hace 2 días",
            likes = 112,
            comments = 31
        )
    )

    val adoptionPosts = listOf(
        AdoptionPost(
            id = "adopt_1",
            shelterId = "shelter_1",
            shelterName = "Refugio Patitas",
            name = "Rocky",
            photoUrl = Images.DOG_2,
            species = PetSpecies.DOG,
            sex = PetSex.MALE,
            ageYears = 2,
            size = PetSize.LARGE,
            location = "CABA",
            description = "Labrador sociable y enérgico. Ideal para familias activas.",
            status = AdoptionStatus.AVAILABLE
        ),
        AdoptionPost(
            id = "adopt_2",
            shelterId = "shelter_1",
            shelterName = "Refugio Patitas",
            name = "Mimi",
            photoUrl = Images.CAT_2,
            species = PetSpecies.CAT,
            sex = PetSex.FEMALE,
            ageYears = 0,
            ageMonths = 8,
            size = PetSize.SMALL,
            location = "CABA",
            description = "Gatita tímida pero muy cariñosa una vez que te conoce.",
            status = AdoptionStatus.AVAILABLE
        ),
        AdoptionPost(
            id = "adopt_3",
            shelterId = "shelter_2",
            shelterName = "Huellitas Felices",
            name = "Toby",
            photoUrl = Images.DOG_3,
            species = PetSpecies.DOG,
            sex = PetSex.MALE,
            ageYears = 5,
            size = PetSize.MEDIUM,
            location = "La Plata",
            description = "Mestizo tranquilo, perfecto para departamento.",
            status = AdoptionStatus.IN_PROCESS
        ),
        AdoptionPost(
            id = "adopt_4",
            shelterId = "shelter_2",
            shelterName = "Huellitas Felices",
            name = "Nina",
            photoUrl = Images.CAT_3,
            species = PetSpecies.CAT,
            sex = PetSex.FEMALE,
            ageYears = 3,
            size = PetSize.SMALL,
            location = "La Plata",
            description = "Gata adulta, independiente y limpia.",
            status = AdoptionStatus.ADOPTED
        ),
        AdoptionPost(
            id = "adopt_5",
            shelterId = "shelter_1",
            shelterName = "Refugio Patitas",
            name = "Simba",
            photoUrl = Images.DOG_4,
            species = PetSpecies.DOG,
            sex = PetSex.MALE,
            ageYears = 1,
            size = PetSize.MEDIUM,
            location = "CABA",
            description = "Cachorro mestizo lleno de energía. Necesita paciencia y entrenamiento.",
            status = AdoptionStatus.AVAILABLE
        )
    )

    val shelters = listOf(
        Shelter(
            id = "shelter_1",
            name = "Refugio Patitas",
            photoUrl = Images.SHELTER_1,
            location = "Villa Crespo, CABA",
            description = "Refugio sin fines de lucro con más de 10 años rescatando animales.",
            contactPhone = "+54 11 4567-8901",
            contactEmail = "contacto@patitas.org",
            adoptionPetIds = listOf("adopt_1", "adopt_2", "adopt_5"),
            needs = listOf(
                ShelterNeed("Alimento para perros", "20 kg"),
                ShelterNeed("Alimento para gatos", "10 kg"),
                ShelterNeed("Mantas", "15 unidades"),
                ShelterNeed("Medicamentos antipulgas", "10 pipetas")
            )
        ),
        Shelter(
            id = "shelter_2",
            name = "Huellitas Felices",
            photoUrl = Images.SHELTER_2,
            location = "Centro, La Plata",
            description = "Organización de rescatistas voluntarios en La Plata y alrededores.",
            contactPhone = "+54 221 456-7890",
            contactEmail = "info@huellitas.org",
            adoptionPetIds = listOf("adopt_3", "adopt_4"),
            needs = listOf(
                ShelterNeed("Transportadoras", "5 unidades"),
                ShelterNeed("Correas y collares", "20 unidades")
            )
        ),
        Shelter(
            id = "shelter_3",
            name = "Rescate Animal BA",
            location = "San Telmo, CABA",
            description = "Red de rescatistas que atiende casos urgentes en CABA.",
            contactPhone = "+54 11 9876-5432",
            adoptionPetIds = emptyList(),
            needs = listOf(
                ShelterNeed("Voluntarios para traslados", "Fines de semana"),
                ShelterNeed("Hogares temporales", "Urgente")
            )
        )
    )

    val lostFoundPosts = listOf(
        LostFoundPost(
            id = "lf_1",
            authorId = "user_3",
            authorName = "Carlos Ruiz",
            type = LostFoundType.FOUND,
            petName = null,
            species = PetSpecies.DOG,
            photoUrl = Images.DOG_5,
            location = "Estación La Plata",
            description = "Golden retriever encontrado con collar rojo sin placa. Muy dócil.",
            contactInfo = "carlos@email.com / 221-4567890",
            date = "28/06/2025"
        ),
        LostFoundPost(
            id = "lf_2",
            authorId = "user_1",
            authorName = "María González",
            type = LostFoundType.LOST,
            petName = "Pelusa",
            species = PetSpecies.CAT,
            photoUrl = Images.CAT_1,
            location = "Palermo, CABA",
            description = "Gata blanca con manchas grises. Ojos verdes. Muy asustadiza.",
            contactInfo = "maria@email.com / 11-1234-5678",
            date = "27/06/2025"
        ),
        LostFoundPost(
            id = "lf_3",
            authorId = "user_2",
            authorName = "Refugio Patitas",
            type = LostFoundType.FOUND,
            petName = null,
            species = PetSpecies.DOG,
            location = "Villa Crespo, CABA",
            description = "Perro mestizo pequeño, color marrón. Sin collar.",
            contactInfo = "contacto@patitas.org",
            date = "26/06/2025"
        )
    )
}
