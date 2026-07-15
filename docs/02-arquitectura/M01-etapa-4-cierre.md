# M01 — Cierre Etapa 4: Recuperación, seguridad y eliminación de cuenta

**Fecha:** 2026-07-14  
**Rama:** `m01/etapa-4-recuperacion-seguridad-eliminacion`  
**Módulo:** M01 — Identidad y Autenticación  
**Estado de entrada:** Etapa 3 aprobada (`e2c88b5`)  
**Spec base en rama:** `6a4ee8b` — docs Etapa 4  
**WIP GPS/mapas/pagos:** **No** incorporado  

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Etapa 3 consolidada | `e2c88b5` |
| Spec Etapa 4 | `6a4ee8b` |
| Implementación Etapa 4 | working tree al cierre (pendiente commit de revisión) |
| Base SHA trabajo | `6a4ee8b` |

---

## 2. Corrección de consentimientos (D-M01-10)

| Ítem | Estado |
|------|--------|
| `014_user_consents.sql` reescrita | Siempre crea `public.users`; consentimiento solo con metadata válida; **no inventa** aceptación; **no falla** admin/invite |
| RPC `accept_legal_consents` | `auth.uid()`; sin `user_id` libre |
| `LegalConsentRequired` | Estado + UI gate |
| Consulta consentimiento vigente | Mock real; Supabase query (si tabla ausente → skip gate documentado) |
| Aplicación remota de 014 | **No aplicada** — requiere revisión explícita del trigger |

---

## 3. Deep links y reset (D-M01-11)

| Ítem | Estado |
|------|--------|
| `AuthDeepLinkParser` | Distingue recovery vs confirmación; consume URI una vez |
| `MainActivity` | `handleDeeplinks` + consume + notifica `SessionViewModel` |
| `PasswordResetActive` | Pantalla + `updateUser` (SDK) / mock recovery session |
| Stub `PASSWORD_RESET_NOT_AVAILABLE` | Solo si no hay sesión de recovery |

---

## 4. Seguridad y logout (D-M01-12)

| Ítem | Estado |
|------|--------|
| Pantalla Seguridad | Cambio de contraseña + eliminación (Perfil → Seguridad) |
| Reautenticación | Contraseña actual vía `signIn` / mock |
| Logout | `signOut` + limpieza estado; FCM unlink en remoto (`deleteDeviceTokens`) |

---

## 5. Matriz y Edge Function (D-M01-13/14)

| Entregable | Ruta |
|------------|------|
| Matriz | `docs/02-arquitectura/M01-matriz-eliminacion-de-cuenta.md` |
| Edge Function | `supabase/functions/delete-account/index.ts` |
| Requests | `account_deletion_requests` en 014 |
| Android | POST con JWT; **sin** `user_id` autoridad; **sin** service_role |

Orden función: request pending → Storage `users/{uid}` → device_tokens → Auth delete → completed.

---

## 6. Migraciones / RLS

| Objeto | Nota |
|--------|------|
| `user_consents` | SELECT propio; INSERT vía trigger/RPC |
| `account_deletion_requests` | SELECT propio; write service role |
| 014 en cloud | **Pendiente aprobación** |

---

## 7. Pruebas

| Suite | Resultado |
|-------|-----------|
| Unitarias totales | **83** (0 failures) |
| Nuevas | Deep link, mock recovery/change/delete, security VMs, legal gate |
| `assembleDebug` | SUCCESS (corrida previa en etapa) |
| `lintDebug` | 0 errors |
| SQL/remoto | Plan en `docs/04-calidad/M01-pruebas-recuperacion-y-eliminacion.md` — **no verificado en staging** |

---

## 8. Archivos principales

**Creados:** `AuthDeepLink.kt`, `AccountSecurityScreen.kt`, `AccountSecurityViewModel.kt`, `delete-account/`, matriz, pruebas calidad, tests Etapa 4.

**Modificados:** `014`, Auth repos/estado, Session/MainActivity/Nav, Profile, PushTokenRegistrar, Platform delete tokens, `.env.example`.

---

## 9. Pruebas remotas (honestidad)

**No se afirma éxito remoto.** Pendiente tras revisión:

1. Revisar y aprobar trigger 014  
2. Aplicar migración en staging  
3. Deploy `delete-account`  
4. Email recovery + deep link real  
5. Eliminación de cuenta de prueba  

---

## 10. Riesgos y deuda

- Textos legales en **borrador** → bloquean release publicable (D-M01-15).  
- Gate consent remoto degradado si 014 no está aplicada (skip documentado).  
- Storage posts/adoptions por URL: best-effort; prefix `users/{uid}` cubierto.  
- Retención futura pagos/clínica: deuda de módulo (matriz).  
- OAuth social / MFA: fuera de alcance.  

---

## 11. Criterios de aceptación Etapa 4

Marcados localmente OK salvo ítems remotos declarados pendientes. **M02 no iniciado.**
