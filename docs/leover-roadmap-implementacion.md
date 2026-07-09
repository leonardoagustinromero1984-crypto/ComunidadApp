# Leover — Roadmap de implementación

**Versión:** 1.0  
**Fecha:** 8 de julio de 2026  
**Fuente:** [Documento Funcional v1.0](leover-documento-funcional.md)  
**Código base:** ComunidadApp (Android, Kotlin, Compose, MVVM, Supabase)

---

## 1. Principio rector

Leover es una **plataforma modular sobre un único perfil**. Cada usuario mantiene una identidad digital y activa módulos según su tipo de cuenta. Los módulos comparten perfil, notificaciones, seguidores, reputación, búsquedas y configuración.

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
| Red social | ⚠️ ~45% | Feed + publicar; faltan likes, comentarios, historias |
| Chat | ❌ Fase 3 | Fuera de alcance MVP según doc |
| Adopciones | ⚠️ ~50% | Persistencia Supabase; falta flujo de solicitudes |
| Mascotas perdidas | ⚠️ ~45% | Persistencia + estado ACTIVE/RESOLVED; falta mapa |
| Búsquedas | ⚠️ ~30% | Filtros en adopciones/perdidos; falta búsqueda global |

### Fase 2 — Comunidad

| Módulo | Estado |
|--------|--------|
| Hogares de tránsito | 🔲 UI mock en Sumate |
| Refugios | 🔲 UI mock |
| Donaciones | 🔲 UI mock |
| Eventos | 🔲 UI mock |
| Reputación / insignias | 🔲 No iniciado |

### Fase 3 — Servicios

Veterinarias, educadores, paseadores, tiendas, agenda, reservas, pagos → **pendiente**.

### Fase 4 — Plataforma inteligente

IA, matching, historial clínico, API pública → **pendiente**.

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
