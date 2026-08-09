# D08-06 — Sistema UX/UI LeoVer v1.0

**Versión:** 1.0 · **Fecha:** 2026-08-05 · **Estado:** RC1.2 (en revisión física)  
**Alcance:** identidad naranja / verde / crema aplicada a pantallas principales y componentes reutilizables.

> Nota de numeración: existe también `D08-06-Auditoria-Reemplazo-Logo-v1.0.md` (auditoría de assets). Este documento es el **sistema UX/UI** de producto.

---

## 1. Principios UX

1. **Simple:** una acción primaria clara por sección.
2. **Cálido:** crema de fondo, tarjetas blancas, acentos naranja suaves.
3. **Explorable:** grillas, carruseles y chips desplazables; sin pestañas cortadas sin scroll.
4. **Humano:** saludos, vacíos amigables, sin códigos técnicos (Mxx) en UI productiva.
5. **Coherente:** mismos tokens, botones y tarjetas en Inicio, Sumate, Comunidad y Perfil.

---

## 2. Jerarquía visual

| Nivel | Uso | Token tipográfico |
|-------|-----|-------------------|
| Página | Saludo / título principal | `LeoPageTitle` 26–28 sp |
| Sección | Bloques (“¿Qué querés hacer hoy?”) | `LeoSectionTitle` 20–22 sp |
| Tarjeta | Títulos de card / tile | `LeoCardTitle` 17–18 sp |
| Cuerpo | Texto corrido | Body 15–16 sp |
| Apoyo | Subtítulos, metadatos | `LeoCaption` 12–14 sp |
| Nav | Labels bottom bar | `LeoNavLabel` 12–13 sp |

---

## 3. Navegación

Bottom navigation fija:

- Inicio · Sumate · **Publicar** (acción elevada circular) · Comunidad · Perfil
- Fondo blanco; seleccionado naranja / `OrangeContainer`; no seleccionado gris.
- **Publicar** usa `BrandOrangeSoft` / `BrandOrange`, no verde dominante.

App bars claras (`BrandCream`), título `BrandText`, sin franjas naranjas completas.

---

## 4. Componentes (`ui/components/leo`)

| Componente | Rol |
|------------|-----|
| `LeoTopAppBar` | Encabezado claro + subtítulo opcional |
| `LeoSectionHeader` | Título de bloque + acción |
| `LeoPrimaryButton` | CTA naranja suave, radio 16, min 52 dp |
| `LeoSecondaryButton` | Apoyo verde contenedor |
| `LeoOutlinedButton` | Solo acciones menores |
| `LeoCard` / `LeoFeatureCard` | Superficies blancas con borde tenue |
| `LeoServiceTile` / `LeoQuickActionTile` | Accesos en grilla |
| `LeoFilterChip` | Filtros compactos |
| `LeoSearchBar` | Búsqueda 52–56 dp |
| `LeoEmptyState` | Vacío con icono + CTA |
| `LeoPetCard` / `LeoPersonCard` | Carruseles |
| `LeoSettingsRow` | Filas de perfil / ajustes |
| Bottom bar | `ComunidappBottomBar` adaptado al sistema |

---

## 5. Tokens

**Color:** ver D08-03. Derivados: `BrandOrangeContainer`, `BrandGreenContainer`, `NeutralBorder`, `MutedText`.

**Espaciado (`LeoDimens`):** 4 / 8 / 12 / 16 / 24 / 32 dp.

**Radios:** chips 12 · campos 14 · tarjetas 16 · destacadas 20 · publicar circular.

**Alturas:** botón primario 52–56 · secundario 48–52 · search 52–56 · chips 36–40 · touch min 48.

---

## 6. Botones

- **Primario:** fondo `BrandOrangeSoft`, texto `BrandText`, sin borde.
- **Secundario:** `BrandGreenContainer` / blanco, texto verde oscuro o `BrandText`.
- **Outlined:** uso restringido; no listas de cápsulas gigantes.

---

## 7. Tarjetas

Fondo blanco, radio 16, padding 16, borde `NeutralBorder` o elevación 1 dp. Sin violetas. Jerarquía título → descripción → CTA/flecha.

---

## 8. Chips y tabs

- Seleccionado: contenedor naranja (o verde semántico).
- No seleccionado: blanco + `MutedText`.
- Tabs scrollables con indicador naranja.
- Filtros avanzados: bottom sheet / panel, no pantalla entera permanente.

---

## 9. Estados vacíos

`LeoEmptyState`: icono en círculo tonal, título, explicación, CTA opcional. Aplicar en Home, personas cerca, adopciones/filtros, mensajes, perfil sin posts.

---

## 10. Fotografías

Priorizar foto real de mascota/persona/organización. Placeholders tonales crema/naranja claro. Evitar bloques vacíos grandes sin mensaje.

---

## 11. Accesibilidad

- Touch ≥ 48 dp · contraste `BrandText` sobre crema/naranja suave · `contentDescription` en iconos de acción · labels de nav siempre visibles · tabs con scroll · no depender solo del color · respetar reducción de movimiento del sistema.

---

## 12. Microinteracciones

150–220 ms: selección tab, presión de tarjeta, favorito, cambio bottom nav. Sin dependencias pesadas.

---

## 13. Ejemplos por pantalla

### Inicio
Saludo “¡Hola, [nombre]!” · grilla 2×2 Adoptar / Encontrar / Ayudar / Publicar · carrusel personas · feed · vacío amigable.

### Sumate
Tabs: Adopciones · Perdidos · Tránsito · Eventos · Refugios/Donaciones como feature cards · chips Explorar / Mis postulaciones / Recibidas en adopciones.

### Comunidad
Bloque red social + mensajes · tiles de servicios · organizaciones/ayuda · chips de categoría scrollables · “Próximamente” si aplica.

### Perfil
Cabecera con avatar/nombre/localidad · carrusel mascotas · acciones 2×2 · actividad en `LeoSettingsRow` · ayuda/cuenta · staff agrupado.

---

## 14. Referencias de código

- `Color.kt`, `Type.kt`, `Dimens.kt`, `Theme.kt`
- `ui/components/leo/LeoComponents.kt`
- `ComunidappTopBar.kt`, `ComunidappBottomBar.kt`
- Pantallas: `HomeScreen`, `SumateScreen`, `ComunidadScreen`, `ProfileScreen`
