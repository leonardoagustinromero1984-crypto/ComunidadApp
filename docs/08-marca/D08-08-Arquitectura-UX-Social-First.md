# D08-08 — Arquitectura UX Social-First LeoVer

**Versión:** 1.0 · **Fecha:** 2026-08-05 · **Estado:** RC1.2 (corrección de arquitectura)

---

## Corrección explícita

La barra inferior **no cambia**. Se mantiene exactamente:

1. **Inicio**
2. **Sumate**
3. **Publicar**
4. **Comunidad**
5. **Perfil**

Queda descartada cualquier propuesta de reemplazarla por Inicio | Reels | Crear | Explorar | Perfil.

---

## Responsabilidad por destino

| Destino | Responsabilidad |
|---------|-----------------|
| **Inicio** | Experiencia social completa: Feed, Reels, Explorar, Historias, búsqueda, notificaciones, mensajes |
| **Sumate** | Casos públicos: adopciones, perdidos, encontrados, tránsito, eventos. Refugios/donaciones contextuales. Gestiones privadas en Perfil |
| **Publicar** | Acción central: contenido cotidiano + ayuda/causas estructuradas (sin refugio/donación genérica) |
| **Comunidad** | Directorio de **servicios** (UI “Servicios”): veterinarias, paseadores, educadores, tiendas. Sin red social ni módulos técnicos |
| **Perfil** | Perfil social; menú con Actividad (Mensajes, Notificaciones, Guardados, Borradores) y Gestión |

Detalle: `D08-12-Especificacion-Sumate-y-Comunidad.md`.

---

## Principios

- LeoVer es principalmente una **red social visual de mascotas**.
- Adopción, pérdida, tránsito y servicios son importantes, pero **no dominan Inicio**.
- **Reels** y **Explorar** viven **dentro de Inicio** (tabs internas), no en la barra inferior.
- **Publicar** sigue siendo el botón central prominente.
- **Perfil** no es una pantalla de configuración: es un perfil social con grillas y mascotas.
- Configuración, postulaciones, guardados, organizaciones, etc. van al **menú hamburguesa**.

---

## Navegación inferior (diseño)

- Fondo blanco.
- Seleccionado: naranja / contenedor tonal naranja.
- No seleccionados: gris.
- Publicar: tamaño, forma y elevación diferenciados (naranja suave, no verde dominante).

---

## Referencias

- Especificación detallada: `D08-11-Especificacion-Inicio-Social-y-Perfil.md`
- Sistema visual: `D08-03`, `D08-06`
