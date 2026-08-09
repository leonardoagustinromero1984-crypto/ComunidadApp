# D08-13 — Flujos Crear: Publicación, Reel e Historia

**Versión:** 1.0 · **Fecha:** 2026-08-05 · **Estado:** RC1.2  
**Marca:** LeoVer

---

## Entradas al creador

| Origen | Ruta | Comportamiento Atrás |
|--------|------|----------------------|
| Barra inferior **Publicar** | `publish` | Destino anterior / tabs |
| Perfil **Crear** | `publish_from_profile` | Vuelve a **Perfil** |

Opciones del hub: **Publicación** · **Reel** · **Historia**.

---

## Publicación (`PostType.GENERAL`)

1. Texto (título + contenido).
2. Imagen opcional (galería).
3. Ubicación opcional.
4. Publicar → `FeedRepository` + storage `POST_MEDIA`.
5. Aparece en Feed y tab Publicaciones del Perfil.

---

## Reel (`PostType.REEL`)

1. Video obligatorio (galería / Photo Picker `VideoOnly`).
2. Preview / confirmación de selección.
3. Descripción obligatoria.
4. Ubicación opcional · ID de mascota opcional.
5. Subida por URI (no cargar bytes completos en memoria en el ViewModel).
6. Progreso vía estado `isLoading`; error recuperable en pantalla.
7. Visible en:
   - Feed (como post social),
   - tab **Reels** de Inicio,
   - tab **Reels** de Perfil.

Reproducción nativa avanzada: pendiente (preview con poster/`imageUrl` del asset).

---

## Historia (`PostType.STORY`)

### Acceso directo desde Inicio (`HOME_STORY_PLUS`)

1. Inicio → `+` en **Tu historia**.
2. Navega a `publish_story` (sin hub Publicar ni pantalla «Próximamente»).
3. Abre de inmediato el selector de medio (Photo Picker: Galería / imagen·video; botón Cámara reutiliza picker de imagen).
4. Cancelar el selector sin medio → vuelve a **Inicio** (sin scrim ni Publicar seleccionado).
5. Tras elegir medio → vista previa (imagen o etiqueta de video), texto opcional, mascota opcional.
6. `X` / Atrás / Cancelar: si hay contenido, confirma descarte; luego Inicio.
7. **Tu historia** publica → progreso → registro `STORY` con `expires_at` + 24 h → cierra y vuelve a Inicio; el carrusel se actualiza al refrescar feed.
8. Error de carga: mensaje recuperable en pantalla; el usuario puede cambiar medio o salir (no queda atrapado).
9. Cambiar de sección inferior cierra el flujo vía navegación del host (sin NavHost nuevo).

### Origenes

| Origen | Retorno |
|--------|---------|
| `HOME_STORY_PLUS` | Siempre Inicio (Feed) |
| `PUBLISH` | Inicio / pop creator |
| `PROFILE_CREATE` | Según ruta de perfil |

### Contenido

1. Imagen o video obligatorio (`ImageAndVideo`).
2. Texto opcional.
3. Mascota opcional.
4. `expires_at` = creación + **24 h** (`StoryExpiration`).
5. Visible en carrusel de Inicio mientras esté activa.
6. Consultas cliente excluyen historias vencidas (`isActiveStory` / `isExpired`).

No se borran físicamente al vencer en esta etapa.

---

## Permisos y media

- Photo Picker moderno (sin permisos legacy de almacenamiento cuando el sistema lo permite).
- Upload reutiliza `FileUploadCoordinator` + propósito `POST_MEDIA`.
- MIME declarado: `video/mp4` o `image/jpeg`.

---

## Backend

Migración no destructiva:

`supabase/migrations/078_social_reels_stories_expires_at.sql`

- `posts.pet_id`
- `posts.expires_at`
- índices por tipo / historias

**Aplicación remota:** pendiente de entorno staging autorizado.

### Compatibilidad sin migración 078 (RC1.2)

Si el remoto aún no tiene `posts.expires_at` / `posts.pet_id`, el cliente:

1. Intenta insertar con el schema completo.
2. Ante error de schema cache, reintenta sin esas columnas (`PostRowLegacy`).
3. Calcula la vigencia de 24 h en cliente con `createdAt + 24h` para el carrusel.

Cuando 078 se aplique en staging, el insert volverá a persistir `expires_at` sin cambiar la UX.

---

## Errores

| Caso | UX |
|------|----|
| Sin medio obligatorio | Mensaje en formulario, no crash |
| Fallo de subida | Mensaje recuperable, se puede reintentar |
| Sesión ausente | “Debés iniciar sesión para publicar” |
