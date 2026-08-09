# D08-07 — Auditoría UX pantallas RC1.2

**Fecha:** 2026-08-05 · **Rama:** `main` · **HEAD base:** `5986a01eb8662a44d3283d5ae1161816f65e156e`  
**Estado:** cambios locales sin commit — pendiente revisión física de Leonardo.

---

## 1. Problemas encontrados — Inicio

| Problema | Severidad |
|----------|-----------|
| App bar / título plano “Inicio” sin saludo útil | Alta |
| Sin atajos claros Adoptar / Perdidos / Ayudar / Publicar | Alta |
| Espacios vacíos sin empty state amigable | Media |
| Jerarquía tipográfica plana | Media |

**Aplicado:** `LeoGreetingHeader`, grilla 2×2 `LeoQuickActionTile`, carrusel personas, feed, `LeoEmptyState`.

---

## 2. Problemas encontrados — Sumate

| Problema | Severidad |
|----------|-----------|
| Demasiadas pestañas / opciones cortadas | Alta |
| Refugios/Donaciones compitiendo como tabs de primer nivel | Alta |
| Cápsulas outlined + textos Mxx | Alta |
| Filtros / vistas poco agrupadas | Media |

**Aplicado:** 4 tabs (Adopciones, Perdidos, Tránsito, Eventos); Refugios/Donaciones como hubs con `LeoFeatureCard`; chips de vista en adopciones; strings Mxx removidos de UI.

---

## 3. Problemas encontrados — Comunidad

| Problema | Severidad |
|----------|-----------|
| Botones naranjas gigantes sin descripción | Alta |
| Códigos M19–M27 visibles | Alta |
| Servicios sin grilla tonal | Media |
| Chips sin scroll evidente | Media |

**Aplicado:** bloques Personas/red · Servicios (tiles) · Organizaciones/ayuda; feature cards; chips scrollables; sin Mxx en labels.

---

## 4. Problemas encontrados — Perfil

| Problema | Severidad |
|----------|-----------|
| Lista de ~8+ `OutlinedButton` gigantes | Alta |
| Cabecera con franja degradada fuerte | Media |
| Mascotas en lista vertical densa | Media |
| Poca agrupación Ayuda / Actividad / Staff | Media |

**Aplicado:** cabecera tarjeta crema/blanca; carrusel `LeoPetCard`; acciones 2×2; `LeoSettingsRow` agrupadas; staff en bloque separado; empty states.

---

## 5. Problemas encontrados — Navegación

| Problema | Severidad |
|----------|-----------|
| Barras naranjas / selección poco diferenciada | Alta |
| Publicar sin jerarquía de acción principal | Alta |

**Aplicado:** bottom bar blanca; seleccionado naranja + `OrangeContainer`; Publicar FAB circular `BrandOrangeSoft`.

---

## 6. Decisiones aplicadas

1. `primary` Material = `BrandOrangeSoft`; verde = apoyo / positivo.
2. App bars claras; status bar crema.
3. Sistema `Leo*` centralizado en `ui/components/leo`.
4. Mxx ocultos en copy de usuario; IDs técnicos internos intactos.
5. Sin cambios de API, Supabase, versionCode/Name, applicationId.
6. Sin commit / push / ramas nuevas.

---

## 7. Diferencias pendientes

- Filtros avanzados de Sumate aún pueden vivir en pantallas hijas (bottom sheet unificado pendiente).
- Algunas pantallas secundarias (admin/ops) mantienen densidad alta aunque sin Mxx.
- `LeoSegmentedControl` / `LeoPublicationCard` / `LeoBottomNavigation` como wrappers nombrados: parcial (bottom bar y cards existentes adaptados).
- Skeletons de carga: se mantienen indicadores previos; skeletons densos pendientes.
- Previews Compose ampliados; validación física en dispositivo real pendiente (Leonardo).
- Numeración doc: coexisten dos archivos con prefijo D08-06 (logo vs UX).

---

## 8. Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Regresión visual en pantallas no tocadas | Alcance RC1.2 = hubs principales |
| Contraste naranja suave + texto | Forzar `BrandText` sobre `BrandOrangeSoft` |
| Touch areas en tiles | `heightIn` / `TouchMin` 48 dp |
| Confusión staff vs usuario | Staff agrupado al final del perfil |

---

## 9. Validación prevista

- Búsqueda estática: M11/M12/M16/M19/M20/M21/M25/M26 en textos UI; sin ComunidadApp; sin violetas.
- Un único `:app:assembleLocalDebug`.
- Sin lint completo / JaCoCo / suite completa / emulador.
