# D08-14 — Perfil de mascota, Salud y Red de cuidado

**Versión:** 1.0 · **Fecha:** 2026-08-06 · **Estado:** RC1.2 (pendiente re-prueba física)  
**Marca:** LeoVer

---

## Estructura del detalle

1. Cabecera: foto, nombre, especie/raza, edad, estado actual, responsable principal (nombre completo), menú ⋮.
2. Resumen / datos básicos.
3. Salud y cuidados.
4. Pasaporte.
5. Red de cuidado.
6. Notas clínicas (si aplica).

No se muestran en primer plano: historial técnico, archivar, fallecimiento, códigos Mxx, microchip, IDs.

---

## Pasaporte

- Estado vacío: título «Pasaporte de [nombre]», texto humano, CTA **Crear pasaporte**.
- Sin permiso: mensaje de red de cuidado (sin M08).
- Si existe: ver / editar; no crear duplicado.
- Microchip no se solicita ni se muestra.

---

## Salud y cuidados

- Vacío: «Todavía no agregaste información de salud» + **Agregar información**.
- Con datos: botón **Editar**.
- Editable por responsable principal o red de cuidado con permiso.
- No bloquear la pantalla por ausencia de registro inicial.

Secciones soportadas (modelos existentes): generales, vacunas, medicación/antiparasitarios, peso, controles, recordatorios, observaciones.  
Pendiente sin inventar backend: alergias/condiciones como módulo propio si no hay modelo.

---

## Red de cuidado

Única sección (reemplaza la duplicación visual de «Responsables y custodias» + «Personas autorizadas» en el detalle):

- Título: **Red de cuidado**
- Subtítulo: personas que colaboran en el cuidado de [nombre].
- Contenido: responsable principal + personas de confianza / ayuda temporal.
- Acciones: agregar, editar permisos (vía flujo existente), quitar de la red.
- No usar «custodia» como etiqueta principal.

---

## Responsable principal

- Resolver ID → Nombre Apellido vía repositorio/ViewModel.
- Loading: «Cargando responsable» / indicador discreto.
- Fallback: «No pudimos cargar esta persona» (nunca UUID/login/código).

---

## Historial de estados

- En el detalle: solo estado actual (+ fecha si aporta).
- Historial completo: fuera del primer plano (ruta secundaria / staff). Datos persistidos.

---

## Archivar perfil

- Menú ⋮ → Administrar perfil → **Archivar perfil** (no acción primaria).
- Copy: el perfil deja de aparecer entre activas; conserva información; se puede restaurar.
- Reversible vía **Restaurar** cuando `canRestore` y estado ARCHIVED (no adoptada).

---

## Informar fallecimiento

- Menú de administración / información importante (no botón rojo primario).
- Lenguaje empático + confirmación explícita.
- Conserva historial; deja de aparecer en flujos activos.
- Modo memorial: evolución futura (no implementado).

---

## Microchip (legado)

- Eliminado de UI/formularios (perfil, pasaporte, salud, listados).
- Campo DTO/BD conservado; sin migración destructiva.
- Documentado como deprecado en experiencia de producto.

---

## Permisos

Se respetan capabilities existentes (`canUpdate`, `canManageHealth`, `canArchive`, `canRestore`, `canMarkDeceased`, etc.). No se amplían privilegios automáticamente.
