# LeoVer UI Audit — UX-01

**Fecha:** 2026-09-13 (matriz §8 actualizada post-reinicio, pass DS2 interno)  
**Alcance:** inventario visual del Android actual **antes** del Design System 2.0.  
**Regla:** solo presentación. No se auditó backend, RLS, Health behavior, Reel pipeline ni contratos.

Marca: **LeoVer**. Producto mascota: **VitaCora**.

---

## 1. Diagnóstico

La app ya tenía tokens (`Brand*`, `LeoDimens`, Nunito Sans, Light forzado) y componentes `Leo*` / `V2*`.  
La sensación pesada no venía de falta de sistema, sino de **cómo se usaba**:

- card dentro de card
- borde + sombra en casi cada bloque
- botones sólidos (verde o naranja) para acciones menores
- círculos de color detrás de casi todos los iconos
- bottom nav con disco naranja de 56 dp y pills verdes
- feed envuelto en Surface con borde
- perfil y Mi manada como fichas administrativas

---

## 2. Inconsistencias por token

### Color

| Hallazgo | Dónde |
|---|---|
| Tokens canónicos correctos (`#FF7A00`, `#49B749`, `#FAFBF8`) | `Color.kt` |
| Paleta activa `v3Experimental` usaba **verde como primary** y naranja lavado `#EFA066` | `VisualDirectionV2.kt` |
| Material `colorScheme.primary` = verde; Leo buttons seguían `leoVisual().primary` | `Theme.kt`, `LeoPrimaryButton` |
| Superficies a veces cream, a veces white, a veces green container | Auth, search hint, empty states |
| `BrandOrangeContainer` prohibido en Comunidad (gate de regresión) | `ComunidadScreen.kt` |

### Typography

| Hallazgo | Dónde |
|---|---|
| Nunito Sans global: correcto | `Type.kt` |
| `LeoDisplay` ExtraBold 32 — demasiado pesado | Home / auth |
| Títulos de pantalla Bold 26 en top bars altos | `LeoTopAppBar` |
| Mezcla `MaterialTheme.typography` + `Leo*` en la misma fila | Chat, Friends, PetCard |
| Exceso de Bold en metadata (edad de mascota como titleMedium) | `PetIdentityBlock` |

### Spacing

| Hallazgo | Dónde |
|---|---|
| Escala 4/8/12/16/24/32 documentada | `LeoDimens` |
| Faltaba 20 dp en la escala | pantallas con `18.dp`, `10.dp`, `6.dp` |
| Feed: padding horizontal 16 **alrededor** del post (media no edge-to-edge) | `HomeScreen` |
| Friends/Search: `16.dp` / `12.dp` / `10.dp` locales | listados |

### Radii

| Token | Valor | Uso real |
|---|---|---|
| Chip | 12 | chips, algunos botones |
| Field | 14 | inputs |
| Card | 16 | posts, cards |
| Feature | 22 | feature cards, V2 surfaces |
| Hero mascota | 24 | `PetHero` — más redondo que el resto |
| Friends rows | 14 | fuera de escala |

### Elevation / bordes

Casi todo `LeoCard`, `V2SurfaceCard`, `LeoFeatureCard`, `LeoSocialPostCard`, `PetCard`, `FriendRow` usaba:

- `BorderStroke(1.dp, NeutralBorder)`
- `elevation` 1–2 dp

Resultado: cajas apiladas, ritmo administrativo.

---

## 3. Componentes

### Buttons

- `LeoPrimaryButton`: fill del primary visual (verde en V3), altura 52, radio 16.
- `LeoSecondaryButton`: fill `secondarySoft` (bloque verde), no outline.
- Material `Button` / `OutlinedButton` sueltos en Friends, transferencias, pet detail.

### Cards

- Post: Surface blanca + borde alrededor de header + media + acciones.
- Perfil: card flotante de stats + `V2NavRow` = card por cada ítem de menú.
- Sumate: seis `V2NavRow` = seis cards pesadas.
- Comunidad: `LeoVerProviderCard` sobre `V2SurfaceCard` (borde + elevación).
- Admin Hub: `LeoFeatureCard` + `LeoCard` de resumen.

### List rows

No había un row canónico. Existían:

- `V2NavRow` (card + icono en círculo 44)
- `LeoSettingsRow` (plano, más cercano a 2.0)
- `FriendRow` / `UserSearchCard` / `ConversationCard` (cards)

### Avatars

- Feed: círculo naranja permanente detrás del avatar (decorativo, no story ring).
- Perfil: borde 2 dp `visual.primary` permanente.
- Stories: ring gobernado por `StoryRingPolicy` (correcto; no tocar lógica).
- Chat lista: sin avatar.

### Icons

- Material Filled + Outlined mezclados.
- Círculos de marca detrás de empty states (72 dp naranja), nav rows (44 dp) y avatares.

### Navigation

- Bottom bar: Publish = disco 56 dp naranja, offset -10 dp; seleccionado = pill `BrandGreenContainer`.
- Top bars: `LeoPageTitle` 26 Bold + subtítulo — headers altos.
- Mi manada: `TabRow` Material default (indicador grueso).

### Dialogs / sheets / snackbars

- `AlertDialog` Material para confirmaciones (transferencias, eliminar conexión) — correcto semánticamente.
- Comments = bottom sheet existente.
- Snackbars Material en Home/Search — sin diseño propio; a veces tapan contenido.

### Empty / error / loading

- `LeoEmptyState`: icono en círculo naranja 72.
- `LoadingState`: spinner naranja centrado a pantalla completa.
- `ErrorState`: texto + primary button; sin icono común.

### Forms

- `LeoTextField` ya unificado (outline suave, radio 14).
- Search Friends usaba `TextField` Material distinto.
- Chat composer: `OutlinedTextField` crudo.

### Admin

Comparte `LeoCard` / `LeoFeatureCard` → misma pesadez que producto, sin densidad extra consciente.

---

## 4. Pantallas (prioridad)

| Superficie | Problema visual | Severidad |
|---|---|---|
| Feed / post card | Caja + borde; media no full-bleed; avatar naranja | Alta |
| Bottom nav | Disco Publish + pills verdes | Alta |
| Perfil persona | Stats card + menú de cards; ring decorativo | Alta |
| Mi manada / conexiones | Cards por fila | Alta |
| Mensajes | Cards sin avatar; burbujas Material | Alta |
| Mascotas | PetCard elevada; edad demasiado prominente | Alta |
| Sumate | Seis cards pesadas | Media |
| Comunidad | Provider card boxed | Media |
| Publicar | Composer con cajas (hereda Leo/V2) | Media |
| VitaCora / Health | Cards V2 médicas | Media (solo styling) |
| Guardados | Padding 16 + cards de post | Media |
| Admin Hub | Feature cards pesadas | Media |
| Auth / onboarding | Headers grandes, primary verde | Media |

---

## 5. Qué no se tocó (a propósito)

- Lógica de story ring (`StoryRingPolicy` / `isActiveStory`)
- Copy canónico (VitaCora, Mi manada, bottom nav)
- Health fields / orden / merge
- Reel pipeline, Guardados, transferencias, permisos
- Tests funcionales

---

## 6. Dirección de corrección (aplicada en UX-01)

Ver `LEOVER-DESIGN-SYSTEM.md`.

Principio: **contenido sobre superficie limpia**. Jerarquía por tipografía, espacio, avatar y media — no por cajas.

## 7. ANDROID-FINAL-CLOSURE

Continuación visual de pantallas internas (auth, settings, formularios, composer, admin, support, diálogos) sobre el mismo Design System 2.0.  
Contratos funcionales de este bloque: `docs/qa/ANDROID-FINAL-CLOSURE.md`.

## 8. ANDROID-CLOSURE-03 — matriz de rutas internas

Clasificación: **COMPLETE** = top bar Leo, spacing/tokens, tipografía Leo, botones `Leo*`, chips `LeoFilterChip`, rows/hairlines, sin Card Material de página, empty/error/loading existentes. **N/A** = primitivo DS2, chrome de editor, o fuera de producto persona.

Nunito + background solo **no** cuenta como COMPLETE.

| Route | Screen | Area | DS2 status | Legacy findings | Fixed in pass | Physical QA |
|---|---|---|---|---|---|---|
| `login` / `register` / `forgot_password` | Auth | Auth | COMPLETE | Headers altos residuales | Tokens + `Leo*` forms | YES |
| `profile_onboarding` / `onb02` / `use_leover_as` / `help/tutorials` | Onboarding | Auth | COMPLETE | Tutorial chrome | Copy intacto; sin chips Material | YES |
| `home` | Feed | Social | COMPLETE | Post boxed | `LeoSocialPostCard` + avatar 40 | YES |
| `publish` / composers | Post / Reel / Story | Social | COMPLETE | FilterChip Material | `LeoFilterChip` visibilidad / pets | YES |
| story sheets | Música / stickers | Social | COMPLETE | FilterChip | `LeoFilterChip` | YES |
| `profile` | Perfil | Persona | COMPLETE | Stats/invites Card | Invites = Column + hairline + `Leo*` | YES |
| `profile` internas | Editar / cuenta / seguridad / consent / privacidad | Persona | COMPLETE | Forms V2 | Segunda pasada tokens | YES |
| `profile/saved` | Guardados | Social | COMPLETE | Nested cards | Sin caja extra | YES |
| `my_memories` | Mis recuerdos | Persona | COMPLETE | Grid | Copy transferencia 1083 (sin tocar gate) | YES |
| manada | Conexiones / solicitudes / buscar | Social | COMPLETE | Cards por fila | Rows planas | YES |
| `my_pets` / form / detail | Mascotas | Pets | COMPLETE | FilterChip + PetV2 Card | Chips Leo; `PetV2Card` plano | YES |
| `m14/vitacora` | VitaCora hub / momentos / import | VitaCora | COMPLETE | Import chips | `LeoFilterChip` import | YES |
| `sumate` | Tránsito / eventos / donaciones | Comunidad | COMPLETE | Cards boxed | Column + hairline + `Leo*` | YES |
| `lost_found` / `lost_found_map` | Perdidos / AlertMap | Comunidad | COMPLETE | FilterChip + `V2SurfaceCard` | `LeoFilterChip` + Column + hairline | YES |
| `adoptions` | Adopciones | Comunidad | COMPLETE | FilterChip | `LeoFilterChip` listas/form | YES |
| `comunidad` / service detail | Comunidad | Comunidad | COMPLETE | FilterChip detalle | `LeoFilterChip` lista + detalle | YES |
| `search` | Buscar | Social | COMPLETE | User Card + Material field | `LeoSearchBar` + `LeoListRow` | YES |
| messages / `m20` | Lista / chat | Mensajes | COMPLETE | Conversation Card | `LeoListRow`; burbuja Column | YES |
| `my_business` | Mi negocio | Comunidad | COMPLETE | FilterChip unused + Material Button | `LeoPrimaryButton` | YES |
| support person | Crear ticket | Support | COMPLETE | FilterChip | `LeoFilterChip` | YES |
| support admin | Cola | Support | COMPLETE | Card + FilterChip | `LeoFilterChip` + `LeoListRow` | YES |
| admin catalogs | Species / Location / Master | Admin | COMPLETE | FilterChip | `LeoFilterChip` | YES |
| admin staff | Staff | Admin | COMPLETE | FilterChip | `LeoFilterChip` | YES |
| admin moderation / cases / verification | Colas | Admin | COMPLETE | Material Card | `LeoListRow` | YES |
| `m15` foster / lifecycle / ops | Tránsito ops | Ops | COMPLETE | Card + Button | `LeoListRow` + `Leo*` buttons | YES |
| `m16`–`m19` / `m23` | Shelter / donación / eventos / social / booking | Ops | COMPLETE | FilterChip | `LeoFilterChip` | YES |
| `m21` reputation | Reseñas | Ops | COMPLETE | Card + Button | `LeoListRow` + `Leo*` | YES |
| `m22` providers | Prestadores | Ops | COMPLETE | Card + Button | `LeoListRow` + `Leo*` | YES |
| `m26` AI | Colas IA | Ops | COMPLETE | Card + Button | `LeoListRow` + `Leo*` | YES |
| `m28` grants / proposals | Accesos VitaCora | VitaCora | COMPLETE | Card | `LeoListRow` + `Leo*` | YES |
| `my_organizations` | Mis organizaciones | Comunidad | COMPLETE | OutlinedCard | Column + hairline + `Leo*` | YES |
| Reel banner | Compact upload | Social | COMPLETE | Orange + sombra | Surface + hairline | YES |
| `LeoFilterChip` / `LeoCard` / `V2SurfaceCard` | Primitivos | Sistema | N/A | Envuelven Material | Colores/elevación 0 DS2 | NO |
| Composer overlay TextButtons | Cámara/Aa/sticker | Social | N/A | Chrome de editor, no página | No se sustituye por fill-width | NO |
| AlertDialog actions | Confirm/dismiss | Sistema | N/A | TextButton de diálogo | Semántica Material correcta | NO |
| Web | — | Web | N/A | Fuera de este bloque | — | NO |

## 9. ANDROID-POLISH-05

Feed: Stories tray / POST vertical / Clips carousel. POST VIDEO se queda en la card. REEL no se duplica.  
Cámara in-app en Publicación e Historias (`LeoVerCaptureCamera`). Sheet DS2.  
Composers, perfil de mascota, VitaCora (Identidad / Resumen / Momentos / Accesos / Acciones) y Sumate campañas/eventos usan rows + `Leo*`.  
Detalle: `docs/qa/ANDROID-POLISH-05.md`.

**Remaining legacy (page chrome):** NONE.

Intencional, no es leftover: `LeoFilterChip` envuelve `FilterChip` Material con tokens DS2; `LeoCard`/`V2Foundation` usan `Card` elevation 0; burbuja propia de chat puede usar fill soft; `UiRegressionGateTest` exige los hex canónicos en `VisualDirectionV2.kt`; Comunidad no usa `BrandOrangeContainer`.

Ninguna ruta interna de producto persona queda fuera de la tabla.
