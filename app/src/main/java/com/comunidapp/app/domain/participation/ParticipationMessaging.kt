package com.comunidapp.app.domain.participation

object ParticipationMessaging {
    fun campaignOffer(campaignTitle: String, offerSummary: String): String =
        "Hola, quiero colaborar con $offerSummary en la campaña $campaignTitle."

    fun eventRegistration(eventTitle: String): String =
        "Me inscribí al evento $eventTitle."

    fun idempotencyKey(kind: String, actorId: String, targetId: String): String =
        "$kind:$actorId:$targetId"
}
