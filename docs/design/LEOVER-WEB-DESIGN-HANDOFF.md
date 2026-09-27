# LeoVer Web Design Handoff

Cómo llevar el **Design System 2.0** a web (desktop, tablet, mobile web) **sin copiar Android literalmente**.

Fuente: `LEOVER-DESIGN-SYSTEM.md`.  
Marca: **LeoVer**. Producto: **VitaCora**.

---

## 1. Qué se comparte (obligatorio)

- Tokens de color (mismos hex).
- Nunito Sans (mismos pesos 400 / 500 / 600 / 700).
- Escala de spacing 4–32 y radios small/medium/field/card/pill.
- Elevation 0 + hairlines.
- Jerarquía: avatar/media > nombre > metadata.
- Bottom/side nav labels canónicos en producto persona.
- Copy: VitaCora, Mi manada, Conexiones, Solicitudes, Encontrar personas.
- Light only en v1 web.
- Naranja/verde solo como acento.

Publicar estos tokens como CSS variables / theme JSON, no como valores mágicos por página.

```css
:root {
  --lv-orange: #ff7a00;
  --lv-green: #49b749;
  --lv-bg: #fafbf8;
  --lv-surface: #ffffff;
  --lv-text: #263238;
  --lv-text-2: #667085;
  --lv-hairline: #e5eae4;
  --lv-space-1: 4px;
  --lv-space-2: 8px;
  --lv-space-3: 12px;
  --lv-space-4: 16px;
  --lv-space-5: 20px;
  --lv-space-6: 24px;
  --lv-space-8: 32px;
}
```

---

## 2. Qué no se copia de Android

| Android | Web |
|---|---|
| Bottom nav 5 tabs siempre visible | Desktop: **sidebar** 240–280 px o top nav. Tablet: bottom o rail. Mobile web: bottom compacto |
| `LeoSocialPostCard` Compose | Mismo ritmo (header / media / actions / copy), otro markup |
| Touch 48 dp | Pointer: 32–40 px ok en desktop; 44–48 en touch |
| Scaffold + insets Android | Safe-area CSS + `env(safe-area-inset-*)` |
| Material NavigationBar | Nav propia LeoVer |
| APK densities | `srcset` / 2x avatars, media `object-fit: cover` sin deformar |

No portar `LeoDimens` ni nombres `V2NavRow` al DOM. Portar **reglas**.

---

## 3. Desktop (≥ 1024 px)

- Canvas: max content 720–840 px para **feed** (lectura social). No estirar posts a 1400 px.
- Comunidad / admin: usar el ancho. Admin en 12 columnas, tablas reales, densidad alta.
- Sidebar fija: Inicio, Publicar, Sumate, Comunidad, Perfil + logo LeoVer (wordmark, no disco naranja).
- Publicar: botón primary en sidebar, no FAB gigante.
- Chat: lista 360 + hilo flex (patrón liviano tipo messenger, no clonar WhatsApp).
- Perfil: avatar + stats en una fila; grid de mascotas a la derecha o debajo, no stack de cards.
- Hover: hairline / color de texto. No sombras al hover.

---

## 4. Tablet (600–1023 px)

- Feed a una columna centrada (600–680).
- Nav: rail de iconos o bottom compacto.
- Composer: panel o ruta dedicada, no modal de 400 px perdido en el centro.
- Mi manada: tabs horizontales (mismos labels).

---

## 5. Mobile web (≤ 599 px)

- Más cerca de Android en ritmo, no en componentes.
- Bottom nav ligera, Publish outline, acento naranja en seleccionado.
- Media edge-to-edge (viewport minus 0–8 px, no 16+16 de card).
- IME: composer `position: sticky` + visual viewport.
- No hover-only actions; iconos siempre visibles.

---

## 6. Mapeo de superficies

| Superficie | Web |
|---|---|
| Feed | Columna central, hairline entre posts, media full width. POST video 4:5 crop; tap → ratio original |
| Reels | Ruta `/reels` o panel 9:16; controles mínimos; no overlay pesado |
| Stories | Tray horizontal; ring solo con story activa (misma regla de negocio) |
| Perfil | Social header, no ficha; bio wrap; menú como lista plana |
| Mi manada | Tres tabs; filas avatar 52 |
| Mensajes | Lista + hilo; burbuja propia `orange-container`, ajena blanca |
| Mascota | Hero foto; **nombre** 22/600; `Perro · Macho · 4 años` caption |
| VitaCora | Cálida, timeline ligera; **sin** copy etimológico en la vista operativa (sí en onboarding/About) |
| Health | Mismos campos/orden; solo CSS |
| Sumate | Lista icono + título + caption, no 4 cards enormes |
| Comunidad | Categorías scaneables; row logo + nombre + badge |
| Admin | Hub módulos por permiso; queues densas; mismo color system |
| Campañas | Alias + “Colaboré”; transferencias fuera de LeoVer; total solo confirmado |

---

## 7. Componentes web sugeridos

`LvButton`, `LvInput`, `LvRow`, `LvAvatar`, `LvChip`, `LvEmpty`, `LvTopBar`, `LvNav`.  
Un design-token file. No un UI kit de 80 wrappers.

---

## 8. Accesibilidad web

- Contraste WCAG AA en texto.
- Focus ring 2 px naranja suave, no outline browser default feo.
- Landmarks: `main`, `nav`, `dialog`.
- No transmitir estado solo con color (like: icono filled + `aria-pressed`).

---

## 9. Qué no hacer

- No clonar Instagram/WhatsApp pixel a pixel.
- No usar cream de fondo de sitio.
- No pintar header verde/naranja a full bleed.
- No reimplementar lógica de ring, Health, Reel o permisos en el front “porque queda mejor”.
- No crear migrations ni cambiar contratos para un hover.

---

Android internals after ANDROID-FINAL-CLOSURE, ANDROID-CLOSURE-03 and ANDROID-POLISH-05 are the visual source for the same tokens. Do not start Web until physical QA of ANDROID-POLISH-05 signs off.

### Feed kinds (web must keep this split)

- **STORY** = tray.  
- **POST** = vertical publication. A video inside a publication is still a POST.  
- **REEL / Clip** = dedicated carousel + vertical viewer. Never render the same Reel as a normal post card.

## 10. Entrega a diseño / front

1. Leer este handoff + Design System.
2. Figma/tokens desde hex de la sección Colors.
3. Mobile web primero para social; desktop para admin y comunidad.
4. QA visual: 320, 768, 1280. Feed, perfil, chat, admin hub.

## 11. Location / Lost-Found contracts (document only — do not build Web yet)

See `docs/architecture/LOCATION-AND-ALERTS.md`.

- RPCs: `canon_create_lost_found`, `canon_list_lost_found`, `canon_claim_lost_found`, `canon_get_lost_found_exact`, `canon_upsert_responder_base_location`, `canon_record_location_consent`, `canon_tick_lost_found_waves` (cron only), `canon_mark_lost_found_in_care`, match assert/reject/confirm
- Public list: locality / zone only. Never render exact lat/lng.
- FOUND publish creates 1 case + 1 provisional pet + 1 VitaCora. Create errors use `LF-CREATE-*` stages (1094/1095 `_canon_geo_point`). Claim transfers custody only after 2s nearest-wins arbitration. Waves are pg_cron, not list reads. Matching cap 15 / threshold 0.55. Reuse verification, transit request/select, adoption pause, vet invite outbox, follow-up plans — no parallel Web backend.
- FORM REQUIRED FIELD RULE and PHOTO INPUT RULE (cámara + galería) apply to future Web. See Design System. Do not build Web in this block.
- Tutorial skip is `tutorial_progress.onb02_flow`, not profile completeness.

## 12. Community / care contracts (document only — no Web UI in this block)

Reuse the same backend. Do not invent parallel tables.

- Verification: `leover_verification_requests` + `person_capabilities.verification_status` / org status (`PENDING`, `REQUIRES_CORRECTION`, `VERIFIED`, `REJECTED`, `SUSPENDED`)
- Transit: existing `foster_profiles` / `foster_placements` (same `pet_id` / VitaCora; FOSTER never in FOUND fanout)
- Adoption: same pet/VitaCora; `adoption_general_profiles`; accept ≠ transfer (`1071` care transfers)
- Professional: existing veterinary history + `vitacora_update_proposals`; `vet_pending_owner_invites` (`PENDING_OWNER_EMAIL`)
- Community nearby: `canon_list_community_nearby` (PostGIS, not client-side)
- Membership: `membership_entitlements` 90-day trial, `paywall_enforced = false` until billing V2
- Appointments: existing veterinary agenda states

See `docs/architecture/VERIFICATION.md`, `TRANSIT.md`, `ADOPTIONS.md`, `PROFESSIONAL-CARE.md`.
