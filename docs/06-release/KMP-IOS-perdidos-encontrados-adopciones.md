# KMP-IOS — Perdidos / Encontrados / Adopciones (Bloque 4)

## Vertical

```text
LeoVerSharedApp
  Home → Alertas → Perdidos|Encontrados → Detalle
       → Adopciones → Detalle
```

## Lost / Found

| Artefacto | Ubicación |
| --------- | --------- |
| `LostFoundCaseType` / `LostFoundCaseStatus` / rules | shared domain (KMP-1) |
| `LostFoundId`, Summary, Detail, Draft, validators | `shared/.../lostfound` |
| `LostFoundRepository` + `FakeLostFoundRepository` | commonMain |
| List filter ALL / LOST / FOUND | presentation |

Privacidad UI: `ApproximateLocation` (sin lat/lng), `publicCode`, publisher display seguro.
Sin: ownerId, userId, teléfono, email, coords.

## Adopciones

| Artefacto | Ubicación |
| --------- | --------- |
| `AdoptionListingStatus` / rules | shared domain (KMP-1) |
| `AdoptionId`, Summary, Detail, Draft | `shared/.../adoption` |
| `AdoptionRepository` + fake | commonMain |

CTA postulación: deshabilitado (“próximamente”) — sin backend nuevo.

## Datos iOS

| Capa | Modo |
| ---- | ---- |
| Sesión | SESSION_STUB |
| Perfil / mascotas | SHARED_FAKE |
| Lost/Found | SHARED_FAKE |
| Adopciones | SHARED_FAKE |

## Brechas

- Supabase REAL_REMOTE KMP — DEFERRED
- M09 Android posts/decoding — no tocado
- Cámara / GPS / publicación remota — DEFERRED
- Applications / completion — ANDROID_ONLY
