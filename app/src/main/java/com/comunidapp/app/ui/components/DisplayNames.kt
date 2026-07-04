package com.comunidapp.app.ui.components

import com.comunidapp.app.data.model.AccountType

fun AccountType.toDisplayName(): String = when (this) {
    AccountType.PERSON -> "Persona"
    AccountType.SHELTER -> "Refugio / ONG"
    AccountType.VET -> "Veterinaria"
    AccountType.TRAINER -> "Educador / adiestrador"
    AccountType.WALKER -> "Paseador"
    AccountType.SHOP -> "Tienda / emprendimiento"
    AccountType.FOSTER_HOME -> "Hogar de tránsito"
}
