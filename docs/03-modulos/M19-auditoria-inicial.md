# M19 — Auditoría inicial (Bloque 1)

## 1. HEAD inicial

Referencia al cierre M18 y estado previo a M19 Bloque 1.

## 2. Estado Git inicial

- M18 Bloques 1–2 implementados localmente
- M19 no iniciado antes de este bloque
- Sin migración SQL para red social

## 3. Nombre oficial vs alcance del bloque

| Fuente | Nombre |
|--------|--------|
| **D01 (autoritativo)** | **M19 Red social y contenido** |
| Prompt Bloque 1 | Publicaciones, comentarios, reacciones, feed, reportes (M04) |

**Decisión:** conservar número **M19** y nombre D01. Bloque 1 implementa fundación local/mock de contenido social organizacional (sin mensajería privada ni algoritmo de feed avanzado).

## 4. Documentos revisados

- `docs/01-producto/D01-Modulos-y-Orden.md`
- Patrones M18 Bloque 1 (modelos, repositorio mock, navegación, Sumate)
- Código focal: M03 organizaciones, M04 moderación, M05 media refs, `DataProvider.kt`, `NavRoutes.kt`

## 5. Auditoría M01 / M02

| Aspecto | Autoridad | Uso M19 |
|---------|-----------|---------|
| Sesión actual | M01 AuthRepository | Actor `mock_user_admin` en seeds |
| Identidad usuario | M01 userId | `authorUserId` / `userId` internos; no expuestos en público |
| Permisos plataforma | M02 roles | Sin roles M19 paralelos |

## 6. Auditoría M03

| Aspecto | Hallazgo | M19 |
|---------|----------|-----|
| Organización autoritativa | `organizationId` M03 | **Toda publicación pertenece a org M03** |
| Tipos elegibles | SHELTER, RESCUE_GROUP, NGO, TRAINING_CENTER, VETERINARY_CLINIC | `M19_ELIGIBLE_ORGANIZATION_TYPES` |
| Permisos propuestos | — | `social.view`, `social.manage` |

## 7. Auditoría M04

- Reportes vía `M19SocialModerationAdapter` → cola M04 existente (`ModerationTargetType.OTHER`)
- Sin cola paralela ni duplicación de moderación
- `moderationStatus` interno en post

## 8. Alcance explícito excluido

- SQL / migraciones Supabase (Bloque 2)
- Mensajería directa / chat privado (M20)
- PII en modelos públicos
- Feed algorítmico avanzado

## 9. Veredicto auditoría

```text
M19 BLOQUE 1 — FUNDACIÓN LOCAL VIABLE
PATRÓN M18 REUTILIZADO
ORGANIZACIONES M03 COMO ANCLA
SIN SQL
SIN M20
```
