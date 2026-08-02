# M19 Bloque 4 — Validación remota

**Fecha:** 2026-08-02  
**Script:** `scripts/ops/m19_remote_validation_060_061.sql`  
**Entorno:** Supabase staging `wystsapjfpdtoprlmizz`

## Resultado

| Rango | Casos | Resultado |
|-------|-------|-----------|
| 01–25 Estructural | 25 | PASS |
| 26–55 RLS/permisos | 30 | PASS |
| 56–85 Operaciones | 30 | PASS |
| 86–105 Privacidad | 20 | PASS |
| **Total** | **105** | **105/105 PASS** |

## Smoke remoto (01–25)

**Comando:** `supabase db query --linked -f scripts/ops/m19_smoke_remote_01_25.sql`  
**Fecha:** 2026-08-02 · **Resultado:** **25/25 PASS**

| # | Caso | Resultado |
|---|------|-----------|
| 1 | RPC feed paginado callable (Supabase M19) | PASS |
| 2 | Entrada Comunidad → Red social | PASS (UI Bloque 3) |
| 3 | Eventos abre M18 (ruta separada) | PASS |
| 4 | Feed remoto carga | PASS |
| 5 | Primera página | PASS |
| 6 | Cursor `publishedAt\|postId` | PASS |
| 7 | Sin duplicados entre páginas | PASS |
| 8 | Orden estable mismo publishedAt | PASS |
| 9 | Crear post | PASS |
| 10 | Editar post | PASS |
| 11 | Publicar | PASS |
| 12 | Archivar | PASS |
| 13 | Crear comentario | PASS |
| 14 | Editar comentario | PASS |
| 15 | Archivar comentario | PASS |
| 16 | Reacción LIKE | PASS |
| 17 | Reacción LOVE | PASS |
| 18 | Toggle reacción | PASS |
| 19 | Conteos agregados | PASS |
| 20 | Referencias JSON públicas | PASS |
| 21 | Media privada filtrada | PASS |
| 22 | Moderación `m19_moderate_post` | PASS |
| 23 | Permiso denegado ajeno | PASS |
| 24 | Sin PII en JSON público | PASS |
| 25 | M06 no bloquea ruta SQL | PASS |

## Tests Kotlin focalizados

- `M19SocialFoundationTest` — PASS
- `M19SocialContentTest` — PASS
- `M19SocialRemoteMapperTest` — PASS

## Compilación

`compileLocalDebugKotlin` — PASS (Bloque 4)

## Producción

No afectada.
