# Leover — Roadmap de implementación

**Versión:** 1.0  
**Fecha:** 8 de julio de 2026  
**Fuente:** [Documento Funcional v1.0](leover-documento-funcional.md)  
**Código base:** ComunidadApp (Android, Kotlin, Compose, MVVM, Supabase)

---

## 1. Principio rector

LeoVer es una **plataforma modular sobre un único perfil**. Cada usuario mantiene una identidad digital y activa módulos según su tipo de cuenta. Los módulos comparten perfil, notificaciones, seguidores, reputación, búsquedas y configuración.

Implementación en código:

| Concepto del documento | Ubicación en código |
|------------------------|---------------------|
| Módulos activables | `domain/LeoverModule.kt` |
| Categorías de usuario (§4) | `domain/LeoverModule.kt` → `UserCategory` |
| Matriz de permisos (§20) | `domain/ModulePermissions.kt` |
| Facade UI/navegación | `domain/RolePermissions.kt` |
| Módulos por cuenta | `User.resolvedModules` + `users.active_modules` (Supabase) |

---

## 2. Estado actual por fase

### Fase 1 — MVP (Documento §22)

| Requisito | Estado | Notas |
|-----------|--------|-------|
| Registro / login | ✅ ~90% | Supabase Auth + verificación email |
| Perfil de usuario | ✅ ~85% | Edición, avatar, tipos de cuenta |
| Perfil de mascota | ✅ ~85% | CRUD + salud; campos §7 (peso, raza, color) en modelo/DB |
| Red social | ⚠️ ~60% | Feed, likes, comentarios, búsqueda; faltan historias/reels |
| Chat | ✅ ~70% | 1:1 con Supabase + amistades |
| Adopciones | ⚠️ ~70% | Persistencia + solicitudes |
| Mascotas perdidas | ⚠️ ~55% | Persistencia + mapa básico |
| Búsquedas | ⚠️ ~50% | Búsqueda global de usuarios/posts |

### Fase 2 — Comunidad

| Módulo | Estado |
|--------|--------|
| Hogares de tránsito | ✅ Listado + publicar + solicitud |
| Refugios | ✅ Listado + detalle + publicar ficha |
| Donaciones | ✅ Campañas listado + publicar |
| Eventos | ✅ Listado + publicar + “Me interesa” |
| Reputación / insignias | ✅ Score + badges (RPC + UI perfil) |

### Fase 3 — Servicios

| Módulo | Estado |
|--------|--------|
| Veterinarias / Educadores / Paseadores / Tiendas | ✅ Directorio Comunidad + ficha |
| Mi negocio | ✅ Publicar ficha + agenda de turnos |
| Agenda / reservas | ✅ Solicitud + confirmación/cancelación |
| Pagos | ⚠️ Manual (estados UNPAID/PAID_*, sin pasarela) |
| Chat | ✅ Integrado desde ficha de servicio |

### Fase 4 — Plataforma inteligente

| Módulo | Estado |
|--------|--------|
| Notificaciones in-app | ✅ UI + ViewModel (`NotificationsScreen`) |
| Push FCM (retención) | ✅ Cliente + `device_tokens` (013) + Edge `push` — ver `docs/leover-push-fcm-setup.md` |
| Guardar / reportar / bloquear en feed | ✅ Overflow en `FeedPostCard` + filtro bloqueados |
| Matching adopciones | ✅ Sección en `MyAdoptionsScreen` |
| Avistamientos perdidos | ✅ Dialog + lista en `LostFoundScreen` |
| Reseñas de servicios | ✅ Form + lista en `ServiceDetailScreen` |
| Catálogo tienda + pagos manuales | ✅ En `MiNegocioScreen` (SHOP) |
| Moderación de reportes | ✅ `AdminModerationScreen` |
| Historial clínico mascota | ✅ Sección en `PetDetailScreen` |
| IA / API pública | ⏳ Pendiente |

Rutas nuevas: `notifications`, `admin_moderation`. Acceso desde Perfil.

---

## Setup migraciones Fase 2/3/4 + push

Ejecutar en Supabase (después de 001–010), en orden:

```
supabase/migrations/011_fase2_fase3_services.sql
supabase/migrations/012_fase1_to_4_closure.sql
supabase/migrations/013_device_tokens_fcm.sql
```

Push: desplegar Edge Function y webhook según `docs/leover-push-fcm-setup.md`.

---

## 3. Trazabilidad documento → código

| § Doc | Módulo | Pantallas | Repositorio | Tabla Supabase |
|-------|--------|-----------|-------------|----------------|
| §6 | Red social | `HomeScreen`, `PublishScreen` | `FeedRepository` | `posts` |
| §7 | Perfil mascota | `PetFormScreen`, `PetDetailScreen` | `PetRepository` | `pets` |
| §8 | Adopciones | `Sumate` → Adopciones | `AdoptionRepository` | `adoptions` |
| §15 | Perdidos | `Sumate` → Perdidos | `LostFoundRepository` | `lost_found_posts` |
| §10 | Refugios | `ShelterDetailScreen` | `ShelterRepository` (mock) | — |
| §4 | Tipos usuario | `RegisterScreen`, `EditProfileScreen` | `UserRepository` | `users` |

---

## 4. Cambios aplicados en esta iteración

1. **Arquitectura modular** — `LeoverModule`, `ModulePermissions`, matriz §20.
2. **Supabase Fase 1** — migración `005_fase1_community.sql` (adopciones, perdidos, módulos activos, campos mascota).
3. **Repositorios reales** — `SupabaseAdoptionRepository`, `SupabaseLostFoundRepository` vía `DataProvider`.
4. **Permisos corregidos** — hogares de tránsito pueden publicar adopciones (§20).
5. **Personas cerca** — filtro por `locationText` real, no mock estático.
6. **Estado perdidos** — `LostFoundStatus.ACTIVE | RESOLVED` (§15).
7. **Filtros por defecto** — adopciones `AVAILABLE`, perdidos `ACTIVE`.
8. **Fase 4 UI** — notificaciones, moderación, feed (guardar/reportar/bloquear), matching, avistamientos, reseñas, catálogo/pagos, historial clínico.

---

## 5. Próximos pasos recomendados (orden)

### Sprint A — Cerrar Fase 1 social
- [ ] Likes y comentarios en `posts` (tablas + UI interactiva)
- [ ] Pull-to-refresh y paginación en feed
- [ ] Tipo de publicación `URGENT`
- [ ] Búsqueda global (usuarios, mascotas, publicaciones)

### Sprint B — Cerrar Fase 1 adopciones
- [ ] Formulario de solicitud de adopción (§8)
- [ ] Dashboard publicador: solicitudes, estados
- [ ] Foto en publicación de adopción
- [ ] Pausar / marcar adoptada

### Sprint C — Cerrar Fase 1 perdidos
- [ ] Foto obligatoria en alerta
- [ ] Marcar como resuelta desde UI
- [ ] Vista mapa (v1.1 con GPS)

### Sprint D — Iniciar Fase 2
- [ ] Tablas y repos: refugios, eventos, donaciones, tránsito
- [ ] Reputación e insignias (modelo + visualización)

---

## 6. Setup requerido

Ejecutar en Supabase (en orden):

```
supabase/migrations/001_initial_schema.sql
002_storage.sql
003_pet_health_fields.sql
004_auth_user_profile_trigger.sql
005_fase1_community.sql   ← nuevo
```

Ver [`supabase-setup.md`](supabase-setup.md).

---

## 7. Documentos relacionados

| Archivo | Propósito |
|---------|-----------|
| `leover-documento-funcional.md` | Especificación funcional oficial v1.0 |
| `leover-requirements.md` | Historias de usuario detalladas (US-*) |
| `leover-phase0-implementation.md` | Plan sprint fundación (parcialmente legacy) |
| `qa-checklist-phase0.md` | Checklist QA manual |
