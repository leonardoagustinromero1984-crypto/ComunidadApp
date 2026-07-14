# LEOVER — M01 Identidad y Autenticación

**Módulo:** M01 — Identidad y Autenticación  
**Versión:** 1.0  
**Estado:** Autorizado para iniciar Etapa 1  
**Dependencia obligatoria:** M00 cerrado y aprobado  
**Backend oficial:** Supabase Auth + Supabase Edge Functions cuando se necesiten operaciones privilegiadas  
**Aplicación actual:** Android Kotlin + Jetpack Compose  
**Regla principal:** auditar y reutilizar lo existente antes de modificar.

---

## 1. Objetivo

Consolidar un sistema seguro y coherente de identidad y autenticación para Leover que permita:

- registrar una cuenta;
- verificar el correo;
- iniciar sesión;
- restaurar la sesión;
- recuperar y cambiar la contraseña;
- cerrar sesión;
- aceptar versiones de términos y privacidad;
- gestionar errores de autenticación;
- solicitar y ejecutar la eliminación segura de la cuenta;
- funcionar en modo mock y en modo Supabase sin cambiar la UI;
- dejar contratos listos para M02 Usuarios, Roles y Permisos.

M01 no crea perfiles sociales completos, organizaciones, mascotas ni permisos de negocio.

---

## 2. Documentos obligatorios

Leer antes de trabajar:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/03-modulos/M00-Fundacion-Tecnica.md`
3. `/docs/02-arquitectura/M00-cierre-final.md`
4. `/docs/adr/ADR-0001-Supabase-como-backend-principal.md`
5. `/docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
6. `/docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md`
7. `/docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
8. `/docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
9. `/docs/03-modulos/M01-Identidad-y-Autenticacion.md`

Los ADR vigentes tienen prioridad frente a instrucciones históricas incompatibles.

---

## 3. Alcance funcional

### 3.1 Registro con email y contraseña

Datos mínimos:

- email;
- contraseña;
- confirmación de contraseña;
- aceptación obligatoria de términos;
- aceptación obligatoria de política de privacidad.

Reglas:

- normalizar email con trim y minúsculas;
- validar formato de email;
- contraseña mínima de 8 caracteres;
- exigir coincidencia de contraseña y confirmación;
- no guardar contraseñas en la base ni en logs;
- manejar el caso de correo ya registrado sin filtrar detalles innecesarios;
- después del registro, dirigir a verificación de correo cuando Supabase lo requiera;
- no crear todavía un perfil social completo: M02 será responsable.

### 3.2 Verificación de correo

- pantalla de “Revisá tu correo”;
- reenvío de verificación con control de frecuencia;
- cambio de email para corregir un error, si el SDK y el flujo actual lo permiten de forma segura;
- deep link/callback compatible con la configuración existente;
- refrescar sesión y estado al volver a la app;
- no inventar dominio web ni cambiar el paquete Android en M01.

### 3.3 Inicio de sesión

- email y contraseña;
- indicador de carga;
- mensajes seguros;
- evitar doble envío;
- restaurar la ruta solicitada después de autenticar, cuando aplique;
- si el correo requiere verificación, dirigir al estado correspondiente;
- si la sesión es válida, no volver a mostrar login.

### 3.4 Restauración y ciclo de sesión

- observar el estado de Supabase Auth;
- restaurar sesión al iniciar;
- distinguir carga inicial, autenticado y no autenticado;
- reaccionar a token expirado o revocado;
- limpiar estado sensible al cerrar sesión;
- evitar pantallas intermedias inconsistentes;
- mantener funcionamiento mock para CI y previews.

### 3.5 Recuperación y cambio de contraseña

- solicitar recuperación por email;
- respuesta genérica que no confirme si el correo existe;
- deep link de recuperación;
- pantalla para ingresar nueva contraseña y confirmación;
- cerrar o refrescar sesiones según el comportamiento seguro definido;
- invalidar formularios después de completar;
- documentar prueba manual del deep link.

### 3.6 Cierre de sesión

- disponible desde la cuenta/configuración existente;
- confirmación opcional si hay procesos en curso;
- limpiar sesión y datos sensibles en memoria;
- volver a una ruta pública;
- no borrar preferencias no sensibles sin necesidad.

### 3.7 Consentimientos legales

Registrar de forma versionada:

- versión de términos;
- versión de privacidad;
- fecha y hora de aceptación;
- usuario;
- país/locale cuando esté disponible sin solicitar ubicación exacta;
- origen de la aceptación: registro o actualización.

No guardar texto completo duplicado; guardar versión y referencia.

### 3.8 Eliminación de cuenta

Debe existir un flujo seguro:

1. usuario autenticado solicita eliminación;
2. se informa qué ocurrirá y qué datos pueden conservarse por obligación legal o antifraude;
3. se exige reautenticación cuando sea técnicamente posible;
4. una Edge Function autorizada realiza la operación privilegiada;
5. la clave `service_role` nunca está en Android;
6. se eliminan o anonimizan únicamente los datos existentes y documentados;
7. se cierra la sesión local;
8. se muestra confirmación final.

No diseñar todavía políticas de retención de módulos futuros. Registrar deuda para cada dominio nuevo.

### 3.9 Proveedores sociales

- Google/OAuth solo se conserva si ya existe y funciona.
- Si está incompleto, se documenta y se deja fuera de la primera entrega de M01.
- No agregar Facebook, Apple, teléfono, MFA o biometría en esta versión.
- No romper el deep link actual.

---

## 4. Fuera de alcance

- perfil personal completo;
- nombre público, avatar, biografía, localidad y preferencias sociales;
- roles de rescatista, organización, profesional o comercio;
- permisos y matriz RBAC;
- perfiles de mascotas;
- mapas, GPS y pagos;
- chat, feed o notificaciones de negocio;
- inicio con teléfono;
- MFA;
- biometría;
- passkeys;
- migración del paquete `com.comunidapp.app`;
- nuevo backend distinto de Supabase;
- refactor masivo a Hilt o módulos Gradle.

Estos puntos pertenecen a M02 o módulos posteriores.

---

## 5. Actores

| Actor | Descripción |
|---|---|
| Visitante | Persona sin sesión válida. |
| Usuario pendiente de verificación | Cuenta creada cuyo email aún no fue confirmado. |
| Usuario autenticado | Cuenta con sesión válida. |
| Usuario en recuperación | Identidad que abrió un enlace válido de recuperación. |
| Usuario con eliminación pendiente | Usuario que inició el flujo de cierre definitivo. |
| Administrador técnico | Opera Supabase/Edge Functions; no actúa desde la app como usuario común. |
| Sistema | Supabase Auth, app Android y Edge Functions. |

---

## 6. Estados oficiales de autenticación

```text
Initializing
Unauthenticated
Registering
EmailVerificationRequired
Authenticating
Authenticated
PasswordRecoveryRequested
PasswordResetActive
SigningOut
AccountDeletionPending
ConfigurationError
AuthError
```

Reglas:

- usar una única fuente observable de verdad;
- no derivar autenticación solo desde una pantalla;
- los estados transitorios deben impedir acciones duplicadas;
- los errores deben mapearse a `AppError`;
- los mensajes técnicos no se exponen directamente al usuario.

---

## 7. Reglas de negocio

| ID | Regla |
|---|---|
| M01-RN-001 | Cada identidad de Supabase tiene un UUID único. |
| M01-RN-002 | El email se normaliza antes de enviarlo. |
| M01-RN-003 | No se almacena ni registra la contraseña. |
| M01-RN-004 | Términos y privacidad deben aceptarse para crear la cuenta. |
| M01-RN-005 | La sesión válida se restaura sin solicitar login nuevamente. |
| M01-RN-006 | La recuperación no confirma públicamente si un correo existe. |
| M01-RN-007 | El reenvío de verificación debe limitar envíos repetidos. |
| M01-RN-008 | Una operación privilegiada nunca usa service_role en Android. |
| M01-RN-009 | El cierre de sesión elimina tokens y estado sensible local. |
| M01-RN-010 | La eliminación de cuenta debe dejar trazabilidad técnica sin conservar datos innecesarios. |
| M01-RN-011 | El modo mock debe respetar los mismos contratos de UI. |
| M01-RN-012 | M01 no asigna roles de negocio ni crea perfiles completos. |
| M01-RN-013 | Ningún error de autenticación debe registrar datos personales. |
| M01-RN-014 | Los deep links válidos se procesan una sola vez de manera idempotente. |
| M01-RN-015 | No se puede iniciar M02 con build, tests o lint en rojo. |

---

## 8. Validaciones

### Email

- obligatorio;
- trim;
- minúsculas;
- formato razonable;
- máximo 254 caracteres;
- no mostrar si existe en recuperación.

### Contraseña

- obligatoria;
- mínimo 8 caracteres;
- máximo compatible con Supabase;
- confirmación coincidente;
- permitir pegar desde gestor de contraseñas;
- mostrar/ocultar contraseña;
- no imponer reglas arbitrarias difíciles de recordar;
- no persistir en SavedState, logs o analytics.

### Consentimientos

- checkbox independiente para términos y privacidad;
- enlaces visibles a documentos;
- versión configurada de manera central;
- bloquear envío si falta alguno.

---

## 9. Pantallas y componentes

### Pantallas mínimas

1. AuthGate / Splash de sesión.
2. Iniciar sesión.
3. Crear cuenta.
4. Verificar correo.
5. Olvidé mi contraseña.
6. Restablecer contraseña.
7. Confirmar cierre de sesión, si aplica.
8. Seguridad de cuenta / eliminar cuenta.
9. Resultado de eliminación.
10. Error de configuración recuperable.

### Componentes reutilizables

- campo de email;
- campo de contraseña;
- indicador de fortaleza básico y no engañoso;
- checkbox legal;
- botón principal con loading;
- mensaje inline de validación;
- banner/error recuperable;
- acción de reenvío con countdown;
- diálogo de confirmación destructiva.

Usar Material 3 y los estados comunes creados en M00.

---

## 10. Arquitectura esperada

Adaptar sin mover masivamente el proyecto.

Ubicaciones sugeridas:

```text
app/src/main/java/com/comunidapp/app/
├── data/auth/
│   ├── AuthDataSource.kt
│   ├── SupabaseAuthDataSource.kt
│   ├── MockAuthDataSource.kt
│   └── AuthRepositoryImpl.kt
├── domain/auth/
│   ├── AuthRepository.kt
│   ├── AuthState.kt
│   ├── AuthUser.kt
│   ├── AuthCommand.kt
│   └── validation/
├── ui/auth/
│   ├── signin/
│   ├── signup/
│   ├── verification/
│   ├── recovery/
│   └── accountsecurity/
└── viewmodel/auth/
```

La estructura final debe respetar la organización actual si ya existe una alternativa coherente.

Reglas técnicas:

- interfaces para repositorio/fuente;
- `StateFlow` para estado;
- coroutines estructuradas;
- `AppConfig`, `FeatureFlags`, `AppLogger`, `AppResult` y `AppError` de M00;
- no leer `BuildConfig` directamente desde UI nueva;
- no duplicar clientes Supabase;
- no introducir Retrofit;
- no introducir Hilt en M01.

---

## 11. Datos y Supabase

### Auditoría obligatoria previa

Identificar:

- configuración Auth actual;
- proveedores habilitados;
- callback/deep links;
- tablas relacionadas con usuarios;
- triggers de alta;
- RLS;
- templates de email;
- Edge Functions;
- migraciones existentes;
- código mock;
- rutas y pantallas actuales;
- manejo de sesión;
- pruebas existentes.

### Tablas permitidas si no existen

#### `user_consents`

Campos mínimos:

```text
id uuid primary key
user_id uuid not null references auth.users
terms_version text not null
privacy_version text not null
accepted_at timestamptz not null
locale text null
source text not null
created_at timestamptz not null
```

Requisitos:

- RLS;
- insertar/leer solo el propio usuario;
- service role para auditoría administrativa futura;
- índice por `user_id`;
- migración con el próximo número disponible, no asumir secuencia.

#### `account_deletion_requests`

Solo si el flujo necesita procesamiento o auditoría:

```text
id uuid primary key
user_id uuid not null
status text not null
requested_at timestamptz not null
completed_at timestamptz null
failure_code text null
```

No guardar motivo libre con datos sensibles salvo decisión explícita.

### Edge Function `delete-account`

Crear solo después de la auditoría y si no existe una alternativa segura.

Requisitos:

- validar JWT;
- identificar al usuario desde el token, no desde un `user_id` enviado libremente;
- usar service role únicamente en servidor;
- ejecutar operaciones idempotentes;
- no devolver secretos;
- respuesta con correlation/request ID si la infraestructura actual lo soporta;
- documentar rollback y pruebas;
- respetar las tablas reales existentes.

---

## 12. Contrato de errores

Mapear, como mínimo:

```text
INVALID_CREDENTIALS
EMAIL_NOT_VERIFIED
EMAIL_ALREADY_REGISTERED
WEAK_PASSWORD
INVALID_EMAIL
RECOVERY_LINK_INVALID
RECOVERY_LINK_EXPIRED
SESSION_EXPIRED
RATE_LIMITED
NETWORK_UNAVAILABLE
CONFIGURATION_ERROR
ACCOUNT_DELETION_FAILED
UNKNOWN_AUTH_ERROR
```

Para recuperación y registro, los mensajes visibles deben evitar enumeración de cuentas.

---

## 13. Seguridad y privacidad

- HTTPS solamente.
- Tokens administrados por el SDK oficial.
- Service role fuera de Android.
- Sin passwords/tokens/email completo en logs.
- AppLogger sanitizado.
- No registrar eventos con ubicación o contenido sensible.
- No guardar contraseña en estado persistente.
- Revisar capturas de pantalla en formularios sensibles; documentar la decisión.
- Reautenticación para acciones destructivas cuando sea viable.
- RLS para tablas nuevas.
- Deep links restringidos a rutas conocidas.
- Validación de nonce/state en OAuth si el flujo existente lo requiere.
- Mensajes genéricos para evitar account enumeration.
- Política de rate limit basada en Supabase, sin inventar infraestructura paralela.

---

## 14. Analítica permitida

Eventos mínimos y sin datos personales:

```text
auth_screen_viewed
signup_started
signup_completed
email_verification_requested
email_verification_completed
login_started
login_completed
password_recovery_requested
password_reset_completed
logout_completed
account_deletion_started
account_deletion_completed
auth_error_shown
```

No incluir email, user_id público, tokens ni contraseña. Si M07 aún no está implementado, documentar eventos y usar la abstracción disponible sin incorporar un proveedor externo.

---

## 15. Pruebas

### Unitarias

- validación de email;
- validación de contraseña;
- aceptación legal;
- mapeo de errores Supabase;
- transición de estados;
- restauración de sesión;
- modo mock;
- reenvío con cooldown;
- sanitización de logs;
- eliminación: request/response mapper.

### Integración local/remota controlada

- registro;
- verificación;
- login;
- logout;
- recuperación;
- reset;
- restauración tras cerrar/reabrir;
- enlace inválido/expirado;
- eliminación segura;
- RLS de consentimientos.

### UI / smoke manual

- rotación/recomposición sin doble envío;
- teclado y accesibilidad;
- back navigation;
- deep links;
- dark mode;
- errores de red;
- loading;
- cuenta no verificada.

Todos los tests existentes de M00 deben conservarse.

---

## 16. Criterios de aceptación

- [ ] Auditoría M01 aprobada.
- [ ] No se duplicó AuthProvider/cliente Supabase.
- [ ] Registro email/password funciona.
- [ ] Consentimientos legales se guardan versionados.
- [ ] Verificación y reenvío funcionan.
- [ ] Login seguro funciona.
- [ ] Sesión se restaura correctamente.
- [ ] Recuperación y reset funcionan.
- [ ] Logout limpia estado sensible.
- [ ] Eliminación no expone service role.
- [ ] Mock y Supabase respetan el mismo contrato.
- [ ] Errores usan AppError/AppResult.
- [ ] Logs no contienen secretos ni datos personales.
- [ ] Deep links están documentados y probados.
- [ ] RLS de tablas nuevas está probado.
- [ ] Build pasa.
- [ ] Tests pasan.
- [ ] Lint queda con 0 errores.
- [ ] CI permanece verde o se documenta honestamente su estado remoto.
- [ ] No se desarrolló M02 ni módulos futuros.
- [ ] Se generó cierre formal.

---

## 17. Etapas de ejecución

### Etapa 1 — Auditoría y diseño

No modificar funcionalidad.

Crear:

```text
/docs/02-arquitectura/M01-auditoria-inicial.md
```

Debe incluir:

- inventario actual de autenticación;
- pantallas, rutas y ViewModels;
- AuthProvider/DataProvider/repositorios;
- Supabase Auth y proveedores;
- deep links;
- migraciones, triggers, RLS y Edge Functions;
- modo mock;
- tests;
- diferencias contra esta especificación;
- riesgos;
- archivos que propone crear/modificar;
- plan por etapas;
- decisiones que requieren aprobación.

Detenerse después de la auditoría.

### Etapa 2 — Contratos, estado y validación

- modelos de dominio;
- repositorio/fuentes;
- estado único;
- validadores;
- mapeo de errores;
- tests.

### Etapa 3 — Registro, verificación y login

- UI y flujos;
- consentimientos;
- sesión;
- mock/remoto;
- tests.

### Etapa 4 — Recuperación, logout y eliminación

- deep links;
- reset;
- account security;
- Edge Function si corresponde;
- RLS;
- tests.

### Etapa 5 — Calidad y cierre

- smoke manual documentado;
- build/tests/lint;
- documentación;
- cierre final.

Crear al terminar:

```text
/docs/02-arquitectura/M01-cierre-final.md
```

---

## 18. Instrucción vigente para esta ejecución

Ejecutar **solamente Etapa 1 — Auditoría y diseño**.

Reglas:

- trabajar en rama `m01/identidad-autenticacion-auditoria`;
- preservar cambios locales;
- no mezclar WIP GPS/mapas/pagos;
- no modificar código de negocio;
- no crear migraciones todavía;
- no reconfigurar Supabase;
- no agregar proveedores sociales;
- no iniciar M02;
- no renombrar paquetes;
- no agregar Hilt, Retrofit, NestJS ni otro backend;
- no asumir que lo existente está mal;
- verificar build/tests/lint actuales;
- documentar con honestidad lo que no pueda probar.

Detenerse después de crear:

```text
/docs/02-arquitectura/M01-auditoria-inicial.md
```
