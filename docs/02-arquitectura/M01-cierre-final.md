# M01 — Cierre final: Identidad y Autenticación

**Fecha:** 2026-07-14  
**Módulo:** M01 — Identidad y Autenticación  
**Estado:** Implementación de etapas 1–4 completa a nivel código y documentación; pendiente revisión humana y validación remota explícita.

Este documento cierra M01. **No inicia M02.**

---

## 1. Funcionalidades M01 terminadas (código)

| Área | Entrega |
|------|---------|
| Contratos | `AuthState`, `AuthUser`, comandos, validadores (password ≥ 8), `AppError` |
| Registro | Sin AccountType; checkboxes legales; metadata allowlisted |
| Login / verificación | Email/password; OTP/link; anti–doble envío |
| Consentimientos | Trigger 014 (local SQL); gate `LegalConsentRequired`; RPC `accept_legal_consents` |
| Recuperación | Email genérico; deep link recovery; `PasswordResetActive`; `updateUser` |
| Seguridad | Cambio de contraseña con reauth; logout + FCM unlink remoto |
| Eliminación | Matriz; Edge Function `delete-account`; UI con reauth + `ELIMINAR` |
| Mock | Flujos equivalentes; demo `maria@email.com` / `demo1234` |

---

## 2. Probado localmente

| Control | Resultado |
|---------|-----------|
| `testDebugUnitTest` | **83** tests, 0 failures |
| `assembleDebug` | SUCCESS |
| `lintDebug` | 0 errors |
| Suite M00+M01 previas | Conservadas |
| Nuevas Etapa 4 | Deep link, recovery, change/delete password, legal gate, security VM |

---

## 3. Probado en Supabase staging

| Ítem | Estado |
|------|--------|
| Migración `014_user_consents.sql` | **No aplicada** en remoto compartido |
| Trigger `handle_new_user` (corrigido) | Solo en repo; **requiere revisión antes de deploy** |
| Edge Function `delete-account` | Código en repo; **no deploy verificado** |
| Recovery email / deep link real | **No verificado** |
| Eliminación Auth + Storage real | **No verificado** |

Cualquier claim de éxito remoto queda fuera de este cierre.

---

## 4. Condiciones pendientes para release publicable

1. Textos de términos y privacidad **aprobados** (`publishable=true`, versiones finales).  
2. Migración 014 aplicada y validada en staging (RLS + trigger).  
3. Deploy y prueba de `delete-account` con cuenta de prueba.  
4. Flujo recovery end-to-end en dispositivo.  
5. Revisión legal/producto de matriz de eliminación ante módulos futuros (pagos, clínica).

---

## 5. Deuda aceptada

- Legales en borrador (debug OK; release bloqueado — D-M01-15).  
- Skip temporal del gate consent si `user_consents` no existe aún en remoto.  
- Retención formal de pagos / historias clínicas / denuncias: **diferida** a sus módulos.  
- OAuth, MFA, passkeys, biometría: fuera de M01.  
- Paquete `com.comunidapp.app` vs marca Leover (ADR-0006).  
- WIP GPS/mapas/pagos aislado en `wip/gps-mapas-pagos`.

---

## 6. Stack y límites respetados

- Backend: **solo Supabase** (Auth, Postgres/RLS, Storage, Edge Functions).  
- Sin Hilt, sin segundo backend, sin service_role en Android.  
- Sin GPS/mapas/pagos en esta rama.  
- Sin inicio de M02.

---

## 7. Documentación de etapa

| Etapa | Documento |
|-------|-----------|
| 1 | `M01-auditoria-inicial.md` |
| 2 | `M01-etapa-2-cierre.md` |
| 3 | `M01-etapa-3-cierre.md` |
| 4 | `M01-etapa-4-cierre.md` |
| Matriz | `M01-matriz-eliminacion-de-cuenta.md` |
| Calidad recovery/delete | `docs/04-calidad/M01-pruebas-recuperacion-y-eliminacion.md` |

---

## 8. Estado de M02

**M02 permanece bloqueado** hasta revisión/aprobación explícita de este cierre final y de los ítems remotos que el producto decida exigir antes del siguiente módulo.
