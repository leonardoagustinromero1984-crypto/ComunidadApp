# M20 Bloque 4 — Validación remota

**Fecha:** 2026-08-02  
**Script:** `scripts/ops/m20_remote_validation_062_063.sql`  
**Entorno:** Supabase staging `wystsapjfpdtoprlmizz`

## Resultado

| Rango | Casos | Resultado |
|-------|-------|-----------|
| 01–25 Estructural | 25 | PASS |
| 26–55 RLS/permisos | 30 | PASS |
| 56–85 Operaciones | 30 | PASS |
| 86–105 Privacidad | 20 | PASS |
| 106–125 Extensiones Bloque 3 | 20 | PASS |
| **Total** | **125** | **125/125 PASS** |

## Smoke remoto (01–25)

**Comando:** `supabase db query --linked -f scripts/ops/m20_smoke_remote_01_25.sql`  
**Fecha:** 2026-08-02 · **Resultado:** **25/25 PASS**

| # | Caso | Resultado |
|---|------|-----------|
| 1 | RPC list conversations callable | PASS |
| 2 | Create direct idempotente | PASS |
| 3 | Send texto | PASS |
| 4 | Send attachment-only | PASS |
| 5 | client_message_id idempotente | PASS |
| 6 | Reply en hilo | PASS |
| 7 | Edit mensaje | PASS |
| 8 | Delete lógico | PASS |
| 9 | Mark read cursor | PASS |
| 10 | Archive per-participante | PASS |
| 11 | Block / unblock | PASS |
| 12 | Paginación cursor | PASS |
| 13 | Context hint PET | PASS |
| 14 | Context CAMPAIGN route | PASS |
| 15 | Privacidad sin sender_user_id | PASS |
| 16 | Adjunto private:// filtrado | PASS |
| 17 | Placeholder eliminado | PASS |
| 18 | conversation_type en JSON | PASS |
| 19 | message_type en JSON | PASS |
| 20 | Effective ARCHIVED actor | PASS |
| 21 | Peer ACTIVE tras archive actor | PASS |
| 22 | Permiso denegado ajeno | PASS |
| 23 | Mensaje vacío rechazado | PASS |
| 24 | Script tag rechazado | PASS |
| 25 | M06 no bloquea ruta SQL | PASS |

## Tests Kotlin focalizados

- `M20MessagingFoundationTest` — 11/11 PASS
- `M20MessagingOperationsTest` — 25/25 PASS
- `M20MessagingRemoteMapperTest` — 10/10 PASS

## Compilación

`compileLocalDebugKotlin` — PASS (Bloque 4)

## Producción

No afectada.

## Veredicto

```text
M20 BLOQUE 4 CERRADO — MÓDULO M20 CERRADO OFICIALMENTE
```
