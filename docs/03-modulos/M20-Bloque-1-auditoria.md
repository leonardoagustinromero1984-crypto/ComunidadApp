# M20 — Auditoría Bloque 1 (Mensajería)

## 1. Contexto inicial

Referencia al cierre M19 Bloques 1–3 y estado previo a M20 Bloque 1.

## 2. Estado Git inicial

- M19 Bloques 1–3 implementados localmente
- M20 no iniciado antes de este bloque
- Sin migración SQL para mensajería

## 3. Nombre oficial vs alcance del bloque

| Fuente | Nombre |
|--------|--------|
| **D01 (autoritativo)** | **M20 Mensajería — conversaciones, adjuntos, bloqueos, contexto y retención** |
| Prompt Bloque 1 | Lista de conversaciones, hilo, envío texto, bloqueo stub, context hints |

**Decisión:** conservar número **M20** y nombre D01. Bloque 1 implementa fundación local/mock (sin adjuntos upload ni SQL).

## 4. Documentos revisados

- `docs/01-producto/D01-Modulos-y-Orden.md`
- Patrones M19 Bloque 1 (modelos, repositorio mock, navegación, Comunidad)
- `docs/03-modulos/M06-Notificaciones.md` (M20 propietario del chat)
- Código focal: `ChatModels.kt` legacy (no reutilizado), `DataProvider.kt`, `NavRoutes.kt`

## 5. Auditoría M01 / M02

| Aspecto | Autoridad | Uso M20 |
|---------|-----------|---------|
| Sesión actual | M01 AuthRepository | Actor `mock_user_admin` en seeds |
| Identidad usuario | M01 userId | `participantUserIds` / `senderUserId` internos; no expuestos en público |
| Permisos plataforma | M02 roles | `messaging.view`, `messaging.send`, `messaging.block` (contrato) |

## 6. Auditoría M03 / M08 / M18

| Aspecto | Hallazgo | M20 |
|---------|----------|-----|
| Contexto organización | M03 org snapshots | `M20ContextReferenceType.ORGANIZATION` |
| Contexto mascota | M08 pet | `M20ContextReferenceType.PET` |
| Contexto evento | M18 event | `M20ContextReferenceType.EVENT` |
| Resolver mock | Patrón M19 | `M20ContextHintResolver` |

## 7. Relación M06 / M19

- M06 notifica; M20 es propietario de conversaciones
- M19 feed público; M20 mensajes privados 1:1 (mock)
- Sin duplicar cola de moderación en Bloque 1

## 8. Alcance explícito excluido

- SQL / migraciones Supabase (Bloque 2)
- Upload de adjuntos M05 (solo referencia `attachmentRef`)
- Retención / políticas de borrado avanzadas
- Chat legacy `ChatSupabaseDataSource` — no migrado en B1

## 9. Veredicto auditoría

```text
M20 BLOQUE 1 — FUNDACIÓN LOCAL VIABLE
PATRÓN M19 REUTILIZADO
CONVERSACIONES CON CONTEXTO M08/M03/M18
SIN SQL
SIN UPLOAD ADJUNTOS
```
