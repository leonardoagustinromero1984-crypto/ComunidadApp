# KMP-IOS — Bloque 10 validación

**HEAD base KMP-9.2:** `290550324a4365f6b6e25ca21d055997d296a2a6`

**Gate #13 (previo):** PASS — framework + test link iOS simulator.

## Alcance

M05 MEDIA READ REAL_REMOTE shared + display CMP:

- Lost list/detail
- Found list/detail
- Pets / Adoptions cuando contrato lo permite
- Profile avatar PARTIAL si solo `avatar_path`

## Windows (regresión)

| Check | Esperado |
| ----- | -------- |
| `:shared:testAndroidHostTest` | 192 + nuevos PASS |
| `compileLocalDebugKotlin` | BUILD SUCCESSFUL |

## Cloud (definitivo)

compile/link iOS simulator + device + Xcode launch.

CI **no** prueba lectura real Supabase sin credentials.

## KMP-9 cierre

| Item | SHA / estado |
| ---- | ------------ |
| KMP-9 impl | `0ef16f532659502ce0ccfda82bb37aebdde96507` |
| KMP-9.1 | `9287e83e2afdaac83867a1d9ac8f87d7dbcdcd4e` |
| KMP-9.2 | `290550324a4365f6b6e25ca21d055997d296a2a6` |
| Gate #11 | FAIL framework cache |
| Gate #12 | FAIL test cache |
| Gate #13 | PASS |
| KMP-9 | **CLOSED GREEN** |
