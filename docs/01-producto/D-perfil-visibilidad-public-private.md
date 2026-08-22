# LeoVer — Visibilidad de perfil social: PUBLIC / PRIVATE

**Fecha:** 2026-08-16  
**Ámbito:** M02 perfil + M19 superficie social  
**Estado:** decisión de producto vigente

## Decisión

La visibilidad del **perfil social** tiene exactamente dos valores activos:

| Producto | Canonical `persons.privacy_state` |
| --- | --- |
| **PUBLIC** | `PUBLIC_LIMITED` |
| **PRIVATE** | `PRIVATE` |

**Solo amigos / FRIENDS / FRIENDS_ONLY** no es un valor de producto activo. Cualquier resto legado se trata como **PRIVATE**.

No existe una relación **AMIGO**. El grafo es **seguir / seguido** (conexiones requester/addressee ya existentes). Si el perfil es PRIVATE, seguir requiere solicitud y el dueño acepta o rechaza.

## Qué significa PRIVATE

- Seguir requiere aprobación.
- Quien fue aprobado ve el perfil social / contenido social según las reglas de visibilidad social.
- Quien no fue aprobado ve solo la identidad pública mínima necesaria para interactuar en LeoVer.
- PRIVATE afecta **solo** la visibilidad del perfil social.

## Qué NO otorga ni restringe PRIVATE

PRIVATE no cambia automáticamente:

- acceso VitaCora
- responsabilidad de mascota
- permisos de mascota
- Health
- familia / holders
- mensajes
- membresías de organización
- ubicación precisa
- acceso de tutor / guardian

Esos dominios tienen sus propios contratos.

## Esquema

Canonical 1002 ya define `privacy_state in ('PRIVATE', 'PUBLIC_LIMITED')`. No hay `FRIENDS_ONLY` en Staging canónico. No se requiere migración forward para esta decisión. No se modifican migraciones 1000–1025.

## Runtime AUTH-02 / AUTH-03

Onboarding y perfil social exponen solo **Público** / **Privado**. El picker “Solo amigos” se eliminó.

Copy de onboarding:

- Público: “Cualquier persona puede ver tu perfil social y el contenido que publiques como público.”
- Privado: “Solo los seguidores que apruebes pueden ver tu perfil social privado.”
- Nota: “Esta configuración no modifica la privacidad de VitaCora ni la información de tus mascotas.”

`canon_update_my_person` persiste `display_name` y `home_locality_id`. La visibilidad social no forma parte de ese contrato; el valor canónico activo sigue siendo `privacy_state` = `PRIVATE` | `PUBLIC_LIMITED`.
