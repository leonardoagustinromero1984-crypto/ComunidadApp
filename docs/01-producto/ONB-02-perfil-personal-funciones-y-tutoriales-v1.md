# ONB-02 — Perfil personal, multifunción y tutoriales contextuales

**Versión:** 1.1  
**Fecha:** 17 de agosto de 2026  
**Estado:** CANONICAL PRODUCT DECISION  

UX-06 visible paths:
- SWITCH_CONTEXT = Perfil → Usar LeoVer como
- ADD_FUNCTION = Perfil → Configuración → Agregar función o perfil  
**Código:** ONB-02 + CONTEXT-01  
**Producto:** LeoVer  
**Fuentes reconciliadas:** Documento Maestro v1.2 · D01 v1.3 · REBASE-03B · REBASE-03C · ADR-016 · código vigente de Auth / PERSON / ActiveContext  

Este documento **no** reescribe informes históricos. Es la decisión de producto canónica para el onboarding multifunción.

---

## 1. Decisiones canónicas (flags)

```
VISIBLE_BASE_PROFILE_NAME = Perfil personal
PERSON_INTERNAL_IDENTITY = YES
PROFILE_PERSONAL_ALWAYS_ACTIVE = YES
PROFILE_PERSONAL_REMOVABLE = NO
PROFILE_PERSONAL_EDITABLE_SELECTION = NO

MULTI_FUNCTION_SELECTION = YES
ZERO_EXTRA_FUNCTIONS_ALLOWED = YES
MULTIPLE_FUNCTIONS_ALLOWED = YES
FUNCTIONS_ADDABLE_LATER = YES

TUTORIAL_BEFORE_SELECTION = YES
SPECIFIC_TUTORIALS_AFTER_SELECTION = YES
TUTORIALS_SKIPPABLE = YES
TUTORIALS_REOPENABLE = YES

ACCOUNT_TYPE_MODEL = NO
APPMODE_MODEL = NO
ACTIVE_CONTEXT_SECURITY_AUTHORITY = NO
```

---

## 2. Terminología — decisión final

| Plano | Nombre | Uso |
|---|---|---|
| Identidad interna | PERSON | Persistencia, RLS, RPC, membresías, grants |
| Nombre visible de producto | **Perfil personal** | Toda UX normal |
| Contexto operativo | ActiveContext | Solo UX / navegación |

La UX normal **no** presenta «Persona» como tipo de cuenta ni como categoría seleccionable.

### PROFILE_PERSONAL

- Siempre existe.
- Siempre está activo.
- No se puede desmarcar.
- No se puede eliminar.
- No se puede desactivar.
- **No** es un `AccountType`.
- **No** es una capability.
- Es la base de identidad humana.

Una cuenta humana = **una** PERSON. Varias funciones simultáneas no crean otra cuenta.

---

## 3. Prohibiciones de arquitectura

**No** implementar la selección como:

- `AccountType`
- `AppMode`
- un único tipo de perfil
- un único rol sobre PERSON
- `user_type`
- `account_category`
- otro enum monolítico de identidad

La UI de selección **no** es autoridad de seguridad.

Las capacidades reales derivan de entidades canónicas existentes:

- PERSON
- membresías de organización
- perfiles profesionales / prestadores
- responsabilidad de mascota
- grants
- RLS / RPC

ActiveContext **no** es:

- autoridad de identidad
- autoridad de permisos
- autoridad de membresía
- autoridad de mascota
- autoridad de VitaCora

Cambiar de contexto **no** concede permisos.

---

## 4. Orden canónico de onboarding (después de Auth)

```
signup
→ verificación email OTP
→ sesión válida
→ cargar / provisionar PERSON canónica
→ completar solo campos básicos faltantes del perfil
→ T00_MULTI_FUNCTION_INTRO (antes del selector)
→ selección múltiple de funciones
→ configurar funciones seleccionadas donde el dominio lo soporte
→ T01_PROFILE_PERSONAL (todos)
→ tutoriales solo de las funciones seleccionadas
→ T11_USE_LEOVER_AS si hay más de un contexto usable
→ Home
```

El usuario debe entender el concepto multifunción **antes** de que se le pida qué funciones elegir.

Usuarios existentes: no se destruye ni se reinicia el onboarding. No se borran username, ubicación, membresías, prestadores, organizaciones, relaciones de mascota ni historial de tutoriales. Si nunca vieron T00 v1, se puede mostrar **una vez**, omitible.

---

## 5. Selector — «¿Cómo querés usar LeoVer?»

**Título:** ¿Cómo querés usar LeoVer?  
**Subtítulo:** Elegí una o varias opciones. Podés agregar otras más adelante.

Arriba, no editable:

```
✓ Perfil personal
Tus mascotas, VitaCora y la comunidad.
Siempre activo
```

El check visual **no** es un checkbox editable. No se puede desmarcar.

Opciones adicionales (cero, una o varias):

1. Rescatista  
2. Hogar de tránsito  
3. Profesional veterinario  
4. Paseador  
5. Cuidador  
6. Educador / Adiestrador  
7. Peluquería  
8. Guardería  
9. Represento una organización o negocio  

Ejemplo válido de **una** cuenta humana:

Perfil personal + Rescatista + Paseador + Profesional veterinario.

Si se omite T00, el selector **sigue** explicando:

- se pueden elegir varias opciones;
- se pueden agregar funciones más adelante;
- Perfil personal permanece siempre activo.

Omitir un tutorial **nunca** crea ambigüedad de producto.

---

## 6. Dominio canónico por función (auditoría)

| Opción | Dominio | Estado | Activación |
|---|---|---|---|
| Perfil personal | `persons` | IMPLEMENTED_CANONICAL_DOMAIN | Siempre presente |
| Rescatista | `person_capabilities.capability = RESCUER` (RESCUE-01) | IMPLEMENTED_CANONICAL_DOMAIN | PERSON + capability. No es autoridad de seguridad |
| Hogar de tránsito | `foster_profiles` | IMPLEMENTED_CANONICAL_DOMAIN | Usar flujo foster existente |
| Profesional veterinario | `professional_profiles` + afiliación a clínica | IMPLEMENTED_CANONICAL_DOMAIN | Usar dominio profesional existente |
| Paseador | `service_providers` (holder PERSON) | IMPLEMENTED_CANONICAL_DOMAIN | Setup de prestador existente |
| Cuidador | `service_providers` | IMPLEMENTED_CANONICAL_DOMAIN | Setup de prestador existente |
| Educador / Adiestrador | `service_providers` | IMPLEMENTED_CANONICAL_DOMAIN | Setup de prestador existente |
| Peluquería | `service_providers` | IMPLEMENTED_CANONICAL_DOMAIN | Setup de prestador existente |
| Guardería | `service_providers` + estadías (M23/daycare) | IMPLEMENTED_CANONICAL_DOMAIN | Setup pendiente hasta completar prestador |
| Organización / negocio | `organizations` + membership | IMPLEMENTED_CANONICAL_DOMAIN | Crear o unirse. Nunca segunda cuenta humana |

Si el dominio no existe, el selector y el tutorial pueden existir, pero la activación es **intent / pending setup**. No se inventa un módulo de producción ni se concede seguridad.

---

## 7. Organización / negocio

Si elige «Represento una organización o negocio»:

**¿Qué querés hacer?**

- Crear una organización  
- Unirme a una existente  

Luego, donde esté soportado:

- Veterinaria  
- Refugio / ONG  
- Tienda  
- Guardería  
- Otro servicio  

Una veterinaria, tienda, refugio, ONG o guardería **no** es otra cuenta humana. Es **ORGANIZATION**. La persona sigue siendo PERSON y actúa por membresía / capability.

**Nunca** implementar contraseñas compartidas de organización.

V1 de Tienda: perfil comercial / directorio, ubicación, contacto, presentación de servicios o productos si existe. **No** carrito, checkout, pedidos ni marketplace transaccional.

---

## 8. Agregar funciones más adelante

Ruta descubrible:

```
Perfil → Usar LeoVer como
Perfil → Configuración → Agregar función o perfil
```

Reutiliza el mismo sistema de selección / configuración.

Si agrega Paseador seis meses después: mostrar `T05_WALKER` y `T11` solo si ahora es necesario.

**No** repetir `T00`, `T01` ni tutoriales no relacionados, salvo política explícita de versión.

---

## 9. Biblioteca de tutoriales

```
Perfil → Ayuda → Tutoriales
```

Los tutoriales se pueden reabrir. El progreso por usuario / tutorial / versión registra:

- `tutorial_id`
- `version`
- `viewed`
- `skipped`
- `completed`

El progreso de tutorial **no** es consentimiento legal. No otorga permisos. No reemplaza Términos ni privacidad. No bloquea el acceso a funciones para siempre.

Schema canónico existente: `public.tutorial_progress` (migración 1004). No modificar migraciones ya aplicadas. No crear migración forward salvo necesidad real de RPC/RLS. El cliente puede persistir localmente y reutilizar la tabla cuando exista consumidor seguro.

---

## 10. Reglas de tutoriales

Todos los tutoriales son:

- omitibles
- reabrables
- no bloqueantes

No deben:

- otorgar permisos
- ser autoridad de seguridad
- reemplazar aceptación de Términos
- reemplazar consentimiento de privacidad
- bloquear el acceso a una función para siempre

Cambiar de contexto **no** vuelve a reproducir su tutorial.

---

## 11. Navegación contextual (intención acordada)

No implementar módulos faltantes solo para pintar navegación.

| Contexto | Tabs |
|---|---|
| Perfil personal | Inicio · Sumate · ＋ Publicar · Comunidad · Perfil |
| Refugio / ONG | Inicio · Animales · ＋ Publicar · Gestión · Perfil |
| Hogar de tránsito | Inicio · Tránsitos · ＋ Publicar · Solicitudes · Perfil |
| Veterinario | Inicio · Agenda · ＋ Publicar · Consultorio · Perfil |
| Prestador | Inicio · Agenda · ＋ Publicar · Mi servicio · Perfil |
| Guardería | Inicio · Reservas · ＋ Publicar · Huéspedes · Perfil |

---

## 12. Visual

```
GENERAL_LIGHT_BACKGROUND = #FFFDF8
SURFACE = #FFFFFF
SOFT_CREAM_ACCENT = #FFF8E1
PROFILE_GREEN = #66B978
MAIN_BRAND_GREEN = #49B749
HOGAR_DE_TRANSITO_MODEL = PERSONAL_FUNCTION_NOT_ORGANIZATION
COUNTRY_UI_V1 = HIDDEN_FIXED_ARGENTINA
ONBOARDING_FUNCTION_CATALOG != ACTIVE_CONTEXT_LIST
ORGANIZATION_CREATION = ATOMIC_CREATION_PLUS_ADMIN_MEMBERSHIP
COMMUNITY_UI = CURRENT_CANONICAL_CARD_AND_CHIP_DESIGN
```

No cambiar el logo. No reemplazar globalmente el verde de marca.

UI de tutoriales: data-driven (`TutorialDefinition` / `TutorialStep`). T00 tiene 5 pantallas. No una pantalla custom por tutorial.

---

## 13. Catálogo de tutoriales y copy

### T00_MULTI_FUNCTION_INTRO — VERSION = 1

Se muestra a **todas** las personas **antes** del selector. Omitible. 5 pantallas.

**Pantalla 1** — Todo lo de tu mascota, en un mismo lugar  
Perfil personal, mascotas y comunidad. Chips: Mascotas · Comunidad · Publicaciones.

**Pantalla 2** — Su vida también tiene una historia  
VitaCora: identidad, cuidados, salud y momentos. Descriptor: «Su vida. Su historia. Sus cuidados.» No es historia clínica oficial.

**Pantalla 3** — La comunidad puede ayudar  
Perdidos, encontrados, adopciones, rescates y hogares de tránsito.

**Pantalla 4** — Encontrá servicios cerca tuyo  
Veterinarias, paseadores, guarderías, peluquerías, educadores y perfiles comerciales. Sin marketplace transaccional.

**Pantalla 5** — Una cuenta, muchas formas de usar LeoVer  
Perfil personal siempre existe. Se pueden sumar rescatista, hogar de tránsito, profesional veterinario, paseador, cuidador, educador, peluquería, guardería u organización. CTA: Elegir cómo quiero usar LeoVer.

---

### T01_PROFILE_PERSONAL — VERSION = 1

Se muestra a **todas** las personas después de la selección.

**Pantalla 1**  
Título: Tu espacio en LeoVer  

Tu Perfil personal es tu espacio principal en LeoVer. Desde acá podés participar de la comunidad, publicar, seguir perfiles y administrar tus mascotas.

**Pantalla 2**  
Título: Tus mascotas y VitaCora  

Cada mascota conserva su identidad y su VitaCora, donde puede reunirse su historia, cuidados, salud y otra información importante.

Vos decidís qué información compartir y con quién.

**Pantalla 3**  
Título: Tu perfil puede crecer con vos  

Si más adelante empezás a rescatar animales, ofrecer servicios, trabajar como profesional o representar una organización, no necesitás otra cuenta.

Podés agregarlo desde Perfil → Configuración → Agregar función o perfil.

CTA: Entendido

---

### T02_RESCUER

Solo si Rescatista fue seleccionado o activado después.

**Pantalla 1**  
Título: Organizá tus rescates  

La función Rescatista te permite participar en casos de animales que necesitan ayuda y mantener mejor organizada tu actividad de rescate.

**Pantalla 2**  
Título: Un animal, una misma identidad  

Si una mascota ya existe en LeoVer, su identidad no se duplica por participar de un rescate. LeoVer busca mantener su historia conectada.

**Pantalla 3**  
Título: Rescatar no cambia quién sos  

Seguís teniendo tu Perfil personal. Cuando quieras trabajar con tus herramientas de rescate, cambiás a Rescatista desde “Usar LeoVer como”.

CTA: Empezar como rescatista

---

### T03_FOSTER

**Pantalla 1**  
Título: Acompañá durante el tránsito  

Como hogar de tránsito podés ayudar temporalmente a una mascota mientras espera volver con su familia, ser adoptada o continuar su proceso de cuidado.

**Pantalla 2**  
Título: Cuidar no significa ser dueño  

LeoVer diferencia la responsabilidad permanente de la custodia temporal.

Podés cuidar a una mascota durante un tránsito sin modificar quiénes son sus responsables.

**Pantalla 3**  
Título: Su historia puede continuar  

Los cuidados o acontecimientos relevantes del tránsito pueden incorporarse a VitaCora cuando corresponda y con la autorización adecuada.

CTA: Empezar como hogar de tránsito

---

### T04_VETERINARY_PROFESSIONAL

**Pantalla 1**  
Título: Tu actividad profesional en LeoVer  

Podés crear tu perfil profesional veterinario y también vincularte con una veterinaria en la que trabajes.

No necesitás crear otra cuenta.

**Pantalla 2**  
Título: Acceso con autorización  

La información de salud y VitaCora pertenece al ámbito protegido de la mascota.

Solo vas a acceder a la información que su responsable haya autorizado para la atención.

**Pantalla 3**  
Título: Información con origen claro  

Cuando aportes información profesional, LeoVer registra quién la aportó y su procedencia.

La información propuesta por terceros no reemplaza silenciosamente los datos de la mascota.

**Pantalla 4**  
Título: Personal o institucional  

Podés trabajar con tu perfil profesional independiente o actuar como miembro de una veterinaria.

LeoVer conserva siempre quién fue la persona que realizó cada acción.

CTA: Configurar perfil profesional

---

### T05_WALKER

**Pantalla 1**  
Título: Ofrecé tus paseos  

Creá tu perfil como paseador para que otras personas puedan conocer tus servicios, zona de trabajo e información profesional.

**Pantalla 2**  
Título: La información necesaria, nada más  

Cuando atiendas a una mascota vas a acceder únicamente a la información necesaria y a los permisos que su responsable haya otorgado.

**Pantalla 3**  
Título: Seguís teniendo tu Perfil personal  

Cuando quieras usar las herramientas de paseador, cambiá desde Perfil → Usar LeoVer como.

CTA: Configurar como paseador

---

### T06_CAREGIVER

**Pantalla 1**  
Título: Ofrecé cuidado responsable  

Mostrá tu perfil, experiencia y los servicios de cuidado que ofrecés para que otras personas puedan encontrarte.

**Pantalla 2**  
Título: Cada mascota tiene necesidades distintas  

Las instrucciones, cuidados y accesos que recibas corresponden únicamente a esa mascota y a esa relación de cuidado.

**Pantalla 3**  
Título: Privacidad siempre separada  

Ser cuidador no te da acceso automático a VitaCora, salud ni información privada. El responsable decide qué compartir.

CTA: Configurar como cuidador

---

### T07_TRAINER

**Pantalla 1**  
Título: Mostrá tu trabajo profesional  

Creá tu perfil para ofrecer educación, entrenamiento o acompañamiento conductual.

**Pantalla 2**  
Título: Seguimientos claros  

Podrás mantener organizada la relación con cada mascota y aportar información cuando la función correspondiente lo permita.

**Pantalla 3**  
Título: La mascota conserva una sola historia  

Tu intervención puede formar parte de su recorrido sin crear perfiles duplicados ni apropiarte de su información.

CTA: Configurar mi servicio

---

### T08_GROOMING

**Pantalla 1**  
Título: Mostrá tus servicios  

Configurá tu perfil para que la comunidad pueda encontrar tus servicios de peluquería y cuidado estético.

**Pantalla 2**  
Título: Información útil para atender mejor  

Cuando corresponda, el responsable puede compartir indicaciones necesarias para atender a su mascota de forma segura.

**Pantalla 3**  
Título: Solo lo necesario  

La contratación de un servicio no da acceso automático a información privada, VitaCora o salud.

CTA: Configurar mi servicio

---

### T09_DAYCARE

**Pantalla 1**  
Título: Gestioná el cuidado durante la estadía  

La función Guardería está pensada para organizar mascotas alojadas temporalmente y mantener claros sus cuidados durante la permanencia.

**Pantalla 2**  
Título: Entrada, estadía y salida  

Cada estadía puede tener un inicio y un cierre claros, junto con instrucciones y acontecimientos relevantes.

**Pantalla 3**  
Título: Acceso operativo ≠ acceso total  

Los datos necesarios para cuidar a una mascota durante una estadía son independientes del acceso completo a VitaCora.

El responsable decide qué compartir.

**Pantalla 4**  
Título: Fotos y VitaCora también son decisiones separadas  

Autorizar el cuidado, permitir contenido público y guardar información de la estadía en VitaCora son elecciones independientes.

CTA: Configurar guardería

---

### T10_ORGANIZATION

**Pantalla 1**  
Título: La organización tiene identidad propia  

Una veterinaria, refugio, ONG, tienda, guardería u otro negocio no es una segunda cuenta personal.

Es una organización independiente dentro de LeoVer.

**Pantalla 2**  
Título: Las personas forman parte de ella  

Cada integrante usa su propia cuenta y puede recibir diferentes permisos dentro de la organización.

Nunca hace falta compartir una contraseña entre empleados o colaboradores.

**Pantalla 3**  
Título: Sabemos quién hizo cada acción  

Aunque estés usando LeoVer como organización, las acciones importantes conservan también la identidad de la persona que las realizó.

**Pantalla 4**  
Título: Cambiá fácilmente de contexto  

Podés volver a tu Perfil personal o cambiar a otra función desde Perfil → Usar LeoVer como.

CTA: Crear o vincular organización

---

### Micro-tutoriales de organización

**T10A_VETERINARY_CLINIC** — Veterinaria  
Pacientes con autorización; profesionales y miembros; agenda; acceso protegido a salud / VitaCora.

**T10B_SHELTER** — Refugio / ONG  
Animales bajo responsabilidad de la organización; adopción; rescate; miembros; apoyo de la comunidad.

**T10C_SHOP** — Tienda  
Perfil comercial / directorio; ubicación; contacto; presentación de servicios o productos si está soportado.  
No presentar carrito, checkout, pedidos ni marketplace transaccional como funcionalidad V1.

**T10D_ORG_DAYCARE** — Guardería (organización)  
Reservas / estadías; huéspedes; instrucciones; permisos.

**T10E_OTHER_SERVICE** — Otro servicio  
Perfil; categoría; ubicación; herramientas de servicio disponibles.

---

### T11_USE_LEOVER_AS

Se muestra automáticamente cuando el onboarding termina con **más de un** contexto usable.

**Pantalla 1**  
Título: Ahora tenés más de una forma de usar LeoVer  

Todas pertenecen a la misma cuenta. No necesitás cerrar sesión para cambiar.

**Pantalla 2**  
Título: Usar LeoVer como  

Desde Perfil podés elegir con qué perfil o función querés usar LeoVer en ese momento.

Ejemplo conceptual:

```
Leonardo
Perfil personal

Leo Paseos
Paseador

Veterinaria Patitas
Veterinaria

+ Agregar función o perfil
```

**Pantalla 3**  
Título: Siempre podés volver  

Cambiar de perfil o función no elimina información ni cambia tu identidad.

Podés volver a tu Perfil personal cuando quieras.

CTA: Ir a LeoVer

---

## 14. Relación con documentos vigentes

- Maestro v1.2: una identidad humana; capacidades contextuales; tutoriales skippable/reopenable; skip ≠ consentimiento.
- D01 v1.3: no `AccountType`; ActiveContext no concede permisos; Rescatista = PERSON + capacidad contextual.
- REBASE-03B: `tutorial_progress`; ActiveContext = UX; PERSON ≠ organización ≠ prestador.
- CONTEXT-01: ActiveContext es navegación. Los permisos viven en PERSON, memberships, profiles, responsibility, grants, RLS/RPC.

---

## 15. Criterios de aceptación

```
PROFILE_PERSONAL_ALWAYS_ACTIVE = PASS
PROFILE_PERSONAL_CANNOT_BE_UNCHECKED = PASS
ZERO_ADDITIONAL_FUNCTIONS_ALLOWED = PASS
ONE_ADDITIONAL_FUNCTION_ALLOWED = PASS
MULTIPLE_ADDITIONAL_FUNCTIONS_ALLOWED = PASS
T00_BEFORE_FUNCTION_SELECTION = PASS
T00_SKIP_DOES_NOT_HIDE_MULTISELECT_EXPLANATION = PASS
PROFILE_PERSONAL_TUTORIAL_FOR_ALL = PASS
ONLY_SELECTED_FUNCTION_TUTORIALS_SHOWN = PASS
ADDING_FUNCTION_LATER_SHOWS_NEW_TUTORIAL_ONLY = PASS
TUTORIAL_REOPEN_FROM_HELP = PASS
TUTORIAL_SKIP_NON_BLOCKING = PASS
MULTIPLE_CONTEXTS_SHOW_T11 = PASS
SINGLE_CONTEXT_DOES_NOT_UNNECESSARILY_SHOW_T11 = PASS
ACTIVE_CONTEXT_SECURITY_AUTHORITY = NO
ACCOUNT_TYPE_RUNTIME_AUTHORITY = 0
APPMODE_RUNTIME_AUTHORITY = 0
EXISTING_USER_T00_SHOWS_FUNCTION_CATALOG = PASS
FOSTER_IS_PERSONAL_NOT_ORGANIZATION = PASS
```

---

## 16. Addendum UX-03 / CONTEXT-02 / COMMUNITY-02 / ORG-02 (2026-08-16)

No reescribe informes históricos.

- El catálogo inicial «¿Cómo querés usar LeoVer?» no se construye desde contextos activos.
- «Usar LeoVer como» lista contextos activos + Agregar función o perfil.
- Hogar de tránsito es función personal (`foster_profiles`); no usa el formulario de organización.
- País ISO oculto en UX V1; internamente Argentina (`AR`).
- Creación de organización: RPC atómica + membresía OWNER; retry idempotente por slug del creador.
- Community: chips verdes, cards blancas, datos canónicos; vacío real si el backend no trae filas.
