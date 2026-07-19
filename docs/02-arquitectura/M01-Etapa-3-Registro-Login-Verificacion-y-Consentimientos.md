# LEOVER — M01 Etapa 3: Registro, login, verificación y consentimientos

**Módulo:** M01 — Identidad y Autenticación  
**Etapa:** 3 — Flujos principales de autenticación  
**Estado de entrada:** Etapa 2 aprobada  
**Backend oficial:** Supabase Auth + PostgreSQL/RLS de Supabase  
**Objetivo:** conectar los contratos de M01 con la UI real y completar registro, login, verificación, restauración de sesión y consentimientos legales versionados.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-auditoria-inicial.md`
4. `/docs/02-arquitectura/M01-etapa-2-cierre.md`
5. `/docs/03-modulos/M01-Identidad-y-Autenticacion.md`
6. `/docs/03-modulos/M01-Etapa-2-Contratos-Estado-y-Validaciones.md`
7. `/docs/adr/ADR-0001-Supabase-como-backend-principal.md`
8. `/docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
9. `/docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md`
10. `/docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
11. `/docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
12. Este documento.

Las decisiones aprobadas de M00 y M01 tienen prioridad sobre especificaciones históricas incompatibles.

---

## 2. Decisiones vigentes

1. Reutilizar el único `AuthProvider`, `AuthRepository` y cliente Supabase.
2. Mantener la estructura actual de paquetes; no realizar movimientos masivos.
3. Usar los validadores, comandos, `AuthState`, `AuthUser`, `AppError` y mapper creados en Etapa 2.
4. Contraseña mínima de 8 caracteres.
5. El registro de M01 no permite seleccionar `AccountType`; usar `PERSON` únicamente como compatibilidad técnica cuando el modelo existente lo requiera.
6. No agregar proveedores sociales.
7. No implementar todavía reset remoto completo ni eliminación de cuenta; pertenecen a Etapa 4.
8. Mantener `com.comunidapp.app://login-callback`.
9. No inventar textos legales definitivos ni URLs públicas.
10. Supabase continúa siendo el único backend.

---

## 3. Protección de Git

Antes de modificar:

1. Ejecutar `git status`.
2. Preservar cualquier cambio local.
3. Crear un commit identificable con la Etapa 2.
4. Crear una rama limpia:

```text
m01/etapa-3-flujos-auth-consentimientos
```

5. Confirmar que no contiene `wip/gps-mapas-pagos`.
6. Registrar SHA base y estado inicial en el cierre.

No mezclar esta etapa con GPS, mapas, pagos, marketplace u otros módulos.

---

## 4. Alcance autorizado

### 4.1 Integración de ViewModels con el dominio M01

Migrar incrementalmente los ViewModels existentes para utilizar:

- comandos tipados;
- validadores centrales;
- `AuthState`;
- `AppError` y `AuthErrorMapper`;
- prevención de doble envío;
- `StateFlow`;
- coroutines estructuradas;
- `AppLogger` sin datos personales.

No exponer mensajes técnicos ni comparar errores directamente en la UI.

### 4.2 Pantalla de registro

La pantalla debe contener:

- email;
- contraseña;
- confirmar contraseña;
- checkbox independiente de términos;
- checkbox independiente de privacidad;
- enlaces visibles a ambos documentos;
- validaciones inline;
- botón con loading;
- acceso a iniciar sesión.

Eliminar u ocultar:

- selector `AccountType`;
- opciones de organización, profesional o comercio.

Reglas:

- no enviar si faltan consentimientos;
- normalizar email;
- usar mínimo 8 caracteres;
- impedir doble submit;
- usar `PERSON` solo internamente si el código legacy lo exige;
- no crear todavía perfil social completo;
- después del alta, mostrar verificación de correo si corresponde.

### 4.3 Documentos legales y versiones

No inventar texto jurídico definitivo.

Crear una configuración tipada, por ejemplo:

```text
LegalDocumentConfig
```

Debe exponer:

- `termsVersion`;
- `privacyVersion`;
- fuente o ruta del documento;
- indicador de documento publicable.

Comportamiento:

- en debug se permite una versión de borrador claramente identificada;
- en release la configuración debe fallar de forma segura si faltan versiones o documentos publicables;
- no usar URLs falsas;
- si existen documentos legales reales en el repositorio, utilizarlos;
- si no existen, crear pantallas internas marcadas:
  `BORRADOR — NO PUBLICABLE`;
- documentar que la revisión legal es requisito de lanzamiento, no de compilación debug.

Los textos no deben estar duplicados en la tabla de consentimientos: solo se guarda la versión aceptada.

### 4.4 Persistencia `user_consents`

Auditar primero el último número de migración y el trigger `handle_new_user`.

Crear una migración con el siguiente número disponible, sin sobrescribir migraciones existentes.

Tabla mínima:

```text
public.user_consents
- id uuid primary key default gen_random_uuid()
- user_id uuid not null references auth.users(id) on delete cascade
- terms_version text not null
- privacy_version text not null
- accepted_at timestamptz not null default now()
- locale text null
- source text not null
- created_at timestamptz not null default now()
```

Restricciones:

- `terms_version` y `privacy_version` no vacías;
- `source` limitado al conjunto permitido, inicialmente `registration`;
- índice por `user_id`;
- unicidad por:
  `(user_id, terms_version, privacy_version)`;
- RLS habilitado;
- usuario autenticado puede leer únicamente sus consentimientos;
- no dar INSERT directo al cliente para el consentimiento inicial si se usa trigger;
- service role conserva acceso administrativo.

### 4.5 Registro del consentimiento

Estrategia aprobada para el consentimiento inicial:

1. La app envía en `signUp` metadata allowlisted:
   - `terms_version`;
   - `privacy_version`;
   - `consent_locale`;
   - `consent_source=registration`.
2. Una nueva migración actualiza de forma idempotente la función existente `handle_new_user`.
3. La función:
   - conserva toda la lógica actual de creación de `public.users`;
   - valida que las versiones no estén vacías;
   - inserta `user_consents` con hora de servidor;
   - usa `ON CONFLICT DO NOTHING`;
   - no confía en un `user_id` enviado desde Android;
   - toma el UUID de `NEW.id`.
4. Si el trigger no puede insertar el consentimiento, la creación del usuario no debe dejar un estado silenciosamente inconsistente:
   - definir y documentar si la transacción completa hace rollback;
   - agregar prueba SQL/manual controlada.
5. No almacenar la aceptación solo de manera local.

No modificar la función sin copiar y revisar primero su definición vigente en las migraciones `004` y `009`.

### 4.6 Pantalla de verificación

Reutilizar y completar la pantalla existente:

- mostrar email parcialmente enmascarado;
- indicar que debe revisar correo y spam;
- reenvío con cooldown de 60 segundos;
- evitar múltiples envíos;
- manejar rate limit;
- procesar OTP si el flujo actual lo utiliza;
- procesar callback/deep link;
- actualizar/refrescar sesión;
- navegar solo una vez;
- permitir volver a login;
- no afirmar que el correo fue enviado si el repositorio devolvió error.

### 4.7 Inicio de sesión

Migrar la pantalla y ViewModel existentes:

- email y contraseña;
- validadores centrales;
- botón con loading;
- prevención de doble envío;
- mostrar/ocultar password;
- recuperación de contraseña;
- acceso al registro;
- mensajes genéricos y seguros;
- dirigir a verificación cuando corresponda;
- restaurar ruta solicitada cuando el router actual lo permita sin refactor masivo.

No mostrar diferencias que permitan enumerar cuentas.

### 4.8 Restauración y navegación de sesión

Integrar `AuthState` con el gate actual:

- `Initializing`: mantener splash;
- `Unauthenticated`: flujo público;
- `EmailVerificationRequired`: verificación;
- `Authenticated`: flujo principal;
- `ConfigurationError`: estado recuperable;
- `AuthError`: mensaje seguro y opción de reintento.

Reglas:

- una única fuente observable de verdad;
- no navegar dos veces por recomposición;
- no usar flags locales de pantalla como autoridad de sesión;
- preservar compatibilidad con `SessionState` solo mediante adapter temporal documentado;
- no iniciar M02.

### 4.9 Recuperación: solo solicitud

En esta etapa se permite completar únicamente:

- pantalla “Olvidé mi contraseña”;
- validación de email;
- respuesta genérica;
- loading;
- prevención de doble envío;
- confirmación de solicitud;
- manejo de red/rate limit.

No implementar todavía:

- pantalla final de nueva contraseña;
- aplicación remota de nueva contraseña;
- sesión de recovery;
- enlace expirado.

Eso corresponde a Etapa 4.

---

## 5. Fuera de alcance

No implementar:

- reset remoto completo;
- eliminación de cuenta;
- `account_deletion_requests`;
- Edge Function `delete-account`;
- OAuth social;
- MFA, biometría o teléfono;
- perfil social;
- roles y permisos M02;
- GPS, mapas o pagos;
- renombre de paquete;
- Hilt, Retrofit, NestJS o segunda base;
- actualización masiva de dependencias;
- textos legales definitivos inventados.

---

## 6. Requisitos de UX y accesibilidad

- Material 3.
- Compatibilidad con tema claro y oscuro.
- Labels visibles; no depender solo de placeholders.
- Errores asociados al campo correspondiente.
- Orden de foco correcto.
- Acciones accesibles con TalkBack.
- Contraste suficiente.
- Teclado de email para email.
- IME actions coherentes.
- Permitir pegar desde gestores de contraseñas.
- No limpiar campos ante errores recuperables salvo contraseña cuando sea necesario.
- Botones deshabilitados durante envío.
- No mostrar un spinner indefinido.
- Usar los componentes Loading/Empty/Error/Retry de M00 cuando corresponda.

---

## 7. Seguridad y privacidad

- No registrar email completo, contraseña, OTP o tokens.
- Enmascarar email en la pantalla de verificación.
- No persistir contraseña en SavedState, preferencias o analytics.
- Deep link procesado una sola vez.
- No cambiar scheme/host.
- RLS obligatorio para `user_consents`.
- Hora de aceptación generada por servidor.
- Consentimiento vinculado a `NEW.id` del trigger.
- No usar service role en Android.
- Mensajes genéricos en login y recuperación.
- No activar `FLAG_SECURE` globalmente.
- No enviar `AccountType` como metadata de autenticación.
- Revisar que el trigger no acepte metadatos arbitrarios fuera de la allowlist.

---

## 8. Analítica permitida

Documentar o emitir mediante la abstracción disponible, sin PII:

```text
signup_started
signup_validation_failed
signup_completed
email_verification_requested
email_verification_completed
login_started
login_completed
password_recovery_requested
auth_error_shown
```

No enviar:

- email;
- password;
- OTP;
- token;
- texto de error técnico completo;
- IDs públicos innecesarios.

Si M07 aún no existe, no agregar un proveedor externo.

---

## 9. Pruebas requeridas

### 9.1 Unitarias

Agregar pruebas para:

- RegisterViewModel con campos válidos;
- falta de términos;
- falta de privacidad;
- contraseña corta;
- confirmación distinta;
- doble submit;
- mapper de error en UI;
- LoginViewModel éxito/error/verificación;
- ForgotPasswordViewModel respuesta genérica;
- EmailVerificationViewModel cooldown;
- SessionViewModel y adapter de navegación;
- metadata de consentimiento;
- `LegalDocumentConfig` debug/release;
- email enmascarado.

### 9.2 Repositorio/mock

- registro guarda versiones en el comando/metadata simulada;
- login fixture;
- usuario no verificado;
- reenvío;
- rate limit simulado;
- recuperación genérica;
- no AccountType.

### 9.3 SQL/RLS

Crear un documento de prueba con sentencias seguras:

```text
/docs/04-calidad/M01-pruebas-user-consents.md
```

Debe comprobar:

- tabla y restricciones;
- trigger crea consentimiento;
- hora de servidor;
- idempotencia;
- RLS: usuario A no lee B;
- no INSERT directo no autorizado;
- cascade al eliminar auth user en entorno de prueba;
- profile trigger continúa funcionando.

No ejecutar operaciones destructivas en producción.

### 9.4 UI/manual

Documentar:

- registro mock;
- registro Supabase de prueba;
- email enviado;
- deep link;
- reenvío/cooldown;
- login verificado;
- login no verificado;
- restauración tras cerrar/reabrir;
- dark mode;
- TalkBack básico;
- error de red;
- configuración legal faltante en release.

Si no puede probarse Supabase remoto, declararlo honestamente.

---

## 10. Orden de implementación

### Bloque 0 — Git y análisis

- consolidar Etapa 2;
- crear rama limpia;
- revisar trigger actual y migraciones;
- listar archivos;
- confirmar WIP excluido.

### Bloque 1 — Configuración legal y migración

- `LegalDocumentConfig`;
- migración `user_consents`;
- actualización idempotente de `handle_new_user`;
- RLS;
- documento de pruebas SQL;
- pruebas unitarias posibles;
- build/tests/lint.

### Bloque 2 — Registro

- ViewModel con comandos/validadores;
- UI sin AccountType;
- checkboxes legales;
- metadata de consentimiento;
- navegación a verificación;
- pruebas;
- build/tests/lint.

### Bloque 3 — Verificación

- email enmascarado;
- cooldown;
- reenvío;
- OTP/callback vigente;
- refresh de sesión;
- navegación idempotente;
- pruebas;
- build/tests/lint.

### Bloque 4 — Login, recuperación solicitada y gate

- LoginViewModel/UI;
- ForgotPasswordViewModel/UI;
- Session/AuthState;
- adapter temporal si aplica;
- pruebas;
- build/tests/lint.

### Bloque 5 — Calidad y cierre

- smoke documentado;
- revisar analytics sin PII;
- actualizar arquitectura;
- build/tests/lint final;
- crear cierre.

Detenerse ante cualquier regresión y corregir antes de continuar.

---

## 11. Archivos esperados

Adaptar a la estructura existente.

Posibles archivos nuevos:

```text
domain/auth/LegalDocumentConfig.kt
domain/auth/ConsentMetadata.kt
ui/screens/legal/TermsScreen.kt
ui/screens/legal/PrivacyScreen.kt
supabase/migrations/NNN_user_consents.sql
docs/04-calidad/M01-pruebas-user-consents.md
```

Posibles modificaciones:

```text
RegisterViewModel.kt
RegisterScreen.kt
LoginViewModel.kt
LoginScreen.kt
ForgotPasswordViewModel.kt
ForgotPasswordScreen.kt
EmailVerificationViewModel.kt
EmailVerificationScreen.kt
SessionViewModel.kt
ComunidappNavGraph.kt
NavRoutes.kt
AuthRepository.kt
MockAuthRepository
SupabaseAuthRepository.kt
```

No crear un segundo repositorio o cliente.

---

## 12. Criterios de aceptación

- [ ] Etapa 2 consolidada en commit.
- [ ] Rama limpia sin WIP funcional.
- [ ] Registro utiliza comandos y validadores centrales.
- [ ] AccountType no aparece en el registro.
- [ ] Términos y privacidad se aceptan por separado.
- [ ] Configuración legal es tipada.
- [ ] Release falla de forma segura si faltan documentos publicables.
- [ ] No se inventaron URLs ni textos legales definitivos.
- [ ] `user_consents` existe con restricciones, índice, unicidad y RLS.
- [ ] Trigger conserva creación de `public.users`.
- [ ] Trigger inserta consentimiento de forma idempotente.
- [ ] Consentimiento usa hora de servidor.
- [ ] Registro conduce correctamente a verificación.
- [ ] Verificación y reenvío tienen cooldown.
- [ ] Login usa AppError y mensajes seguros.
- [ ] Sesión se restaura mediante AuthState.
- [ ] Recuperación devuelve respuesta genérica.
- [ ] No se implementó reset remoto final ni eliminación.
- [ ] No se inició M02.
- [ ] Tests anteriores siguen aprobados.
- [ ] Nuevos tests aprobados.
- [ ] `assembleDebug` aprobado.
- [ ] `lintDebug` con 0 errores.
- [ ] CI sin secretos.
- [ ] Pruebas remotas no verificadas se declararon honestamente.
- [ ] Se creó el cierre de etapa.

---

## 13. Entregables de cierre

Crear exactamente:

```text
/docs/02-arquitectura/M01-etapa-3-cierre.md
```

Debe incluir:

- rama y commits;
- archivos creados/modificados/eliminados;
- migración y políticas;
- cambios de trigger;
- UI y ViewModels;
- pruebas agregadas;
- resultados build/tests/lint;
- pruebas Supabase reales realizadas o no realizadas;
- estado del checklist dashboard;
- riesgos;
- deuda de Etapa 4;
- checklist completo.

No iniciar Etapa 4.

---

## 14. Instrucción de parada

Detenerse después de crear:

```text
/docs/02-arquitectura/M01-etapa-3-cierre.md
```

Esperar revisión explícita.
