# D08-11 — Especificación Inicio Social y Perfil

**Versión:** 1.0 · **Fecha:** 2026-08-05 · **Estado:** RC1.2

---

## 1. Inicio — estructura

1. Cabecera compacta: wordmark LeoVer · Buscar · Notificaciones · Mensajes (fondo blanco).
2. Tabs internas: **Feed** | **Reels** | **Explorar** (default: Feed).
3. Contenido según tab.

Sin grillas de módulos, sin barras naranjas altas, sin menús administrativos encima del feed.

---

## 2. Feed

1. Historias (fila horizontal; “Tu historia” + vacío preparado si no hay backend).
2. Selector **Para vos** / **Siguiendo**.
3. Lista de `LeoSocialPostCard`.
4. Carga incremental / fin de feed.
5. Empty states reales (sin inventar posts productivos).

**Siguiendo:** vacío correcto hasta existir grafo de follows (sin filtrar con datos inventados).

---

## 3. Historias

- Avatar circular, nombre corto, anillo de novedad.
- Agregar historia → snackbar “próximamente” si no hay backend.
- Componente preparado: `StoriesRow` / `StoryUiItem`.

---

## 4. Reels (dentro de Inicio)

- Pager vertical.
- Layout: media, cuenta, descripción, audio, acciones.
- Si no hay player: mensaje “Reproducción pendiente” + empty state.
- **Dependencia técnica pendiente:** reproducción de video nativa / pipeline de media.

---

## 5. Explorar (dentro de Inicio)

- Barra de búsqueda (filtro local + acceso a búsqueda global).
- Chips Personas / Mascotas / Tendencias → búsqueda.
- Cuentas sugeridas (nearby existentes).
- Cuadrícula 3 columnas de publicaciones.
- No incluye catálogo de servicios de Comunidad.

---

## 6. LeoSocialPostCard

Cabecera: avatar, cuenta, mascota opcional, ubicación, fecha, menú ⋮.  
Contenido: imagen / placeholder.  
Acciones: like, comentar, compartir, guardar.  
Info: likes, texto, preview comentarios.  
Especiales: badges ADOPCIÓN / PERDIDO·ENCONTRADO / URGENTE / PROMO + CTA.

---

## 7. Publicar

Selector en dos bloques (pantalla actual, estilo hoja):

**Crear contenido:** Publicación · Foto/carrusel · Reel · Historia.  
**Ayuda y causas:** Perdí / Encontré · Adopción · Tránsito · Evento · Urgent · etc. (role-gated).

Social no exige campos de adopción/pérdida.

---

## 8. Perfil social

- Cabecera: avatar, @usuario, bio, localidad, publicaciones / seguidores / seguidos, Editar, menú ☰.
- Mascotas: carrusel horizontal.
- Tabs: Publicaciones · Reels · Etiquetadas.
- Publicaciones: grilla 3 cols + lista texto.
- Guardados: solo en menú (privado).
- Empty compacto + CTA “Crear tu primera publicación”.

**Nota datos:** seguidores/seguidos usan conteo de amigos disponible hasta existir follows.

---

## 9. Menú hamburguesa

Grupos: **Actividad** (Mensajes · Notificaciones · Guardados · Borradores) · **Gestión** · Cuenta · Ayuda · Equipo (staff) · Sesión.  
Mensajes no vive en Comunidad. Reutiliza rutas existentes. Guardados/Borradores: “próximamente” si no hay pantalla.

---

## 10. Estados vacíos

Feed, Siguiendo, Reels, Explorar, Perfil posts/reels/etiquetadas, Historias sin datos.

---

## 11. Backend pendiente

| Capacidad | Estado |
|-----------|--------|
| Historias productivas | UI preparada |
| Reels con video | Layout + pending playback |
| Follow graph Para vos/Siguiendo | Siguiendo vacío |
| Guardados list screen | Snackbar próximamente |
| Borradores sociales | Snackbar próximamente |
| Etiquetadas | Empty preparado |
| Compartir nativo | Snackbar próximamente |
