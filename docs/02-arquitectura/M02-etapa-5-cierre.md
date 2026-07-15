# M02 — Cierre Etapa 5: Validación staging, calidad y cierre

**Fecha:** 2026-07-15  
**Rama:** `m02/etapa-5-validacion-cierre`  
**Commit base verificado:** `87b8c07a0759696d8028f0583a86aa57c5333a15`  
**Spec:** `d6604d1` — docs Etapa 5  

---

## 1. Estado Git

| Momento | Estado |
|---------|--------|
| Inicial | HEAD `87b8c07`, working tree limpio salvo spec Etapa 5 untracked |
| Rama creada | `m02/etapa-5-validacion-cierre` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** realizado |
| M03 | **No** iniciado |

---

## 2. Archivos

### Creados

| Archivo |
|---------|
| `docs/03-modulos/M02-Etapa-5-Validacion-Staging-Calidad-y-Cierre-Final.md` |
| `docs/02-arquitectura/M02-etapa-5-cierre.md` (este) |
| `docs/02-arquitectura/M02-cierre-final.md` |
| `docs/04-calidad/M02-reporte-validacion-staging.md` |
| `app/src/test/.../M02Stage5GateSafetyTest.kt` |

### Modificados / eliminados

Ninguna migración 014–018 editada. Sin eliminaciones.

---

## 3. Revisión estática 014–018 (repo)

| Tema | Hallazgo |
|------|----------|
| Orden | 014 → 015 → 016 → 017 → 018 coherente |
| `search_path` | RPCs `SECURITY DEFINER` principales con `set search_path = public` |
| Grants | `revoke all` + `grant execute` a `authenticated` en RPCs |
| Client writes | `users` INSERT/UPDATE/DELETE revocados (016); assignments/history revocados (018) |
| PII pública | `get_public_user_profile` / search sin email/phone/modules |
| Storage | Path `users/{uid}/avatar/%`; bucket privado |
| Actor | `auth.uid()` en administración; sin reemplazo por target |
| Deny-by-default | `has_permission` false ante null/no ACTIVE/error |
| Autoelevación | Self assign/revoke/status forbidden |
| Último SUPERADMIN | Protegido en RPC revoke |
| service_role en Android | No encontrado |
| Deuda aceptada | Helpers SQL inmutables sin `search_path` (bajo riesgo); staging remoto pendiente |

**Corrección migratoria 019:** no requerida por defectos severos detectados en esta revisión.

---

## 4. Comandos y calidad local

```text
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | SUCCESS |
| `testDebugUnitTest` | **124** tests, **0** failures, **0** errors |
| `lintDebug` | SUCCESS (0 errores) |

---

## 5. Staging

**PENDIENTE DE VALIDACIÓN REMOTA** — sin acceso autorizado en esta sesión.  
Detalle por caso: `docs/04-calidad/M02-reporte-validacion-staging.md`.

**Release:** bloqueado hasta completar checklist staging.

---

## 6. Seguridad (confirmación código)

- [x] Sin service role en Android  
- [x] Actor = `auth.uid()`  
- [x] Deny-by-default  
- [x] Sin autoelevación (contrato SQL + tests mock)  
- [x] UPDATE sensible revocado (migración)  
- [x] Storage por path  
- [x] UI no es única barrera (RPC + permission check)  
- [x] Logs con sanitización existente (suite AppLogger)

---

## 7. Riesgos

1. Migraciones 014–018 solo en repo hasta staging.  
2. Bootstrap SUPERADMIN manual pendiente.  
3. Convivencia 014 trigger / consent si el remoto difiere del repo.

---

## 8. Checklist Etapa 5

- [x] Commit base verificado  
- [x] Rama limpia sin WIP  
- [x] Migraciones revisadas; no editadas  
- [x] Build / tests / lint OK  
- [x] Staging documentado como pendiente  
- [x] Release bloqueado si falta remoto  
- [x] Sin M03  
- [x] Tres documentos de salida creados  

---

## 9. Parada

Sin merge a `main`. Sin iniciar M03.
