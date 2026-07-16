# M03 — Cierre Etapa 4: Equipos, invitaciones, sucursales y contexto

**Fecha:** 2026-07-16  
**Rama:** `m03/etapa-4-equipos-invitaciones-sucursales`  
**Módulo:** M03 — Organizaciones y Equipos  
**Estado de entrada:** Etapa 3 aprobada  
**Commit base:** `635943cd2fa56a401a23fd011835c82e34b23d7b`  
**Spec Etapa 4:** `docs/03-modulos/M03-Etapa-4-Equipos-Invitaciones-Sucursales-y-Contexto.md`  

---

## 1. Rama y commits

| Ref | SHA / nota |
|-----|------------|
| Commit base Etapa 3 | `635943cd2fa56a401a23fd011835c82e34b23d7b` |
| Rama | `m03/etapa-4-equipos-invitaciones-sucursales` |
| WIP GPS/mapas/pagos | **No** incorporado |
| Merge a `main` | **No** |
| M04 / Etapa 5 | **No** iniciados |

---

## 2. Migración

| Ítem | Valor |
|------|-------|
| Archivo | `supabase/migrations/020_organizations_teams_invitations_branches.sql` |
| Numeración | Siguiente a `019` (014–019 presentes en repo; **no editadas**) |
| Remoto / staging | **PENDIENTE DE VALIDACIÓN REMOTA** |

### Tablas nuevas

- `organization_invitations` — solo `token_hash` (SHA-256); sin token plano
- `organization_branches` — sin coordenadas / mapas / GPS

### RPC principales (actor `auth.uid()`, SECURITY DEFINER, `search_path = public`)

**Invitaciones**

- `invite_organization_member`
- `list_organization_invitations`
- `list_my_pending_invitations`
- `accept_organization_invitation` (transaccional; un solo uso)
- `decline_organization_invitation`
- `revoke_organization_invitation`

**Miembros**

- `list_organization_members`
- `change_organization_member_role` (no asigna OWNER; usar transfer)
- `suspend_organization_member`
- `remove_organization_member`
- `leave_organization` (protege último OWNER)

**Ownership / cierre**

- `transfer_organization_ownership` (atómica)
- `close_organization` (revoca invitaciones PENDING)

**Sucursales**

- `create_organization_branch`
- `update_organization_branch`
- `set_organization_branch_status`
- `list_organization_branches` (privado / público allowlist)

### Seguridad

- Sin INSERT/UPDATE/DELETE directo `authenticated` en invitaciones/sucursales
- No invitar OWNER; último OWNER protegido; anti-autoelevación
- Deny-by-default; org SUSPENDED/CLOSED/REJECTED bloquea operación
- Contacto de sucursal privado por defecto (`contact_phone_public = false`)

---

## 3. Android

| Pieza | Entrega |
|-------|---------|
| Contexto | `OrganizationContext` / `OrganizationContextProvider` (no reemplaza AuthState; limpia en logout; refresca permisos al cambiar org) |
| Repos | Interfaces + mocks extendidos; `SupabaseOrganizationInvitationRepository`; membership admin vía RPC; branches en `OrganizationRepository` |
| DataProvider | Invitaciones Supabase cuando `useSupabase` |
| UI | Panel administrar, equipo/invitaciones, sucursales; entrada desde Mis organizaciones |
| Gates | Acciones privilegiadas vía RPC/`hasOrgPermission`; deny ante loading/error |

---

## 4. Decisiones respetadas

- Token solo hash en persistencia
- Membresía solo al aceptar invitación
- AccountType / active_modules no conceden membresía
- Transferencia OWNER atómica
- Sucursales sin GPS/mapas/coordenadas
- Sin verificación documental (M04)
- Sin Hilt / Retrofit / NestJS
- Sin merge a `main`

---

## 5. Pruebas y calidad

| Control | Resultado |
|---------|-----------|
| Suite SQL documentada | `docs/04-calidad/M03-pruebas-equipos-invitaciones-sucursales.md` |
| Unit tests Etapa 4 | `OrganizationEtapa4RulesTest` (+ suites previas) |
| `assembleDebug` | **SUCCESS** |
| `testDebugUnitTest` | **172 tests, 0 failures, 0 errors** |
| `lintDebug` | **SUCCESS** |

### 5.1 Evidencia local de esta sesión

| Control | Resultado |
|---------|-----------|
| `assembleDebug` | **SUCCESS** (BUILD SUCCESSFUL) |
| `testDebugUnitTest` | **172 tests, 0 failures, 0 errors** |
| `lintDebug` | **SUCCESS** (BUILD SUCCESSFUL) |

---

## 6. Remoto

**PENDIENTE DE VALIDACIÓN REMOTA** para migraciones **014–020**.  
No se afirma deploy ni checklist staging/producción.

Release sigue bloqueado hasta staging documentado.

---

## 7. Fuera de alcance (respetado)

Verificación documental avanzada, M04, GPS/mapas/pagos, borrado físico completo de organizaciones, Hilt/Retrofit, merge `main`, producción, Etapa 5.

---

## 8. Checklist Etapa 4

- [x] Commit base confirmado (`635943cd…`)
- [x] Rama `m03/etapa-4-equipos-invitaciones-sucursales`
- [x] Migración `020` sin editar 014–019
- [x] Invitaciones con token hash
- [x] Expirables, revocables, un solo uso
- [x] Aceptación crea membresía atómicamente
- [x] Gestión de miembros con jerarquía
- [x] Último OWNER protegido
- [x] Transferencia ownership atómica
- [x] Sucursales sin mapas
- [x] Contactos privados por defecto
- [x] Historial/auditoría extendida
- [x] Escritura directa sensible revocada
- [x] OrganizationContext no reemplaza AuthState
- [x] Permisos refrescados / clear en logout
- [x] AccountType no concede membresía
- [x] UI y rutas protegidas
- [x] Sin M04
- [x] Calidad Gradle confirmada (actualizar §5.1)
- [x] Remoto declarado honestamente
- [x] Cierre creado

---

## 9. Parada

**No** se inicia Etapa 5 ni M04.  
**No** merge a `main`.

Siguiente habilitado solo tras aprobación y calidad local verde: **M03 Etapa 5** (calidad y cierre final).
