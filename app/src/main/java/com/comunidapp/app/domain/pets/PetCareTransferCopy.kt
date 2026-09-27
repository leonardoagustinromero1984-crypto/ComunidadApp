package com.comunidapp.app.domain.pets

/** User-facing care-transfer copy. Never says owner / propietario / dueño. */
object PetCareTransferCopy {
    const val SCREEN_TITLE = "Transferir VitaCora"
    const val RECEIVER_TITLE = "Solicitud de transferencia"
    const val DESCRIPTION =
        "Transferí el cuidado de esta mascota a otra persona u organización."
    const val SHARED_ACCESS_NOTE =
        "Las personas con las que actualmente compartís el cuidado perderán el acceso " +
            "cuando la transferencia sea aceptada."
    const val MEDIA_SECTION_TITLE = "Fotos y videos personales anteriores"
    const val MEDIA_SECTION_BODY =
        "Elegí si querés que el nuevo cuidador también pueda acceder a los archivos " +
            "multimedia personales guardados durante esta etapa de cuidado."
    const val SHARE_PERSONAL_MEDIA_LABEL =
        "Compartir también mis fotos y videos personales anteriores"
    const val HISTORY_ALWAYS_TRAVELS =
        "La historia de la mascota, sus datos principales, la información de salud y " +
            "los aportes profesionales siempre acompañan a su VitaCora."
    const val MEDIA_INCLUDED = "Incluidos"
    const val MEDIA_NOT_INCLUDED = "No incluidos"
    const val CONTINUE = "Continuar"
    const val CANCEL_REQUEST = "Cancelar solicitud"
    const val OUTGOING_STILL_YOURS =
        "continúa bajo tu cuidado hasta que la transferencia sea aceptada."
    const val MEMORIES_TITLE = "Mis recuerdos"
    const val MEMORIES_SUBTITLE =
        "Recuerdos de mascotas cuyo cuidado ya transferiste."
    const val MEMORIES_EMPTY =
        "Los recuerdos que conserves de mascotas que estuvieron bajo tu cuidado aparecerán acá."
    const val MEMORY_ONLY_YOU = "Solo vos"
    const val MEMORY_SHARED = "Compartido con VitaCora"
    const val MEMORY_UNASSIGNED = "Otros recuerdos"

    fun memoriesOfPet(petName: String): String {
        val name = petName.trim().ifBlank { "mascota" }
        return "Recuerdos de $name"
    }
    const val UNDER_THE_CARE_OF = "Bajo el cuidado de"
    const val ARCHIVE_TITLE = "Archivar mascota"
    const val ARCHIVE_BODY =
        "La mascota dejará de aparecer entre tus mascotas activas, pero su VitaCora y " +
            "sus registros se conservarán."

    fun confirmInitiate(targetName: String, petName: String, targetIsOrganization: Boolean): String {
        val actor = targetName.trim().ifBlank { if (targetIsOrganization) "la organización" else "esa persona" }
        val pet = petName.trim().ifBlank { "la mascota" }
        val base =
            "Cuando $actor acepte, $pet pasará a estar bajo su cuidado y podrá administrar su VitaCora."
        return if (targetIsOrganization) base else "$base $SHARED_ACCESS_NOTE"
    }

    fun incomingRequest(sourceName: String, petName: String): String {
        val actor = sourceName.trim().ifBlank { "Alguien" }
        val pet = petName.trim().ifBlank { "esta mascota" }
        return "$actor quiere transferirte el cuidado de $pet."
    }

    fun mediaShareLine(included: Boolean): String =
        "$MEDIA_SECTION_TITLE:\n${if (included) MEDIA_INCLUDED else MEDIA_NOT_INCLUDED}"

    fun acceptConfirmTitle(petName: String): String {
        val pet = petName.trim().ifBlank { "esta mascota" }
        return "¿Aceptar el cuidado de $pet?"
    }

    fun acceptConfirmBody(petName: String): String {
        val pet = petName.trim().ifBlank { "la mascota" }
        return "$pet pasará a estar bajo tu cuidado y podrás administrar su VitaCora. " +
            "Su historia, datos principales, información de salud y aportes profesionales se conservarán."
    }

    fun nowUnderYourCare(petName: String): String {
        val pet = petName.trim().ifBlank { "La mascota" }
        return "$pet ahora está bajo tu cuidado."
    }

    fun outgoingWaiting(targetName: String): String {
        val target = targetName.trim().ifBlank { "La otra persona" }
        return "$target todavía no aceptó."
    }

    fun outgoingStillYours(petName: String): String {
        val pet = petName.trim().ifBlank { "La mascota" }
        return "$pet $OUTGOING_STILL_YOURS"
    }

    fun describeTransfer(petName: String): String {
        val pet = petName.trim().ifBlank { "esta mascota" }
        return "Transferí el cuidado de $pet a otra persona u organización."
    }

    fun incomingAcceptPerson(petName: String): String {
        val pet = petName.trim().ifBlank { "la mascota" }
        return "$pet pasará a estar bajo tu cuidado y podrás administrar su VitaCora."
    }

    fun incomingAcceptOrganization(orgName: String, petName: String): String {
        val org = orgName.trim().ifBlank { "la organización" }
        val pet = petName.trim().ifBlank { "la mascota" }
        return "Si aceptás en nombre de $org, $pet pasará a estar bajo su cuidado y podrá administrar su VitaCora."
    }

    fun underTheCareOf(actorName: String?): String {
        val name = actorName?.trim().orEmpty()
        return if (name.isNotEmpty()) "$UNDER_THE_CARE_OF\n$name" else "$UNDER_THE_CARE_OF\nno disponible"
    }

    fun createdHistory(petName: String): String {
        val pet = petName.trim().ifBlank { "la mascota" }
        return "Se creó la VitaCora de $pet."
    }

    fun transferredHistory(petName: String, actorName: String): String {
        val pet = petName.trim().ifBlank { "la mascota" }
        val actor = actorName.trim().ifBlank { "un nuevo cuidador" }
        return "$pet pasó a estar bajo el cuidado de $actor."
    }
}
