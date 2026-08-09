# D08-12 — Especificación Sumate y Comunidad

**Versión:** 1.0 · **Fecha:** 2026-08-05 · **Estado:** RC1.2

---

## SUMATE

### Finalidad
Casos públicos de ayuda y participación: adoptar, reunir, tránsito y eventos solidarios.

### Conservación de contexto (RC1.2)
`SumateViewModel` + `SavedStateHandle` conserva:

- categoría seleccionada;
- texto de búsqueda;
- filtro de organizaciones;
- modo de alertas asociado.

Al regresar desde detalle o formularios (`popBackStack` / `popBackStack(SUMATE)`), no se reinicia a Adopciones.
La barra inferior mantiene `launchSingleTop` + `saveState` + `restoreState`.
Tras publicar desde Sumate, se usa `popBackStack(SUMATE, inclusive = false)` (sin apilar otra instancia).

### Categorías (selector horizontal)
Adopciones · Perdidos · Encontrados · Tránsito · Eventos

### Cabecera
Título **Sumate** · subtítulo de ayuda · búsqueda · filtros (p. ej. publicado por organizaciones) · fondo crema/blanco.

### Tarjetas
Fotografías, badges, localidad, fecha, organización/persona, CTA contextual (Ver adopción, Ver alerta, etc.) vía contenidos existentes.

### Refugios
No flotan como módulo. **No** hay enlace genérico «Ver organizaciones» al final de Sumate.

Formas aprobadas (RC1.2):

- **Autor de caso:** en tarjetas de adopción (y análogos) mostrar «Publicado por» + nombre de organización / refugio + verificación si aplica.
- **Filtro:** «publicado por organizaciones» cuando el toggle de filtros esté activo (ya soportado).
- **Sección contextual / vacío:** solo si hay organizaciones reales relacionadas; no al pie de Sumate sin contexto.

Rutas M16/shelters y perfiles de organización se conservan; no se eliminan funcionalidades administrativas.

### Donaciones
No flotan como módulo. Permanecen en campañas/organizaciones/casos (ruta M17 conservada, fuera del hub principal).

### Gestiones privadas
Mis postulaciones / recibidas / orgs → menú hamburguesa de Perfil. Rutas intactas.

### Empty state
“No encontramos casos con estos filtros” · Limpiar filtros · Crear publicación de ayuda (Publicar).

---

## COMUNIDAD (UI: Servicios)

### Finalidad
Directorio de servicios para mascotas. Label inferior sigue siendo “Comunidad”.

### Categorías visibles (reales)
Veterinarias · Paseadores · Educadores · Tiendas

### Interacción de categoría (RC1.2)
Filtrado **en la misma pantalla** (no navegación a otra ruta):

1. Actualiza `selectedCategory` y muestra loading.
2. Título contextual: `Veterinarias cerca de vos`, `Paseadores cerca de vos`, etc.
3. Filtra resultados reales (no inventa prestadores).
4. Empty: «No encontramos resultados en esta categoría» + probar ubicación/filtros.
5. La selección se conserva en recomposición.
6. Cada tarjeta abre el perfil del prestador (`service_detail`).

### Jerarquía visual (RC1.2)
- Fondo pantalla: BrandCream `#FFF6EA`.
- Superficies/tarjetas: BrandWhite.
- Selección: BrandOrange `#FF7A00` (borde + fondo tonal suave).
- Texto: BrandText `#2F3A37`.
- Verificación / positivos: BrandGreen / BrandGreenDark.

### Estilo determinista de categorías
Icono en contenedor tonal fijo por categoría (no aleatorio, no cambia en recomposición):

| Categoría | Contenedor icono | Tint icono |
|-----------|------------------|------------|
| Veterinarias | verde suave | BrandGreenDark |
| Paseadores | naranja suave | BrandOrange |
| Educadores | verde suave | BrandGreenDark |
| Tiendas | naranja suave | BrandOrange |

- No seleccionada: superficie blanca, borde neutral.
- Seleccionada: fondo naranja tonal suave, borde BrandOrange, icono BrandOrange.
- Sin violeta ni plancha blanca completa.

### Diferencia Sumate vs Comunidad
- **Sumate:** refugios, asociaciones, rescate y causas solidarias (organizaciones de ayuda).
- **Comunidad:** veterinarias, paseadores, educadores, tiendas y servicios comerciales.

Un chip/categoría sin datos reales no se muestra como módulo vacío: las cuatro categorías listadas son las únicas visibles.

### Botón Filtros (RC1.2)
Abre `ModalBottomSheet` «Filtrar servicios» con controles compatibles con datos actuales:

- localidad/zona;
- solo activos;
- (categoría ya se elige en tiles; no se muestran distancia/valoración/verificados si el backend no los aplica).

Incluye cantidad de filtros activos, **Limpiar**, **Aplicar** y cierre sin dejar scrim (dismiss / cancelar no altera filtros aplicados).

### CTAs de creación en Sumate (RC1.2)
Por categoría, botón primario contextual (rutas existentes):

| Categoría | CTA | Destino |
|-----------|-----|---------|
| Adopciones | Publicar adopción | `ADOPTION_FORM` |
| Perdidos | Reportar mascota perdida | `PUBLISH_LOST_FOUND` |
| Encontrados | Informar mascota encontrada | `PUBLISH_FOUND_PET` |
| Tránsito | Ofrecer tránsito | `PUBLISH_FOSTER` |
| Eventos | Crear evento | `PUBLISH_EVENT` |

### Ocultas / futuras (no en UI principal)
Peluquerías, transporte, marketplace, etc. sin backend útil.

### Búsqueda
Servicios/profesionales + ubicación/zona · resultados filtrados localmente **y** por categoría seleccionada.

### Tarjetas de prestadores
Imagen, nombre, categoría, localidad, descripción breve, verificado/activo, CTA **Ver perfil**.

### Retirados de la UI principal
| Función | Destino correcto |
|---------|------------------|
| Red social | Inicio |
| Mensajes | Menú Perfil → Actividad |
| Marketplace próximamente | Oculto |
| Integraciones | Oculto (técnico/admin) |
| Reservas (módulo) | Detalle del prestador |
| Prestadores (módulo) | Las tarjetas ya lo son |
| Reputación (módulo) | Perfil/tarjeta de entidad |
| Asistencia inteligente | Oculto hasta contexto útil |

Rutas internas se conservan en NavGraph; no se muestran en la pantalla.

---

## Futuro documentado
- Peluquería / estética y transporte cuando existan categorías reales.
- Filtros avanzados de distancia/valoración con datos.
- Donar embebido en detalle de campaña/caso.
