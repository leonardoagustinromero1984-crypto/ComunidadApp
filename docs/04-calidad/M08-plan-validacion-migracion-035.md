# M08 — Plan de validación migración 035

**Producto:** LeoVer  
**Etapa:** 3A freeze (plan); ejecución en **3B/local/staging** tras crear SQL.

---

## 1. Criterios globales PASS/FAIL

| Check | PASS | FAIL |
|---|---|---|
| Archivo `035_*.sql` único | 1 archivo, número 035 | gap/dup |
| `db reset --local` ×2 | exit 0 | error apply |
| `db lint --local --fail-on error` | exit 0 | errors |
| Backfill principals | count = pets con owner legacy | mismatch |
| Soft-unique microchip | crea OK | dups ACTIVE |
| RLS | matriz casos OK | fuga SELECT-all |
| Grants | sin PUBLIC EXECUTE en DEFINER internos | grant residual |
| Tests Android | ≥584, 0 fail | regressions |
| Staging apply | solo con auth | push accidental |

---

## 2. Local — orden

1. `supabase stop` / `start` (o ignore-health-check si storage flaky).  
2. `supabase db reset --local` (001–035).  
3. Lint.  
4. Repetir reset+lint (reproducibilidad).  
5. Scripts SQL de verificación (read-only asserts).  
6. `m08_stage2` + futuros `m08_stage3b` quality.  
7. Gradle: test / assemble / lint / jacoco **secuencial**.

---

## 3. Pruebas de backfill

- Toda pet con `owner_id` tiene exactamente 1 PRINCIPAL ACTIVE person.  
- `starts_at` = `created_at`.  
- Re-ejecutar backfill idempotente (0 inserts nuevos).  
- `microchip_normalized` coherente con normalizer.  
- Lista pets `owner_id IS NULL` post-035 solo si principal org (ninguna en backfill inicial).  
- `photo_url` intacto; `avatar_file_asset_id` null.

---

## 4. Pruebas RLS

Fixtures: users A (principal), B (co), C (auth READ), D (stranger), staff ADMIN.

| Caso | Esperado |
|---|---|
| D SELECT pet A | 0 rows |
| A SELECT | 1 |
| C SELECT | 1 si grant READ |
| D UPDATE | fail |
| A UPDATE name | OK vía policy/RPC |
| A UPDATE owner_id directo | PET_OWNER_ID_DIRECT_FORBIDDEN |
| INSERT responsibility directo | fail |
| History INSERT directo | fail |

---

## 5. Grants

- `has_function_privilege('anon', m08_*, EXECUTE)` false para RPC e internos.  
- `_m08_*` solo service_role.  
- authenticated EXECUTE en RPC cliente true.

---

## 6. RPC

| RPC | Caso | Esperado |
|---|---|---|
| create | A crea | pet+principal+history+event |
| assign co | A→B | ACTIVE co |
| assign dup | segunda | error duplicate |
| initiate transfer | A→B | PENDING unique |
| segunda PENDING | error | |
| accept | B | principal B; owner_id=B; A SUPERSEDED |
| reject/cancel | | estados terminales |
| expire | past expires_at | EXPIRED |
| deceased | | status; auths revoked; pending cancelled |
| archive/restore | | transitions |
| transfer deceased | | reject |
| set avatar | asset PET purpose | OK; wrong purpose fail |
| duplicates | same chip ACTIVE | detect / unique violation |

---

## 7. owner_id

- Tras accept person: owner_id = to_person.  
- Tras accept org (si habilitado): owner_id NULL.  
- Trigger bloquea UPDATE owner_id sin GUC.

---

## 8. Microchip

- Normalize empty → NULL (múltiples NULL OK).  
- Dos ACTIVE mismo chip → índice rechaza.  
- DECEASED puede reutilizar chip.

---

## 9. Integración Kotlin

- Etapa 2 tests siguen PASS.  
- No requerir wiring repos en 3B inicial salvo smoke opcional.  
- UI legacy: MyPets sigue listando por owner_id mientras principal persona.

---

## 10. Staging

- Backup schema/data/roles.  
- Dry-run solo 035.  
- Apply con auth.  
- Lint remoto 0 errors.  
- Matriz SQL ampliada o script m08 verify.  
- **No** producción.

---

## 11. Rollback rehearsal (staging only)

1. Snapshot.  
2. Apply 035.  
3. Simular forward-fix (no down).  
4. Documentar tiempo restore snapshot.

---

## 12. Criterio autorización Etapa 3B

Este plan + freeze 3A aprobados + estrategia dups microchip firmada.
