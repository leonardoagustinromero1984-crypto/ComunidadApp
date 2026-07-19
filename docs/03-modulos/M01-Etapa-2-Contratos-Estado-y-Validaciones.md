# LEOVER — M01 Etapa 2: Contratos, estado y validaciones

**Módulo:** M01 — Identidad y Autenticación  
**Etapa:** 2 — Contratos, estado, validaciones y pruebas unitarias  
**Estado de entrada:** Auditoría inicial aprobada  
**Backend oficial:** Supabase Auth  
**Regla central:** extender la autenticación existente sin duplicarla ni reescribirla.

---

## 1. Documentos obligatorios

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/02-arquitectura/M00-cierre-final.md`
3. `/docs/02-arquitectura/M01-auditoria-inicial.md`
4. `/docs/03-modulos/M01-Identidad-y-Autenticacion.md`
5. `/docs/adr/ADR-0001-Supabase-como-backend-principal.md`
6. `/docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
7. `/docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md`
8. `/docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
9. `/docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
10. Este documento.

Los ADR vigentes y las decisiones aprobadas aquí reemplazan instrucciones históricas incompatibles.

---

## 2. Decisiones aprobadas

### D-M01-01 — Reutilizar la autenticación existente

No crear otro `AuthProvider`, otro cliente Supabase ni otro repositorio paralelo.

Se debe extender:

- `AuthRepository`;
- `MockAuthRepository`;
- `SupabaseAuthRepository`;
- `SessionViewModel`;
- ViewModels existentes;
- rutas y pantallas existentes en etapas posteriores.

### D-M01-02 — Estructura incremental

Mantener los paquetes actuales.

Se permite crear un dominio mínimo:

```text
domain/auth/
domain/auth/validation/
```

No mover masivamente pantallas, repositorios o ViewModels solo para coincidir con una estructura ideal.

### D-M01-03 — Mock seguro

El mock dejará de aceptar cualquier email desconocido.

Debe:

- utilizar cuentas fixture explícitas;
- rechazar credenciales desconocidas;
- permitir pruebas deterministas;
- conservar una cuenta demo documentada;
- implementar el mismo contrato que Supabase.

No romper previews ni CI.

### D-M01-04 — Contraseña mínima

La app validará una contraseña mínima de **8 caracteres**.

En esta etapa:

- centralizar la regla;
- eliminar mensajes locales que hablen de 6 caracteres;
- no cambiar el dashboard de Supabase desde código;
- documentar como verificación manual que la política remota debe ser compatible.

### D-M01-05 — AccountType se difiere a M02

M01 no debe pedir ni decidir roles de negocio.

En el registro:

- ocultar o desactivar la selección de `AccountType` en una etapa posterior de UI;
- el contrato de M01 no expone tipos de organización, profesional o comercio;
- si el código actual requiere un valor, usar únicamente `PERSON` como default técnico;
- no eliminar el modelo porque puede ser reutilizado en M02.

En Etapa 2 solo se desacopla el contrato de autenticación de `AccountType`; no rediseñar todavía la pantalla.

### D-M01-06 — Eliminación de cuenta

No crear en Etapa 2:

- tabla `account_deletion_requests`;
- Edge Function;
- migraciones;
- UI destructiva.

La decisión entre eliminación síncrona o solicitud persistida se tomará en Etapa 4 después de auditar todas las tablas reales.

### D-M01-07 — Capturas de pantalla

No activar `FLAG_SECURE` globalmente en M01.

Motivos:

- no sustituye una gestión segura de credenciales;
- puede afectar accesibilidad, soporte y gestores de contraseña;
- los campos de password deben permanecer enmascarados y no persistirse.

La decisión puede revisarse para pantallas futuras con información clínica o financiera.

### D-M01-08 — Deep links y dashboard

Mantener:

```text
com.comunidapp.app://login-callback
```

No cambiar scheme, host ni paquete.

Crear un checklist manual para verificar posteriormente en Supabase:

- redirect URL;
- confirmación de email;
- templates;
- recuperación;
- expiración;
- rate limits.

No afirmar que el dashboard está correctamente configurado sin comprobarlo.

### D-M01-09 — Consolidar M00 antes de implementar

Antes de cambiar código M01:

1. proteger el working tree;
2. consolidar los cambios aprobados de M00 Etapa 4 en su rama;
3. crear un commit identificable de cierre M00;
4. crear una rama limpia para M01 Etapa 2 desde ese estado;
5. no incorporar `wip/gps-mapas-pagos`.

Rama de trabajo requerida:

```text
m01/etapa-2-contratos-validaciones
```

---

## 3. Objetivo de esta etapa

Construir la base de dominio de autenticación sin completar todavía todos los flujos visuales.

Al finalizar deben existir:

- estados oficiales de autenticación;
- modelos de comandos y resultados;
- validadores reutilizables;
- errores de autenticación mapeados a `AppError`;
- repositorio actual extendido con contratos coherentes;
- mock endurecido;
- SessionViewModel preparado para estados más ricos;
- pruebas unitarias suficientes;
- build, tests y lint en verde.

---

## 4. Alcance autorizado

### 4.1 Estado de autenticación

Crear un modelo único y observable que contemple:

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

- puede implementarse como `sealed interface` o `sealed class`;
- no guardar contraseña ni token;
- incluir solo datos mínimos;
- `Authenticated` puede contener una proyección segura de identidad;
- errores mediante `AppError`;
- estados transitorios impiden envíos duplicados;
- no romper la navegación existente durante esta etapa.

### 4.2 Proyección segura de usuario

Crear un modelo de dominio mínimo, por ejemplo `AuthUser`, con:

- UUID;
- email opcional;
- email verificado;
- fecha técnica opcional si ya está disponible;
- ningún rol de negocio;
- ningún perfil social.

No duplicar el modelo completo `User`.

### 4.3 Validadores

Crear validadores puros y testeables para:

#### Email

- obligatorio;
- trim;
- lowercase;
- formato razonable;
- máximo 254 caracteres.

#### Contraseña

- obligatoria;
- mínimo 8 caracteres;
- confirmación coincidente;
- máximo compatible con el SDK actual;
- no persistir valor.

#### Consentimientos

- términos aceptados;
- privacidad aceptada;
- versiones no vacías.

La UI existente podrá seguir usando su lógica temporal, pero la Etapa 3 deberá migrar a estos validadores.

### 4.4 Comandos de autenticación

Definir modelos de entrada tipados, como mínimo:

```text
SignInCommand
SignUpCommand
RequestPasswordRecoveryCommand
ResetPasswordCommand
VerifyEmailCommand
```

No incluir `AccountType` en `SignUpCommand`.

Se permiten nombres alternativos si son más coherentes con el código actual.

### 4.5 Errores

Crear códigos internos:

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

Implementar un mapper que traduzca errores conocidos de Supabase a `AppError`.

Reglas:

- no depender únicamente de comparar mensajes en UI;
- si el SDK no expone códigos estructurados, encapsular las comparaciones en un único mapper;
- mensaje técnico separado del mensaje visible;
- no exponer existencia de cuentas;
- no registrar emails completos.

### 4.6 Repositorio

Extender el `AuthRepository` existente de manera compatible.

Objetivo del contrato:

- login;
- registro;
- solicitar recuperación;
- aplicar nueva contraseña;
- verificación;
- reenvío;
- usuario actual;
- observar sesión;
- logout.

En esta etapa:

- no implementar todavía Edge Function;
- no crear consentimientos remotos;
- se pueden introducir contratos o TODOs explícitos para Etapa 3/4;
- no dejar métodos falsamente exitosos en Supabase;
- el stub de reset remoto debe quedar claramente representado como no soportado o pendiente hasta Etapa 4, sin engañar a UI ni tests.

### 4.7 Mock seguro

Modificar el mock para:

- aceptar únicamente fixtures conocidas;
- verificar contraseña real del fixture;
- devolver `INVALID_CREDENTIALS` para datos incorrectos;
- soportar registro determinista;
- impedir emails duplicados;
- soportar verificación y recuperación simuladas;
- mantener datos solo en memoria;
- reiniciarse de forma controlada para tests.

Documentar cuenta demo sin usar una contraseña real de producción.

### 4.8 SessionViewModel

Evolucionar incrementalmente:

- observar el nuevo estado;
- mantener compatibilidad con el gate actual;
- no hacer una reescritura total de navegación;
- eliminar `runBlocking` en logout si puede hacerse sin romper el contrato;
- usar coroutines estructuradas;
- limpiar estado sensible;
- añadir pruebas de transiciones.

Si el cambio completo del gate implica riesgo alto, crear un adaptador temporal documentado.

---

## 5. Fuera de alcance

No implementar en esta etapa:

- cambios visuales completos de login/registro;
- checkboxes legales en pantalla;
- tabla `user_consents`;
- migraciones Supabase;
- Edge Function `delete-account`;
- eliminación de cuenta;
- reset remoto completo por deep link;
- navegación nueva;
- OAuth social;
- M02;
- mapas, GPS o pagos;
- Hilt, Retrofit, NestJS;
- renombre de paquete;
- refactor masivo.

---

## 6. Archivos esperados

La lista exacta debe adaptarse al repositorio auditado.

### Crear

Sugeridos:

```text
app/src/main/java/com/comunidapp/app/domain/auth/AuthState.kt
app/src/main/java/com/comunidapp/app/domain/auth/AuthUser.kt
app/src/main/java/com/comunidapp/app/domain/auth/AuthCommand.kt
app/src/main/java/com/comunidapp/app/domain/auth/AuthErrorCode.kt
app/src/main/java/com/comunidapp/app/domain/auth/AuthErrorMapper.kt
app/src/main/java/com/comunidapp/app/domain/auth/validation/AuthValidators.kt
```

Pruebas sugeridas:

```text
app/src/test/java/com/comunidapp/app/domain/auth/AuthValidatorsTest.kt
app/src/test/java/com/comunidapp/app/domain/auth/AuthErrorMapperTest.kt
app/src/test/java/com/comunidapp/app/data/repository/MockAuthRepositoryTest.kt
app/src/test/java/com/comunidapp/app/viewmodel/SessionViewModelTest.kt
```

### Modificar

Solo cuando sea necesario:

```text
AuthRepository.kt
SupabaseAuthRepository.kt
AuthProvider.kt
SessionViewModel.kt
LoginViewModel.kt
```

No modificar pantallas salvo una corrección mínima necesaria para compilar.

---

## 7. Orden de ejecución

### Bloque 0 — Consolidación M00 y Git

- ejecutar `git status`;
- proteger cambios;
- commit de M00 Etapa 4;
- crear rama limpia M01;
- confirmar que WIP funcional no está incluido;
- registrar SHA inicial.

### Bloque 1 — Modelos y validadores

- AuthState;
- AuthUser;
- commands;
- validadores;
- tests;
- build + tests + lint.

### Bloque 2 — Errores

- AuthErrorCode;
- mapper Supabase → AppError;
- mensajes visibles seguros;
- tests;
- build + tests + lint.

### Bloque 3 — Contrato y mock

- adaptar `AuthRepository`;
- endurecer mock;
- preservar modo remoto;
- tests;
- build + tests + lint.

### Bloque 4 — Sesión

- transición incremental de SessionViewModel;
- eliminar bloqueo de hilo cuando sea seguro;
- adapter de compatibilidad si se necesita;
- tests;
- build + tests + lint.

### Bloque 5 — Documentación y cierre

Crear:

```text
/docs/02-arquitectura/M01-etapa-2-cierre.md
```

Actualizar la auditoría con:

- decisiones implementadas;
- deuda restante;
- estado de tests;
- archivos modificados;
- preparación para Etapa 3.

---

## 8. Pruebas mínimas

### Validadores

- email vacío;
- trim/lowercase;
- email inválido;
- email >254;
- password <8;
- password válida;
- confirmación distinta;
- consentimientos incompletos;
- versiones vacías.

### Mapper

- credenciales inválidas;
- correo no verificado;
- email duplicado;
- rate limit;
- recuperación expirada;
- red;
- configuración;
- error desconocido.

### Mock

- login fixture correcto;
- password incorrecta;
- email desconocido;
- registro;
- registro duplicado;
- logout;
- restauración;
- verificación;
- recuperación.

### Sesión

- Initializing → Unauthenticated;
- Initializing → Authenticated;
- login exitoso;
- login con error;
- logout;
- sesión expirada;
- no doble submit;
- excepción mapeada.

---

## 9. Criterios de aceptación

- [ ] M00 quedó consolidado en un commit identificable.
- [ ] Rama M01 limpia sin GPS/mapas/pagos.
- [ ] No existe un segundo AuthProvider ni cliente Supabase.
- [ ] AuthState oficial implementado.
- [ ] AuthUser no contiene roles de negocio.
- [ ] SignUpCommand no contiene AccountType.
- [ ] Email y contraseña usan validadores centrales.
- [ ] Mínimo 8 caracteres aplicado al contrato.
- [ ] AuthErrorMapper usa AppError.
- [ ] Mock rechaza emails desconocidos.
- [ ] Mock y remoto mantienen el mismo contrato.
- [ ] Stub remoto no devuelve falso éxito.
- [ ] SessionViewModel tiene transiciones testeadas.
- [ ] No se crearon migraciones ni Edge Functions.
- [ ] No se desarrolló M02.
- [ ] Tests previos siguen aprobados.
- [ ] Nuevos tests aprobados.
- [ ] assembleDebug aprobado.
- [ ] lintDebug con 0 errores.
- [ ] CI no contiene secretos.
- [ ] Se creó M01-etapa-2-cierre.md.

---

## 10. Instrucción de parada

No iniciar Etapa 3.

Detenerse después de crear:

```text
/docs/02-arquitectura/M01-etapa-2-cierre.md
```

Presentar:

- rama y commits;
- archivos creados/modificados;
- pruebas agregadas;
- resultados build/tests/lint;
- decisiones;
- riesgos;
- deuda para Etapa 3;
- checklist.
