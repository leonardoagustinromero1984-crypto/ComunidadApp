package com.comunidapp.app.data.mock

import com.comunidapp.app.data.model.AccountType
import com.comunidapp.app.data.model.AdoptionEvent
import com.comunidapp.app.data.model.AdoptionPost
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.CommunityCategory
import com.comunidapp.app.data.model.CommunityListing
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.FosterHomeListing
import com.comunidapp.app.data.model.LostFoundPost
import com.comunidapp.app.data.model.LostFoundType
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.PetReminder
import com.comunidapp.app.data.model.PetSex
import com.comunidapp.app.data.model.PetSize
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.model.ServiceCategory
import com.comunidapp.app.data.model.ServiceProfile
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
        const val VET_1 = "https://picsum.photos/seed/vet1/400/300"
        const val TRAINER_1 = "https://picsum.photos/seed/trainer1/400/300"
        const val WALKER_1 = "https://picsum.photos/seed/walker1/400/300"
        const val SHOP_1 = "https://picsum.photos/seed/shop1/400/300"
        const val EVENT_1 = "https://picsum.photos/seed/event1/400/300"
        const val FOSTER_1 = "https://i.pravatar.cc/300?u=ana"
        const val FOSTER_2 = "https://i.pravatar.cc/300?u=luis"
    }

    val currentUser = User(
        id = "user_1",
        name = "María González",
        email = "maria@email.com",
        profileImageUrl = Images.USER,
        bio = "Amante de los animales. Adopté a Luna hace 2 años 🐾",
        locationText = "Buenos Aires, Argentina",
        petIds = listOf("pet_1", "pet_2"),
        username = "maria.demo",
        displayName = "María González",
        onboardingStatus = "COMPLETED",
        accountStatus = "ACTIVE",
        profilePrivate = false
    )

    val users = listOf(
        currentUser,
        User(
            id = "user_2",
            name = "Refugio Patitas",
            email = "contacto@patitas.org",
            accountType = AccountType.SHELTER,
            profilePrivate = false,
            bio = "Refugio sin fines de lucro. Rescatamos y damos en adopción.",
            locationText = "CABA, Argentina"
        ),
        User(
            id = "user_3",
            name = "Carlos Ruiz",
            email = "carlos@email.com",
            bio = "Rescatista voluntario",
            locationText = "La Plata, Argentina"
        ),
        User(
            id = "user_4",
            name = "PetLovers Boutique",
            email = "hola@petlovers.ba",
            accountType = AccountType.SHOP,
            profileImageUrl = Images.SHOP_1,
            bio = "Alimentos premium y accesorios. Envíos en CABA.",
            locationText = "Recoleta, CABA"
        ),
        User(
            id = "user_5",
            name = "Ana Martínez",
            email = "ana@email.com",
            profileImageUrl = Images.FOSTER_1,
            bio = "Hogar de tránsito para perros y gatos.",
            locationText = "Rosario, Santa Fe"
        ),
        User(
            id = "user_6",
            name = "Luis Fernández",
            email = "luis@email.com",
            profileImageUrl = Images.FOSTER_2,
            bio = "Paseador profesional. Amante de los perros grandes.",
            locationText = "Córdoba, Argentina"
        ),
        User(
            id = "user_7",
            name = "VetCare Centro",
            email = "info@vetcare.ar",
            accountType = AccountType.VET,
            bio = "Clínica veterinaria con guardia 24 hs.",
            locationText = "Palermo, CABA"
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
            locationText = "CABA",
            date = "Hace 2 horas",
            likeCount = 45,
            commentCount = 12
        ),
        FeedPost(
            id = "feed_2",
            authorId = "user_1",
            authorName = "María González",
            type = PostType.GENERAL,
            title = "Día de parque con Luna",
            content = "Hoy Luna conoció a muchos amigos en el parque. ¡Qué feliz estaba!",
            imageUrl = Images.FEED_2,
            locationText = "Palermo, CABA",
            date = "Hace 5 horas",
            likeCount = 23,
            commentCount = 5
        ),
        FeedPost(
            id = "feed_3",
            authorId = "user_3",
            authorName = "Carlos Ruiz",
            type = PostType.LOST_FOUND,
            title = "Perro encontrado en La Plata",
            content = "Encontré un golden retriever cerca de la estación. Tiene collar rojo sin placa.",
            imageUrl = Images.FEED_3,
            locationText = "La Plata",
            date = "Ayer",
            likeCount = 67,
            commentCount = 18
        ),
        FeedPost(
            id = "feed_4",
            authorId = "user_2",
            authorName = "Refugio Patitas",
            type = PostType.ADOPTION,
            title = "Rocky busca familia",
            content = "Rocky es un labrador de 2 años, super sociable. Ideal para familias con niños.",
            locationText = "CABA",
            date = "Hace 1 día",
            likeCount = 89,
            commentCount = 24
        ),
        FeedPost(
            id = "feed_6",
            authorId = "user_1",
            authorName = "María González",
            type = PostType.QUESTION,
            title = "¿Qué alimento recomiendan para gato adulto?",
            content = "Michi tiene 2 años y quiero cambiar de marca. ¿Cuál usan ustedes?",
            locationText = "Palermo, CABA",
            date = "Hace 3 horas",
            likeCount = 8,
            commentCount = 14
        ),
        FeedPost(
            id = "feed_7",
            authorId = "user_4",
            authorName = "PetLovers Boutique",
            type = PostType.PROMO,
            title = "20% off en alimento premium",
            content = "Esta semana 20% de descuento en línea Natural Dog. Solo en local de Recoleta.",
            imageUrl = Images.SHOP_1,
            locationText = "Recoleta, CABA",
            date = "Hace 1 hora",
            likeCount = 34,
            commentCount = 6
        ),
        FeedPost(
            id = "feed_5",
            authorId = "user_2",
            authorName = "Refugio Patitas",
            type = PostType.GENERAL,
            title = "Jornada de castraciones gratuitas",
            content = "Este sábado realizamos castraciones gratuitas. Inscribite enviando un mensaje.",
            locationText = "Villa Crespo, CABA",
            date = "Hace 2 días",
            likeCount = 112,
            commentCount = 31
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

    val fosterHomes = listOf(
        FosterHomeListing(
            id = "foster_1",
            hostName = "Ana Martínez",
            photoUrl = Images.FOSTER_1,
            location = "Caballito, CABA",
            capacity = 2,
            acceptedSpecies = listOf(PetSpecies.DOG, PetSpecies.CAT),
            notes = "Tengo patio chico. Experiencia con cachorros y gatitos.",
            available = true,
            contactInfo = "ana@email.com"
        ),
        FosterHomeListing(
            id = "foster_2",
            hostName = "Luis Fernández",
            photoUrl = Images.FOSTER_2,
            location = "San Isidro, GBA",
            capacity = 1,
            acceptedSpecies = listOf(PetSpecies.DOG),
            notes = "Acepto perros medianos en recuperación post cirugía.",
            available = true,
            contactInfo = "luis@email.com / 11-5555-1234"
        ),
        FosterHomeListing(
            id = "foster_3",
            hostName = "Familia Pérez",
            location = "Quilmes, GBA",
            capacity = 3,
            acceptedSpecies = listOf(PetSpecies.CAT),
            notes = "Solo gatos. No convive con perros en la zona.",
            available = false,
            contactInfo = "contacto@familiaperez.org"
        )
    )

    val adoptionEvents = listOf(
        AdoptionEvent(
            id = "event_1",
            title = "Feria de adopción en Plaza Armenia",
            photoUrl = Images.EVENT_1,
            location = "Palermo, CABA",
            date = "Sábado 12/07 · 10 a 18 hs",
            organizerName = "Refugio Patitas",
            description = "Más de 30 perros y gatos buscando familia. Habrá veterinarios voluntarios y food trucks pet friendly.",
            contactInfo = "contacto@patitas.org"
        ),
        AdoptionEvent(
            id = "event_2",
            title = "Jornada Adopta un Amigo",
            location = "Paseo del Bosque, La Plata",
            date = "Domingo 20/07 · 11 a 17 hs",
            organizerName = "Huellitas Felices",
            description = "Adopciones responsables con entrevista previa. Traé DNI y compromiso de castración.",
            contactInfo = "info@huellitas.org"
        ),
        AdoptionEvent(
            id = "event_3",
            title = "Encuentro de familias adoptivas",
            location = "Villa Crespo, CABA",
            date = "Sábado 26/07 · 15 a 19 hs",
            organizerName = "Rescate Animal BA",
            description = "Charla sobre tenencia responsable + stands de refugios locales.",
            contactInfo = "+54 11 9876-5432"
        )
    )

    val communityListings = listOf(
        CommunityListing(
            id = "comm_vet_1",
            category = CommunityCategory.VET,
            name = "Clínica Veterinaria San Roque",
            photoUrl = Images.VET_1,
            location = "Almagro, CABA",
            description = "Atención 24 hs, cirugías, internación y vacunación.",
            contactInfo = "11-4444-5678",
            tags = listOf("Urgencias", "Castraciones")
        ),
        CommunityListing(
            id = "comm_vet_2",
            category = CommunityCategory.VET,
            name = "Dr. Pablo Sosa — Veterinario a domicilio",
            location = "Zona Norte GBA",
            description = "Consultas a domicilio para perros y gatos. Control anual y vacunas.",
            contactInfo = "pablo.vet@email.com",
            tags = listOf("A domicilio")
        ),
        CommunityListing(
            id = "comm_trainer_1",
            category = CommunityCategory.TRAINER,
            name = "K9 Educación Canina",
            photoUrl = Images.TRAINER_1,
            location = "Belgrano, CABA",
            description = "Adiestramiento positivo, paseos educativos y modificación de conducta.",
            contactInfo = "hola@k9edu.com",
            tags = listOf("Cachorros", "Ansiedad")
        ),
        CommunityListing(
            id = "comm_trainer_2",
            category = CommunityCategory.TRAINER,
            name = "Laura Gatti — Etología felina",
            location = "CABA y online",
            description = "Consultas de comportamiento para gatos en hogar.",
            contactInfo = "laura@felinos.com",
            tags = listOf("Gatos", "Online")
        ),
        CommunityListing(
            id = "comm_walker_1",
            category = CommunityCategory.WALKER,
            name = "Paseos Palermo",
            photoUrl = Images.WALKER_1,
            location = "Palermo y Colegiales",
            description = "Paseos individuales y grupales. Cobertura con GPS en cada salida.",
            contactInfo = "paseos@palermo.pet",
            tags = listOf("GPS", "Grupos reducidos")
        ),
        CommunityListing(
            id = "comm_walker_2",
            category = CommunityCategory.WALKER,
            name = "Maxi — Paseador certificado",
            location = "Villa Urquiza, CABA",
            description = "Paseos de 45 y 60 min. Experiencia con perros reactivos.",
            contactInfo = "11-2222-3333",
            tags = listOf("Reactivos")
        ),
        CommunityListing(
            id = "comm_shop_1",
            category = CommunityCategory.SHOP,
            name = "PetLovers Boutique",
            photoUrl = Images.SHOP_1,
            location = "Recoleta, CABA",
            description = "Alimentos premium, accesorios y productos sustentables.",
            contactInfo = "@petlovers.ba",
            tags = listOf("Delivery", "Natural")
        ),
        CommunityListing(
            id = "comm_shop_2",
            category = CommunityCategory.SHOP,
            name = "Cocina de Max — Comida casera para perros",
            location = "CABA",
            description = "Viandas cocinadas por nutricionista veterinaria. Planes semanales.",
            contactInfo = "pedidos@cocinademax.com",
            tags = listOf("Casero", "Por encargo")
        ),
        CommunityListing(
            id = "comm_donation_1",
            category = CommunityCategory.DONATION,
            name = "Campaña: 500 kg de alimento",
            location = "CABA y GBA",
            description = "Refugio Patitas necesita alimento para invierno. Punto de entrega en Villa Crespo.",
            contactInfo = "contacto@patitas.org",
            tags = listOf("Alimento", "Urgente")
        ),
        CommunityListing(
            id = "comm_donation_2",
            category = CommunityCategory.DONATION,
            name = "Voluntarios para traslados",
            location = "La Plata",
            description = "Huellitas Felices busca choferes los fines de semana para llevar mascotas al vet.",
            contactInfo = "info@huellitas.org",
            tags = listOf("Voluntariado", "Traslados")
        )
    )

    val serviceProfiles: List<ServiceProfile> = communityListings.mapNotNull { listing ->
        val category = ServiceCategory.fromCommunityCategory(listing.category) ?: return@mapNotNull null
        ServiceProfile(
            id = listing.id,
            ownerId = "mock_${listing.id}",
            category = category,
            name = listing.name,
            location = listing.location,
            description = listing.description,
            contactInfo = listing.contactInfo,
            photoUrl = listing.photoUrl,
            tags = listing.tags,
            scheduleText = "Lun a Vie 9–18 hs",
            priceFrom = when (category) {
                ServiceCategory.VET -> 15000.0
                ServiceCategory.TRAINER -> 12000.0
                ServiceCategory.WALKER -> 5000.0
                ServiceCategory.SHOP -> null
            },
            acceptsBookings = category != ServiceCategory.SHOP
        )
    }

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
