# Inventario de legado — Leover

**Fecha:** 2026-07-14  
**Etapa:** M00 Etapa 2  
**Regla:** en esta etapa **no eliminar** archivos funcionales ni hacer limpiezas irreversibles.

## 1. Resumen

| Categoría | Acción recomendada |
|-----------|-------------------|
| Docs Firebase / Firestore | Conservar → mover a `99-legacy/` en commit futuro documentado |
| Roadmap pre-D01 | Conservar en raíz hasta enlazar desde D01; marcar como histórico |
| `firebase.json` / rules | Conservar hasta confirmar que ningún script de deploy las usa |
| FCM / `google-services.json` | **En uso** — no tratar como legado |
| Código Firestore Kotlin | No existe en `app/` — solo docs/planes |

---

## 2. Documentos en `docs/` (raíz)

| Archivo | ¿Vigente? | Recomendación |
|---------|-----------|---------------|
| `supabase-setup.md` | Sí | Conservar (setup operativo) |
| `leover-push-fcm-setup.md` | Sí | Conservar |
| `supabase-email-templates.md` | Sí | Conservar |
| `leover-documento-funcional.md` | Parcial | Conservar; superado en parte por Maestro/D01 |
| `leover-requirements.md` | Histórico útil | Conservar; mapear a módulos luego |
| `leover-roadmap-implementacion.md` | Histórico | Conservar; **fuente de orden = D01** |
| `leover-public-api-stub.md` | Futuro M27 | Conservar |
| `qa-checklist-phase0.md` | Histórico | Conservar |
| `leover-phase0-implementation.md` | **Legado Firebase** | Mover a `99-legacy/` en commit separado |
| `leover-firestore-model.md` | **Legado** | Mover a `99-legacy/` en commit separado |
| `firebase-setup.md` | **Legado** (backend Firebase) | Mover a `99-legacy/`; FCM documentado en push-fcm-setup |

**Movimiento propuesto (no ejecutado en Etapa 2):**

```text
docs/leover-phase0-implementation.md  → docs/99-legacy/
docs/leover-firestore-model.md        → docs/99-legacy/
docs/firebase-setup.md                → docs/99-legacy/
```

Actualizar enlaces en `docs/README.md` al mover.

---

## 3. Archivos Firebase en la raíz del repo

| Archivo | Referencias en código app | Recomendación |
|---------|---------------------------|---------------|
| `.firebaserc` | No (config CLI) | Conservar temporalmente; verificar CI/scripts |
| `firebase.json` | No | Conservar; apunta a rules Firestore/Storage |
| `firestore.rules` | No Kotlin | Legado de datos; no borrar sin commit documentado |
| `storage.rules` (Firebase) | No Kotlin | Idem |
| `app/google-services.json` | Sí (plugin Google Services + FCM) | **Mantener** mientras repo privado |

Verificación: búsqueda de `firestore` / `FirebaseStorage` en `*.kt` → **sin uso** de Firestore/Storage SDK; solo messaging.

---

## 4. Elementos todavía utilizados

- `firebase-messaging` (Gradle)
- `LeoverFirebaseMessagingService`
- `PushTokenRegistrar` + Edge Function `supabase/functions/push`
- Plugin `google-services`
- Deep link OAuth Supabase en `AndroidManifest`

---

## 5. Roadmaps / specs reemplazados

| Antes | Ahora |
|-------|--------|
| `leover-roadmap-implementacion.md` como orden | [D01](../01-producto/D01-Modulos-y-Orden.md) + specs en `03-modulos/` |
| Plan NestJS en M00 original | [ADR-0001](../adr/ADR-0001-Supabase-como-backend-principal.md) + [Etapa 2](../03-modulos/M00-Etapa-2-Documentacion-y-Gobierno.md) |
| Arquitectura Firebase Phase 0 | [arquitectura-inicial.md](../02-arquitectura/arquitectura-inicial.md) |

---

## 6. Próximos pasos seguros (fuera de Etapa 2)

1. Commit `chore: move firebase docs to 99-legacy` solo con los 3 md listados.  
2. Revisar si alguien despliega aún `firebase deploy` → si no, ADR corto + retirada de `firebase.json` / rules.  
3. Nunca mezclar esa limpieza con features de negocio.
