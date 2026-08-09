# D08-15 — Mapa de alertas y privacidad de ubicación

**Versión:** 1.0 · **Fecha:** 2026-08-06 · **Estado:** RC1.2 (pendiente prueba física)  
**Marca:** LeoVer

---

## Finalidad

Mostrar alertas geolocalizadas de mascotas **perdidas** y **encontradas** cerca de la persona, con vista Mapa y Lista.

## Tipos incluidos

- Perdida (BrandOrange)
- Encontrada (BrandGreen)

## Excluidos

Adopciones, tránsitos, eventos, servicios/Comunidad, publicaciones sociales, mascotas sin alerta activa.

## Criterios de visibilidad

- estado ACTIVE;
- no resueltas / no eliminadas;
- coordenadas válidas para marcadores de mapa;
- filtros de zona, distancia, fecha y especie (si aplica).

Alertas activas sin GPS aparecen en Lista; no se inventan coordenadas.

## Privacidad

- No se muestran domicilios exactos ni coordenadas crudas al usuario general.
- Representación pública: zona/localidad + coordenadas redondeadas (~3 decimales) solo para dibujar.
- La ubicación persistida no se modifica por esta capa.

## Controles

- Mapa | Lista
- Todas | Perdidas | Encontradas
- Distancia (5 / 10 / 25 / 50 km)
- Fecha (7 / 30 días)
- Especie (si hay dato)
- Centrar / elegir zona manual (catálogo de zonas públicas)
- Permiso de ubicación opcional (sin bloquear: siempre hay elección manual)

## Clustering

Agrupación simple por celda en el canvas del mapa (sin SDK pesado adicional). El proyecto no tenía SDK de mapas; se usa representación Compose determinista.

## Estados

- Sin permiso → permitir ubicación o elegir zona
- Ubicación desactivada → configuración o zona manual
- Vacío → ampliar búsqueda / reportar perdida / informar encontrada
- Error → reintentar / otra zona

## Navegación

Marcador / tarjeta → preview → **Ver alerta** → detalle real (`lost_found_detail/{postId}`).
Formularios de alta reutilizan `PUBLISH_LOST_FOUND` / `PUBLISH_FOUND_PET`.
