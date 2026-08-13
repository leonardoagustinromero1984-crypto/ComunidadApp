# KMP-IOS — Bloque 15 auditoría (Adoption media write)

Fuente: HEAD `128f691` + SQL M09 (sin WIP dirty).

## Decisión

**PET_SNAPSHOT_ONLY** — no hay upload multimedia propio en el flujo productivo M09.

## Evidencia

1. RPC `m09_create_adoption_publication` asigna `photo_url = v_pet.photo_url` (snapshot de mascota).
2. `AdoptionFormViewModel` / form productivo: sin FileAsset / M05 / URI de foto.
3. Existe `FileAssetPurpose.ADOPTION_MEDIA` y path template, pero el form M09 **no** lo usa (legacy `PublishViewModel` es otro camino).

## Resultado KMP-15

```
ADOPTION MEDIA WRITE = NOT_APPLICABLE_BY_CURRENT_BACKEND_CONTRACT
```

No se implementó gateway M05 de adopción. KMP-10 READ sigue resolviendo `photo_url` asset/HTTPS cuando existe.

No es bug: es contrato de producto/backend actual.
