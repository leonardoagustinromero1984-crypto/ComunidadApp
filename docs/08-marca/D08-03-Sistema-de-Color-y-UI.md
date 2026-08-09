# D08-03 — Sistema de color y UI LeoVer

**Versión:** 1.2 · **Fecha:** 2026-08-05 · **Estado:** APROBADO  
**Implementación código:** `app/.../ui/theme/Color.kt`, `Theme.kt`, `res/values/colors.xml`

---

## 1. Tokens exactos (fuente única)

| Token código | Nombre | HEX |
|--------------|--------|-----|
| `BrandOrange` | Naranja Principal | `#FF7A00` |
| `BrandOrangeSoft` | Naranja Suave | `#FFA64D` |
| `BrandGreen` | Verde Principal | `#49B749` |
| `BrandGreenDark` | Verde Oscuro | `#247A3D` |
| `BrandCream` | Crema Claro | `#FFF6EA` |
| `BrandText` | Gris Texto | `#2F3A37` |
| `BrandWhite` | Blanco | `#FFFFFF` |

Tonales derivados documentados: `BrandOrangeContainer`, `BrandGreenContainer`, `BrandGreenSoft`, `BrandOrangeDeep`, `BrandTextSecondary`.

Semánticos (no corporativos): `UrgentRed`, `WarningAmber`.

---

## 2. Uso semántico (UI interna — calibración 2026-08-05)

| Color | Uso |
|-------|-----|
| Naranja Principal | **Predominante:** botones primarios, CTAs, nav seleccionada, highlights, status bar |
| Verde Principal | **Apoyo:** secundarios, comunidad/apoyo visual, chips complementarios |
| Verde Oscuro | Acento positivo fuerte / tertiary; texto blanco cuando el fondo es verde oscuro |
| Crema | Fondo tema claro, secciones, bloques secundarios |
| Gris Texto | Texto, títulos, **eslogan login/onboarding** (`#2F3A37`) |
| Blanco | Tarjetas, diálogos, superficies elevadas, inputs |

---

## 3. Accesibilidad

- No texto blanco pequeño sobre `#FF7A00`, `#49B749`, `#FFA64D`.  
- Preferir texto `#2F3A37` sobre esos fondos.  
- Botones sólidos con texto blanco: fondo `#247A3D`.  
- Acción naranja destacada: fondo `#FF7A00` + texto `#2F3A37` si cumple contraste.  
- Errores → rojo; advertencias → ámbar semántico; alertas de peligro/pérdida **no** solo naranja corporativo.

---

## 4. Componentes

| Componente | Regla |
|------------|-------|
| Botón primario | `#FF7A00` / texto `#2F3A37` (`MaterialTheme.primary`) |
| Secundario / apoyo | Verde o verde oscuro (borde o acento) |
| Destructivo | Rojo semántico |
| Chips | Contenedores naranja (predominio) / verde (apoyo) |
| Tarjetas | Fondo blanco; texto `#2F3A37`; secundarios en crema |
| Navegación | Selección **naranja** en todos los ítems; no seleccionado gris; fondo blanco |
| Eslogan login/onboarding | Solo `#2F3A37` (ni naranja ni verde) |
| Formularios | Superficie blanca sobre crema |
| Progreso / donaciones | Naranja |
| Verificado / éxito real | Verde (semántica, no invertir) |
| Alertas críticas | Rojo / ámbar semántico |

---

## 5. Tema claro (implementado)

- `background` = Crema `#FFF6EA`  
- `surface` = Blanco  
- `primary` = **Naranja Suave** `#FFA64D`  
- `onPrimary` = Gris Texto `#2F3A37`  
- `secondary` = Verde Principal `#49B749`  
- `onSecondary` = Gris Texto  
- `tertiary` = Naranja fuerte `#FF7A00` (acento puntual)  
- `surfaceVariant` = Crema  
- `onBackground` / `onSurface` = Gris Texto  

---

## 6. Paleta pastel de la interfaz interna — decisión definitiva

- **Predominio interno (acción):** `BrandOrangeSoft` `#FFA64D` — botones primarios, selección de nav, chips activos, Publicar.  
- **Énfasis puntual:** `BrandOrange` `#FF7A00` — indicadores, iconos activos, no barras completas.  
- **Color de apoyo / positivo:** `BrandGreen` `#49B749` y `BrandGreenDark` `#247A3D` — verificación, éxito, tiles de apoyo. **No** usar verde como segunda acción principal permanente (p. ej. Publicar).  
- **Fondo:** `BrandCream` `#FFF6EA`.  
- **Texto / eslogan:** `BrandText` `#2F3A37`.  
- **Logo, launcher y splash** conservan colores oficiales del asset (no se recalibran).  
- **Semántica** de errores, advertencias y urgencias: sin cambio (rojo / ámbar).  

---

## 6.1 RC1.2 — App bars y superficies grandes (2026-08-05)

- **Barras superiores mayormente claras:** `BrandCream` o `BrandWhite`, título e iconos `BrandText`.  
- **No** repetir grandes franjas `BrandOrangeSoft` / `BrandOrange` como fondo permanente de todas las app bars.  
- Status bar alineada a crema (o naranja suave solo cuando la pantalla lo justifique).  
- Acento naranja en indicadores, iconos seleccionados y pequeñas superficies (`OrangeContainer`).  
- Sistema completo: ver `D08-06-Sistema-UX-UI-LeoVer-v1.0.md`.

---

## 7. Ajuste de predominio cromático interno — 2026-08-05

Histórico: primero se invirtió verde↔naranja; luego se fijó la paleta pastel (§6) como decisión definitiva. RC1.2 refuerza app bars claras y evita bloques naranjas repetitivos (§6.1). La marca oficial (logo/launcher) no cambia.

---

## 8. Tema oscuro (futuro)

Usar superficies `#121212` / `#1E1E1E`, primary `BrandOrangeSoft`, secondary `BrandGreen`, texto claro. Mantener rojo de error.
