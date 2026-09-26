# LeoVer Design System 2.0

**Fuente de verdad visual** para Android y Web futura.  
**Producto:** LeoVer · **Mascota / historia:** VitaCora (*vita* = vida, *cora* = corazón).

Este documento describe **presentación**. No define backend, permisos, RLS ni reglas de negocio.

---

## Principios

1. **Limpieza.** El contenido vive sobre blanco / off-white. Casi nunca una card dentro de otra card.
2. **Acentos, no superficies.** Naranja LeoVer y verde LeoVer pintan acción, estado y selección — no fondos enteros.
3. **Social, no administrativo.** Avatar y media mandan. Metadata es secundaria.
4. **Una acción primaria.** El resto es outline, texto o icono.
5. **Identidad propia.** Inspiración de ritmo (Instagram / WhatsApp): no layouts, logos ni iconos de esas marcas.
6. **Light forzado.** Nunito Sans global. Sin dark mode de producto.

---

## 1. Colors

| Token | Hex | Uso |
|---|---|---|
| `BrandOrange` | `#FF7A00` | CTA primaria, like activo, tab seleccionado, story ring |
| `BrandOrangeDeep` | `#E56E00` | Texto sobre soft orange, pressed |
| `BrandOrangeSoft` | `#FFA64D` | Iconos de acento suaves |
| `BrandOrangeContainer` | `#FFE8CC` | Soft fill (chips, burbuja propia, like wash) |
| `BrandGreen` | `#49B749` | Éxito, verificado, estado positivo |
| `BrandGreenDark` | `#247A3D` | Texto sobre green soft |
| `BrandGreenContainer` | `#E3F5E3` | Soft fill de éxito |
| `ProfileGreen` | `#66B978` | Solo acento de perfil / rating — no reemplaza BrandGreen |
| `BrandBackground` | `#FAFBF8` | Fondo de pantalla |
| `BrandWhite` | `#FFFFFF` | Superficie, top/bottom bars |
| `BrandCream` | `#FFF8E1` | Highlight puntual — **nunca** full-screen |
| `BrandText` | `#263238` | Texto principal |
| `BrandTextSecondary` | `#667085` | Metadata, captions |
| `NeutralBorder` / hairline | `#E5EAE4` | Separadores 0.5–1 dp |
| VitaCora section divider | `#B4BAB2` | Hairline de sección operativa, un punto más visible que el hairline global |
| `UrgentRed` | `#E53935` | Error / perdido / urgente |

**Regla:** si un bloque entero es naranja o verde, está mal. Soft containers son lavados, no branding de página.

Paleta activa Compose: `LeoVerVisualPalette.v3Experimental` (Design System 2.0).  
`leoVisual().primary` = naranja (CTA). `leoVisual().secondary` = verde (éxito).

---

## 2. Typography

Familia: **Nunito Sans**.

| Estilo | Peso | Tamaño | Uso |
|---|---|---|---|
| Display | 700 | 28 | Hero / auth grande |
| Screen title (`LeoPageTitle`) | 600 | 22 | Títulos de pantalla |
| Section title | 600 | 18 | Secciones |
| Card / list title | 600 | 16 | Nombre en fila, autor |
| Body | 400 | 15 | Párrafos |
| Secondary | 400 | 14 | Apoyo |
| Caption | 400 | 13 | Timestamp, metadata |
| Button | 600 | 16 | Labels de botón |
| Nav | 600 | 12 | Bottom bar |

Evitar ExtraBold salvo wordmark excepcional. Evitar Bold en metadata.

---

## 3. Spacing

Escala: **4 / 8 / 12 / 16 / 20 / 24 / 32**.

| Token | dp |
|---|---|
| `SpaceXs` | 4 |
| `SpaceS` | 8 |
| `SpaceM` | 12 |
| `SpaceL` | 16 |
| `Space20` | 20 |
| `SpaceXl` | 24 |
| `SpaceXxl` | 32 |

Pantalla: padding horizontal 16. Separación entre bloques 20–24. Entre filas de lista 0 + hairline, o 8.

---

## 4. Corner radii

| Token | dp | Uso |
|---|---|---|
| Small | 8 | chips internos, badges |
| Medium / chip | 12 | chips, botones compactos |
| Field | 14 | inputs |
| Card | 16 | media suave, sheets, dialogs |
| Large | 22 | feature rara vez |
| Pill | 999 | avatares, status chips |

No redondear todo a pill. Media de feed: 0 (edge) o 8 máximo.

---

## 5. Elevation

Default: **0**.  
Hairline > sombra.  
Elevación 1 solo en bottom sheet / dialog / snackbar.  
Nunca 2+ en feed, listas o perfil.

---

## 6. Iconography

- Material outlined para nav e iconos sociales; filled solo en estado activo (like, save, tab).
- Tamaños: 20 (inline), 22–24 (acciones), 28 (empty).
- **Sin círculo de fondo** salvo significado (story +, badge, add-pet dashed).
- Peso visual consistente. No mezclar sets ilustrados.

---

## 7. Buttons

| Tipo | Look | Cuándo |
|---|---|---|
| Primary | Fill naranja, texto blanco, radio 16, min 48–52 | Una sola acción real |
| Secondary | Outline hairline, texto oscuro | Alternativa |
| Tertiary | Texto 600, naranja o verde según semántica | Acciones de apoyo |
| Icon | 48×48 touch, icono 22–24, sin caja | Like, comentar, share, save |

No usar primary naranja para cada fila.

---

## 8. Inputs

- `LeoTextField` / `LeoSearchBar`.
- Fondo blanco, borde hairline, focus naranja suave.
- Altura ~52. Label + helper + error debajo.
- Sin outlines gruesos ni fill cream.

---

## 9. Cards

Card es **opcional**. Usar solo cuando el bloque necesita agrupación (admin resumen, health group, diálogo).

Card 2.0: superficie blanca, **sin borde**, **sin sombra**, padding 16.  
Si no agrupa, no es card: es sección + hairline.

---

## 10. List rows

Patrón: avatar/icono 48–56 · nombre 600 · detalle caption · acción contextual.  
Padding vertical 12, horizontal 16. Hairline inferior. Touch min 48.

Componentes: `LeoSettingsRow`, `V2NavRow` (plano), filas de chat / manada.

---

## 11. Avatars

- Siempre redondos.
- Feed / listas: 40–52. Perfil persona: 84. Historia: 64.
- Story ring **solo** si `StoryRingPolicy` / story activa. Naranja LeoVer.
- Sin borde decorativo permanente.

---

## 12. Media

- Feed: protagonista, casi edge-to-edge, sin deformar.
- POST video en feed: viewport social **4:5**, crop visual (`ContentScale.Crop` / `RESIZE_MODE_ZOOM`). No letterbox negro a los lados. No stretch.
- POST video al tocar: viewer con ratio original (`FIT`). El crop no copia el archivo.
- Foto: 1:1. Reel en feed: 9:16 contenido, player limpio.
- Multiphoto: indicador `1/n` discreto, oscuro 55% + caption blanca.
- Radio 0 en feed; 16 en thumbs de comunidad / mascota hero.

---

## 13. Chips

Unselected: superficie + hairline.  
Selected: `BrandOrangeContainer` + texto `BrandOrangeDeep` — no fill sólido naranja/verde.

---

## 14. Badges

Pill soft (green / orange / urgent). Texto 12–13, 600.  
Solicitudes: badge en tab, no banner.

---

## 15. Top bars

Fondo background/white. Título 22 / 600. Acciones iconográficas 48.  
Respetar status bar insets. Sin headers enormes ni franjas de marca.

---

## 16. Bottom nav

Items fijos (persona): **Inicio · Publicar · Sumate · Comunidad · Perfil**.  
(Contextos org/foster pueden cambiar labels; no cambiar semántica de rutas.)

Look: barra blanca, elevación 0, icono + label.  
Seleccionado: naranja discreto, **sin pill**.  
Publicar: círculo outline 40, no disco sólido flotante.

---

## 17. Dialogs

Confirmaciones destructivas o irreversibles. Radio 16, un primary.

---

## 18. Bottom sheets

Acciones contextuales, media, comentarios, selectores. Handle + padding 16. Elevación 1.

---

## 19. Snackbars

Breves, sobre el contenido, sin tapar bottom nav. Success corto. Error humano.

---

## 20. Empty states

Icono simple (sin círculo 72). Título corto. Una línea de apoyo. CTA solo si ya existía.

---

## 21. Error states

Icono sutil + título humano + mensaje. Retry **solo** si la pantalla ya lo tenía.

---

## 22. Loading / skeleton

Preferir inline / shimmer existente. Spinner 28, acento naranja, no héroe de pantalla salvo carga inicial de ruta.

---

## 23. Forms

Secciones claras. Inputs unificados. CTA primary al final. Aire 16–20.  
Health: mismos tokens; **no** reordenar campos.

---

## 24. Admin surfaces

Mismo sistema, más densidad (spacing 12, títulos 16).  
Cards solo para resumen / módulos. Tablas y queues como listas + status chips.  
No phpMyAdmin, no Material demo gris.

---

## 25. Responsive / small screens (320–360 dp)

- Nada importante cortado; wrap, no ellipsis agresivo en CTAs.
- Primary no a 100% si hay dos acciones (weight).
- Sin overflow horizontal.
- Touch ≥ 48.
- Insets: status, nav, IME.

---

## Componentes canónicos (Android)

| Nombre | Rol |
|---|---|
| `LeoTopAppBar` / `LeoVerTopBar` | Top bar |
| `LeoPrimaryButton` / `LeoSecondaryButton` / `LeoOutlinedButton` | Acciones |
| `LeoCard` / `LeoVerCard` | Agrupación opcional |
| `LeoTextField` / `LeoSearchBar` | Inputs |
| `LeoEmptyState` / `LeoVerErrorState` / `LoadingState` | Estados |
| `LeoSocialPostCard` | Post social |
| `LeoListRow` / `V2NavRow` / `LeoSettingsRow` | Filas |
| `ComunidappBottomBar` | Bottom nav |
| `LeoFilterChip` | Chips |

No construir un framework extra. Extraer solo lo reutilizado.

---

## Accesibilidad

Contraste texto `#263238` sobre `#FAFBF8` / blanco.  
Acentos naranja/verde no son el único canal (icono + texto).  
`contentDescription` en icon-only. Font scaling: no fijar alturas que recorten 2 líneas de body.

---

## Animaciones

Fade / scale pequeña / press feedback. Sin timings funcionales nuevos.

---

## FORM REQUIRED FIELD RULE

Todo campo obligatorio muestra `label + " *"` (ejemplo: `Especie *`, `Foto *`, `Email *`, `Ubicación *`).  
Los opcionales no llevan asterisco.  
Si el usuario intenta continuar sin completar, marcar visualmente cada faltante y un mensaje junto al campo. No alcanza un “faltan datos obligatorios” genérico.

Android: `LeoRequiredField` + `V2FormTextField(required = …)` / `LeoTextField(required = …)`.

## PHOTO INPUT RULE

En toda pantalla donde se agrega o toma una **foto**: ofrecer **Tomar foto** y **Elegir de galería**.  
Reutilizar `rememberLeoVerPhotoSourcePicker`. Cámara pide permiso CAMERA al elegir cámara. Galería usa el picker moderno; no pedir permisos de almacenamiento amplios.  
Solo omitir una opción si hay restricción funcional documentada. LOST/FOUND no tiene excepción.

Web futura debe aplicar las mismas dos reglas. Web no se desarrolla en este bloque.

## ANDROID-FINAL-CLOSURE

Las pantallas internas (auth, perfil, mascotas, VitaCora, publicar, admin, support) deben usar este mismo sistema.  
No crear un Design System paralelo. Detalle de cierre: `docs/qa/ANDROID-FINAL-CLOSURE.md`.

## ANDROID-CLOSURE-03

Segunda pasada visual de internas: chips de publicar / stories / mascotas vía `LeoFilterChip`, banner de Reel sin contenedor naranja elevado.  
Aceptación: tokens + botones/inputs/rows DS2, no “solo Nunito + background”.  
Checklist de rutas: `docs/design/LEOVER-UI-AUDIT.md`. QA: `docs/qa/ANDROID-CLOSURE-03.md`.  
Web sigue bloqueada.

## 26. Social feed composition (ANDROID-POLISH-05)

Contrato visual. No cambia backend ni `content_kind`.

| Kind | Presentación | Media |
|---|---|---|
| **STORY** | Tray horizontal arriba | Foto/video de 24 h |
| **POST** | Flujo vertical tipo publicación | Foto, multiphoto o **video de publicación** |
| **REEL / Clip** | Carrusel horizontal **Clips** + visor vertical | Video corto. Preview = frame real |

**POST VIDEO ≠ REEL.** Un video tomado o elegido desde Publicación sigue siendo POST.  
Un video desde Publicar → Reel sigue siendo REEL. Nunca se convierte solo.

Feed: historias → publicaciones; el carrusel de Clips entra después de la primera publicación (o solo si no hay posts).  
Un Reel en el carrusel **no** se duplica como card vertical.  
Preview de clip: thumbnail cacheado (`VideoPreviewFrame`). Sin ExoPlayer en el carrusel.

Composer: sheet DS2 (foto / video / galería). Cámara in-app compartida (`LeoVerCaptureCamera`) con giro frente/dorso.  
QA: `docs/qa/ANDROID-POLISH-05.md`.
