# M06 — Pruebas Etapa 4: push, deep links, preferencias e instalaciones

**Fecha:** 2026-07-17  
**Rama:** `m06/etapa-4-push-deep-links-preferencias-instalaciones`  
**Commit base:** `824a091df9bda80c40492c64486d6d5ecd9d0c06`  
**Estado:** local aprobado, staging pendiente.

---

## 1. Comandos

Ejecutados:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Resultado:

- `assembleDebug`: SUCCESS
- `testDebugUnitTest`: **480 tests, 0 failures, 0 errors**
- `lintDebug`: SUCCESS

---

## 2. Pruebas agregadas

Archivos:

- `M06Stage4PushDeepLinkInstallationTest` (32)
- `M06Migration027PushHardeningTest` (5)
- `M06Stage4WiringInventoryTest` (3)

Total agregado: **40** tests.

---

## 3. Matriz push / payload

Cubierto:

- payload válido tipado;
- rechazo de token, signed URL o PII en data;
- copy genérico para `SENSITIVE`;
- ruta desconocida → `SAFE_HOME`;
- push entregado no marca `READ`;
- retry → dead-letter;
- token inválido → `FAILED_PERMANENT`;
- Edge Function claim M06 + `UNAUTHORIZED` / sin body arbitrario Android;
- migración 027: claim/mark/plan/emit + sin reabrir cross-user.

---

## 4. Deep links

Cubierto:

- sin sesión → `DENIED_NOT_AUTHENTICATED`;
- recurso inexistente → `DENIED_DEEP_LINK`;
- permiso denegado / staff sin permiso;
- organización incorrecta;
- INTERNAL sin permiso staff;
- INTERNAL con permiso staff;
- doble tap / reentrega;
- logout limpia navegación pendiente;
- cambio de cuenta limpia navegación pendiente;
- `NotificationDeepLinkRouter.unknownRouteFallback` → home.

---

## 5. Preferencias

Cubierto:

- push desactivado excluye canal PUSH;
- categoría in-app obligatoria no desactivable;
- marketing OFF por defecto;
- quiet hours con defer (incluye zona con DST).

UI (manual / no instrumentada aquí): loading/saving/success/error/retry, email “Próximamente”, explicación de críticas.

---

## 6. Permiso Android

Cubierto a nivel unitario:

- denegación no bloquea uso de la app;
- estados `DENIED_CAN_ASK` vs `DENIED_PERMANENT` distintos;
- rationale menciona bandeja in-app.

No se pide automáticamente al iniciar (cambio en `MainActivity`).

---

## 7. Instalaciones

Cubierto:

- registro;
- rotate token;
- logout revoca solo instalación actual;
- otro dispositivo permanece activo;
- cambio de usuario reasocia instalación;
- no doble registro mismo fingerprint;
- modelo no expone raw token;
- comentario/migración anti doble push legacy/nuevo.

---

## 8. Deliveries / canales

Cubierto:

- estados y transiciones de retry/dead-letter;
- canales por categoría + fallback `leover_system`;
- `leover_default` conservado;
- sanitización de errores UI (Bearer).

---

## 9. Wiring M03–M05 y dispatcher

Cubierto:

- migración 027 contiene event keys / triggers M03–M05;
- inventario `NotificationDispatcher.remainingClientCallSites()` no vacío (deuda documentada);
- auth/username intactos (marcadores de `UsernameValidators` / ausencia de diffs en paths protegidos).

Estados documentados en cierre:

- M03: IMPLEMENTADO  
- M04: IMPLEMENTADO  
- M05: IMPLEMENTADO  
- Social/chat/amistad/booking cliente: PENDIENTE  

---

## 10. Realtime

No cubierto como funcional. Documentado **PENDIENTE** (refresh/polling).

---

## 11. Auth / username

Sin modificaciones en:

- `AuthRepository`
- `domain/auth`
- `UsernameValidators`

Pruebas existentes de username continúan en la suite (480 total).

---

## 12. Staging

`014`–`027` pendientes de validación remota. Sin evidencia de apply.

---

## 13. Parada

No iniciar Etapa 5.  
No implementar email.  
No iniciar M07.  
No merge a `main`.  
No producción.  
Sin commit hasta indicación explícita.
