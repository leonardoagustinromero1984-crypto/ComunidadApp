# RC1 — Auditoría de permisos y autoridades

**Principio:** backend (`auth.uid()`, RLS, RPC) es autoridad; Android valida UX pero no confía en IDs enviados.

## Fuentes de autoridad

| Actor | Mecanismo remoto | Mock equivalente |
|-------|------------------|------------------|
| Usuario autenticado | `auth.uid()` | `actorUserId` lambda en repos mock |
| Roles plataforma | M02 permissions | `MockPermissionRepository` |
| Organización | M03 membership + org permissions | mock org managers |
| Moderador | M04 moderation.* | mock admin actors |
| Propietario recurso | RLS owner column | repo checks |
| Prestador | M22 provider ownership | M22MockUsers |
| Comerciante | M25 shop ownership | M25MockUsers |
| Desarrollador integraciones | M27 developer role | M27MockUsers |

## Revisión por módulo

| Módulo | UI-only checks | Acepta userId ajeno | Mock/Supabase consistente |
|--------|----------------|---------------------|---------------------------|
| M01–M03 | No crítico | Denegado en repos | OK |
| M04 | Gates admin screens | Staff-only RPCs | OK |
| M08–M09 | Feature gates M08 | Transfer solo owner | OK |
| M16–M18 | Manage screens | Org-scoped | OK |
| M19–M20 | Author en UI | Repo valida actor | OK |
| M21 | Eligibility UI | Self-review blocked | OK |
| M22–M23 | Provider/customer views | Cross-user denied | OK |
| M25 | Merchant orders | Shop-scoped | OK |
| M26 | Review queue staff | Member vs admin | OK |
| M27 | Developer-only ops | otherOrg denied | OK |

## Hallazgos

| ID | Severidad | Descripción | Acción RC1 |
|----|-----------|-------------|------------|
| PERM-001 | MEDIO | Dual legacy repos pueden mostrar datos distintos según pantalla (chat vs M20) | Backlog |
| PERM-002 | BAJO | M23 nav graph usa IDs mock fijos para availability | Backlog |
| PERM-003 | INFO | Defectos SQL potenciales solo documentables (sin migración RC1) | Backlog SQL |

## Operaciones administrativas

Pantallas admin/moderación requieren permisos M02/M04. No se detectaron acciones admin expuestas sin gate en ViewModels revisados.

## Estados terminales

- M23: reject/cancel/no-show no reabren — verificado en tests.
- M27: app suspendida terminal — verificado en tests.
- M21: verificación rejected/revoked no muestra señal activa — OK.

## Veredicto

Modelo de permisos **coherente** para RC1. Sin filtración crítica local demostrada. Deuda legacy en PERM-001.
