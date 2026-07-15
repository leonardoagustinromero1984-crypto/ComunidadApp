# LEOVER — M01 Etapa 4: Recuperación, seguridad y eliminación de cuenta

**Módulo:** M01 — Identidad y Autenticación  
**Etapa:** 4 — Recuperación completa, seguridad de cuenta y eliminación  
**Estado de entrada:** Etapa 3 aprobada con corrección preventiva  
**Backend oficial:** Supabase Auth + Supabase PostgreSQL/RLS + Edge Functions  
**Objetivo:** cerrar el ciclo completo de credenciales y cuenta sin exponer secretos ni romper los flujos ya implementados.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-auditoria-inicial.md`
4. `/docs/02-arquitectura/M01-etapa-2-cierre.md`
5. `/docs/02-arquitectura/M01-etapa-3-cierre.md`
6. `/docs/03-modulos/M01-Identidad-y-Autenticacion.md`
7. `/docs/03-modulos/M01-Etapa-2-Contratos-Estado-y-Validaciones.md`
8. `/docs/03-modulos/M01-Etapa-3-Registro-Login-Verificacion-y-Consentimientos.md`
9. `/docs/adr/ADR-0001-Supabase-como-backend-principal.md`
10. `/docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
11. `/docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md`
12. `/docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
13. `/docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
14. Este documento.

Las decisiones aprobadas de M00 y M01 tienen prioridad sobre especificaciones históricas incompatibles.

---

## 2. Resoluciones obligatorias

### D-M01-10 — La migración 014 no se despliega todavía

`014_user_consents.sql` no fue aplicada ni validada en Supabase remoto.

Antes de desplegarla se debe revisar el trigger `handle_new_user`.

El diseño final debe cumplir simultáneamente:

- conservar siempre el alta de `public.users`;
- insertar consentimiento cuando metadata legal válida esté presente;
- no confiar en `user_id` enviado por Android;
- usar `NEW.id` y hora del servidor;
- impedir que usuarios de la app accedan al producto sin consentimiento;
- evitar que cuentas creadas por administración, invitaciones o futuros proveedores fallen con un error inesperado;
- mantener idempotencia.

Estrategia aprobada:

1. El registro Android continúa exigiendo términos y privacidad.
2. El trigger crea `public.users` siempre.
3. Si ambas versiones legales son válidas, crea `user_consents`.
4. Si falta metadata, no inserta consentimiento y no inventa una aceptación.
5. Al restaurar una sesión, la app consulta si existe consentimiento vigente.
6. Cuando falta, el estado pasa a `LegalConsentRequired`.
7. La persona debe aceptar versiones vigentes antes de entrar al flujo principal.
8. La aceptación posterior se guarda mediante una función RPC segura o una Edge Function que usa `auth.uid()`/JWT, nunca un `user_id` libre.

Si Cursor demuestra que el rollback actual es necesario y seguro para todos los caminos reales, debe documentarlo y detenerse para aprobación. No aplicar 014 al cloud sin resolver este punto.

### D-M01-11 — Recuperación oficial

El flujo será:

1. Solicitud genérica de email.
2. Apertura de deep link de tipo recovery.
3. Supabase establece/restaura una sesión de recuperación.
4. La aplicación entra en `PasswordResetActive`.
5. La persona ingresa nueva contraseña y confirmación.
6. El repositorio actualiza la contraseña con el SDK oficial.
7. La pantalla se invalida para evitar doble uso.
8. Se muestra confirmación y se dirige a login o sesión según el comportamiento real del SDK.

No actualizar masivamente `supabase-kt`. Adaptar la implementación a la versión instalada.

### D-M01-12 — Reautenticación de acciones sensibles

Para cambiar contraseña desde una sesión normal o eliminar la cuenta:

- solicitar la contraseña actual para identidades email/password;
- verificarla mediante el flujo oficial disponible;
- no guardar la contraseña;
- limitar intentos;
- no implementar todavía OAuth social;
- documentar cómo se extenderá cuando existan otros proveedores.

### D-M01-13 — Eliminación de cuenta desde servidor

La eliminación de `auth.users` requiere una operación administrativa y no puede ejecutarse con una clave privilegiada dentro de Android.

Se implementará una Edge Function `delete-account` que:

- valida el JWT;
- obtiene el usuario desde el token;
- no acepta un `user_id` libre como autoridad;
- usa la clave secreta únicamente dentro de la función;
- audita tablas, FKs y Storage antes de borrar;
- elimina o anonimiza datos según una matriz explícita;
- elimina archivos propiedad del usuario cuando corresponda;
- finalmente elimina la identidad Auth;
- devuelve un resultado seguro;
- es tolerante a reintentos razonables;
- registra un request/correlation ID.

### D-M01-14 — Borrado duro actual, retención futura por dominio

Para los datos actuales de M01 se utilizará borrado duro o cascade cuando no exista obligación de conservación.

No definir políticas ficticias para pagos, facturación, adopciones formalizadas, historias clínicas, denuncias o moderación. Cada módulo futuro deberá declarar su política de retención.

### D-M01-15 — Consentimientos legales publicables

M01 puede cerrarse técnicamente con documentos marcados como borrador en debug.

No se habilitará un release publicable mientras:

- términos y privacidad no tengan texto aprobado;
- `publishable=true` no esté configurado;
- las versiones legales no sean definitivas;
- los enlaces o pantallas no hayan sido revisados.

---

## 3. Protección de Git

Antes de modificar:

1. Ejecutar `git status`.
2. Preservar cambios locales.
3. Consolidar Etapa 3 en un commit identificable.
4. Crear una rama limpia:

```text
m01/etapa-4-recuperacion-seguridad-eliminacion
```

5. Confirmar que no contiene `wip/gps-mapas-pagos`.
6. Registrar SHA base y estado inicial en el cierre.

---

## 4. Alcance autorizado

### 4.1 Corrección y validación de consentimientos

Auditar `014_user_consents.sql`, la definición vigente de `handle_new_user`, las rutas de registro, la lectura de consentimiento y el comportamiento cuando falta metadata.

Implementar:

- modificación de 014 si aún no fue aplicada en ningún entorno compartido, o una migración correctiva nueva si ya fue aplicada;
- consulta de consentimiento vigente;
- estado `LegalConsentRequired`;
- pantalla de aceptación pendiente;
- persistencia segura de aceptación posterior;
- pruebas RLS e idempotencia.

No modificar migraciones aplicadas en entornos compartidos sin una migración correctiva.

### 4.2 Pantalla de nueva contraseña

Crear o completar:

- nueva contraseña;
- confirmación;
- mostrar/ocultar;
- mínimo 8 caracteres;
- loading;
- error inline;
- confirmación de éxito;
- estado de enlace inválido o expirado;
- prevención de doble envío;
- navegación idempotente.

No persistir la contraseña ni registrarla.

### 4.3 Deep link de recovery

Procesar únicamente rutas y tipos esperados:

- confirmación de email;
- recuperación de contraseña.

Requisitos:

- procesar el intent una vez;
- diferenciar confirmación y recovery;
- no navegar por recomposición;
- restaurar/actualizar sesión;
- limpiar el estado después de consumirlo;
- mantener `com.comunidapp.app://login-callback`;
- documentar pruebas manuales.

### 4.4 Repositorio de reset

Reemplazar `PASSWORD_RESET_NOT_AVAILABLE` por una implementación real únicamente cuando exista una sesión de recovery válida.

El repositorio debe:

- validar la contraseña con `AuthValidators`;
- actualizar mediante el cliente Supabase existente;
- mapear errores a `AppError`;
- detectar sesión ausente/expirada;
- no duplicar el cliente;
- no devolver éxito falso;
- funcionar en mock con comportamiento equivalente.

### 4.5 Seguridad de cuenta

Crear una pantalla accesible desde perfil/configuración existente:

- cambiar contraseña;
- cerrar sesión;
- eliminar cuenta;
- explicación clara de cada acción.

Cambio de contraseña desde sesión normal:

- contraseña actual;
- nueva contraseña;
- confirmación;
- reautenticación;
- confirmación final.

### 4.6 Logout endurecido

Auditar y completar:

- `signOut` del SDK;
- limpieza de `AuthState`;
- limpieza de contraseñas/OTP temporales;
- cancelación de jobs de sesión;
- limpieza de caché sensible;
- desvinculación del token FCM del usuario si existe persistencia remota;
- navegación pública una sola vez.

### 4.7 Matriz de eliminación

Antes de escribir la Edge Function, crear:

```text
/docs/02-arquitectura/M01-matriz-eliminacion-de-cuenta.md
```

Debe inventariar todas las tablas y buckets existentes. Por cada entidad indicar:

```text
entidad
propietario/FK
acción: delete / cascade / anonymize / retain / block
orden
razón
prueba
rollback o recuperación
```

Reglas:

- no asumir cascades;
- revisar objetos de Storage;
- no borrar contenido de otros usuarios;
- no eliminar organizaciones completas por borrar un miembro;
- no conservar PII sin justificación;
- bloquear la implementación si existe una FK o bucket sin estrategia.

### 4.8 Registro de solicitudes

Crear tabla solo si la auditoría confirma que aporta idempotencia o soporte.

Modelo recomendado:

```text
public.account_deletion_requests
- id uuid primary key
- user_id uuid null references auth.users(id) on delete set null
- status text not null
- requested_at timestamptz not null
- completed_at timestamptz null
- failure_code text null
- created_at timestamptz not null
```

No guardar email, contraseña, motivo libre ni contenido sensible.

### 4.9 Edge Function `delete-account`

Ubicación:

```text
supabase/functions/delete-account/
```

Requisitos mínimos:

1. Aceptar solo POST.
2. Requerir Authorization Bearer.
3. Validar la sesión con Supabase.
4. Obtener UID desde el token.
5. Rechazar `user_id` enviado como sustituto de identidad.
6. Validar un idempotency/request ID cuando corresponda.
7. Ejecutar la matriz en orden transaccional cuando sea posible.
8. Eliminar objetos Storage antes de Auth si son propiedad del usuario.
9. Eliminar/anonymizar datos según la matriz.
10. Eliminar el usuario Auth con cliente administrativo.
11. No devolver stack traces.
12. Aplicar CORS mínimo necesario.
13. Registrar logs sanitizados.
14. No exponer secret/service role.
15. Documentar variables requeridas en `.env.example`, sin valores reales.

Si una parte no puede ser transaccional, documentar compensación y estado de fallo.

### 4.10 UI de eliminación

Flujo:

1. Información clara.
2. Confirmación destructiva.
3. Reautenticación con contraseña actual.
4. Checkbox de comprensión.
5. Texto de confirmación, por ejemplo `ELIMINAR`, si mejora seguridad.
6. Envío a Edge Function.
7. Loading no cancelable durante fase crítica.
8. Resultado de éxito o fallo recuperable.
9. Limpiar sesión local.
10. Volver al flujo público.

No usar mensajes amenazantes ni dark patterns.

---

## 5. Fuera de alcance

No implementar:

- OAuth social;
- MFA;
- biometría;
- passkeys;
- teléfono;
- recuperación por SMS;
- políticas de retención de módulos aún no desarrollados;
- M02;
- GPS, mapas o pagos;
- renombre del paquete;
- Hilt, Retrofit, NestJS o segunda base;
- lanzamiento release con textos legales en borrador.

---

## 6. Seguridad

- Secret/service role solo en Edge Function.
- Contraseña actual solo en memoria durante reautenticación.
- Sin tokens, emails completos, OTP o contraseñas en logs.
- JWT validado antes de toda operación privilegiada.
- UID derivado del token.
- RLS para tablas nuevas.
- CORS restringido.
- No devolver detalles internos.
- Eliminar Storage antes del usuario Auth si existen objetos de su propiedad.
- No confiar en metadata libre para operaciones de borrado.
- Toda acción destructiva debe ser idempotente o tener manejo explícito de reintento.

---

## 7. Pruebas requeridas

### 7.1 Unitarias

- detección de deep link confirmación;
- detección de deep link recovery;
- intent consumido una sola vez;
- password reset válida/inválida;
- sesión de recovery ausente;
- enlace expirado;
- cambio de contraseña;
- reautenticación correcta/incorrecta;
- logout limpia estado;
- `LegalConsentRequired`;
- aceptación pendiente;
- ViewModel de eliminación;
- mapper de errores Edge Function.

### 7.2 Mock

- recovery completo;
- cambio de password;
- login con password nueva;
- password anterior rechazada;
- eliminación borra identidad mock;
- sesión se cierra;
- reintento controlado.

### 7.3 SQL/RLS

Crear:

```text
/docs/04-calidad/M01-pruebas-recuperacion-y-eliminacion.md
```

Debe cubrir trigger corregido, consentimiento posterior, RLS, requests, cascades/anonymización, aislamiento entre usuarios, Storage de prueba, orden de eliminación y fallo parcial.

### 7.4 Edge Function

Probar:

- sin token;
- token inválido;
- método no permitido;
- UID del token;
- body con `user_id` malicioso ignorado/rechazado;
- usuario con datos mínimos;
- usuario con archivos;
- reintento;
- fallo controlado;
- respuesta sin secretos.

### 7.5 Manual/staging

- email de recuperación real;
- deep link en dispositivo;
- nueva contraseña;
- login con nueva contraseña;
- cierre de sesión;
- cambio de contraseña normal;
- eliminación de cuenta de prueba;
- confirmación de eliminación en Auth;
- eliminación/anonymización de datos;
- limpieza de Storage;
- legal consent gate.

No afirmar éxito remoto sin evidencia.

---

## 8. Orden de implementación

### Bloque 0 — Git y auditoría

- consolidar Etapa 3;
- crear rama limpia;
- revisar migración 014;
- crear matriz de eliminación;
- inventariar Storage/FKs;
- detenerse si falta estrategia.

### Bloque 1 — Consentimiento pendiente

- corregir trigger;
- estado `LegalConsentRequired`;
- consulta y aceptación posterior;
- RLS;
- pruebas;
- build/tests/lint.

### Bloque 2 — Recovery y reset

- deep link;
- `PasswordResetActive`;
- pantalla;
- update password;
- mock;
- pruebas;
- build/tests/lint.

### Bloque 3 — Seguridad y logout

- account security;
- cambio de contraseña;
- reautenticación;
- logout endurecido;
- token FCM si aplica;
- pruebas;
- build/tests/lint.

### Bloque 4 — Eliminación

- migración opcional de requests;
- Edge Function;
- UI;
- matriz aplicada;
- Storage;
- pruebas;
- build/tests/lint.

### Bloque 5 — Calidad y cierre

- staging/manual documentado;
- actualizar arquitectura;
- build/tests/lint final;
- crear cierre de etapa y cierre final M01.

---

## 9. Criterios de aceptación

- [ ] Etapa 3 consolidada en commit.
- [ ] Rama limpia sin WIP funcional.
- [ ] Migración 014 no se desplegó sin revisión.
- [ ] El trigger no inventa consentimiento.
- [ ] Alta de `public.users` permanece funcional.
- [ ] Existe `LegalConsentRequired`.
- [ ] Consentimiento posterior se guarda de forma segura.
- [ ] Deep link distingue confirmación y recovery.
- [ ] Recovery se procesa una sola vez.
- [ ] Nueva contraseña se actualiza realmente en Supabase.
- [ ] Mock implementa el mismo flujo.
- [ ] Cambio de contraseña exige reautenticación.
- [ ] Logout limpia estado sensible.
- [ ] Token FCM se desvincula si corresponde.
- [ ] Matriz de eliminación completa.
- [ ] Ninguna FK/bucket queda sin estrategia.
- [ ] Service role no aparece en Android.
- [ ] Edge Function obtiene UID desde JWT.
- [ ] Eliminación afecta solo al usuario autenticado.
- [ ] Storage se gestiona antes de borrar Auth.
- [ ] Datos actuales se borran o anonimizan según matriz.
- [ ] UI de eliminación no usa dark patterns.
- [ ] No se implementó M02.
- [ ] Tests anteriores siguen aprobados.
- [ ] Nuevos tests aprobados.
- [ ] `assembleDebug` aprobado.
- [ ] `lintDebug` con 0 errores.
- [ ] CI sin secretos.
- [ ] Pruebas remotas no verificadas se declaran honestamente.
- [ ] Textos legales de borrador bloquean release publicable.
- [ ] Se crearon ambos documentos de cierre.

---

## 10. Entregables de cierre

Crear exactamente:

```text
/docs/02-arquitectura/M01-etapa-4-cierre.md
/docs/02-arquitectura/M01-cierre-final.md
/docs/02-arquitectura/M01-matriz-eliminacion-de-cuenta.md
/docs/04-calidad/M01-pruebas-recuperacion-y-eliminacion.md
```

`M01-etapa-4-cierre.md` debe incluir rama y commits, corrección de consentimientos, deep links, reset, seguridad/logout, matriz y Edge Function, migraciones/RLS, pruebas, resultados, pruebas remotas, riesgos y deuda.

`M01-cierre-final.md` debe indicar funcionalidades M01 terminadas, qué está probado localmente, qué está probado en Supabase staging, condiciones pendientes para release, deuda aceptada y confirmación de si M02 queda habilitado o bloqueado.

---

## 11. Instrucción de parada

No iniciar M02.

Detenerse únicamente después de crear los cuatro entregables anteriores y esperar revisión explícita.
