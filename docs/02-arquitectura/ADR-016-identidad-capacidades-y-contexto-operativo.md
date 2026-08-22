# ADR-016 — Identidad humana, capacidades y contexto operativo

## Estado

```text
APROBADO
```

## Contexto

LeoVer Documento Maestro Integral v1.1 establece que la identidad humana es única. La implementación histórica usaba `AccountType` / `account_type` (PERSON, SHELTER, FOSTER_HOME, VET, SHOP, TRAINER, WALKER) como si el usuario *fuera* esas entidades.

Eso contradice M03 (organizaciones), M10/M15 (hogar de tránsito), M12/M22/M28 (veterinaria/prestador) y M25 (tienda).

## Decisión

```text
IDENTITY = PERSON
CAPABILITIES != IDENTITY
ORGANIZATION != USER
FOSTER = PERSONAL CAPABILITY/PROFILE
PROVIDER = USER/ORG OWNED DOMAIN ENTITY
ACTIVE_CONTEXT = UI/OPERATIONAL CONTEXT
PLATFORM_ROLE != PRODUCT CONTEXT
```

| Concepto | Autoridad |
|---|---|
| Identidad humana | `auth.users` → `public.users` |
| Rol de plataforma | USER / MODERATOR / ADMIN / SUPERADMIN (018) |
| Organización | `public.organizations` (M03) |
| Membership | `organization_memberships` ACTIVE + permisos |
| Hogar de tránsito | `foster_home_profiles.owner_user_id` (M10/M15) |
| Prestador | M22 `owner_user_id` y/o `organization_id` |
| Veterinaria directorio | M12 clinic profile |
| Veterinaria prestador | M22 category VET |
| Profesional veterinario | M28 |
| Tienda | M25/M03 entidad comercial; checkout fuera de V1 |
| Contexto activo | `OperationalContext` local/sesión; PERSONAL siempre fallback |

No se crea tabla `contexts` ni `user_capabilities`. Los contextos se derivan de entidades canónicas. `FOSTER_AVAILABLE` se deriva de `foster_home_profiles.availability_status`.

`account_type` queda **LEGACY / DEPRECATED**: se deja de usar para identidad, permisos sensibles y navegación primaria. No se hace DROP en esta etapa.

## Veterinaria — no crear una cuarta entidad

```text
VET_DIRECTORY_AUTHORITY = M12 VeterinaryClinicProfile
VET_PROVIDER_AUTHORITY = M22 provider category VET
VET_PROFESSIONAL_AUTHORITY = M28
```

## Navegación

PERSONAL (cerrado, board UI V2): Inicio | Sumate | Publicar | Comunidad | Perfil.

Otros contextos reutilizan rutas funcionales actuales. No se cierra UX final de SHELTER / FOSTER / VET / SHOP / TRAINER / WALKER en este ADR.

## Eliminación futura de `account_type`

Cuando ningún cliente lea/escriba la columna, no queden RPC/RLS dependientes, y el backfill a PERSON esté validado: migración posterior DROP. No antes.

## Consecuencias

- Android resuelve `getMyAvailableContexts()` / `ActiveContext` en domain.
- Registro/onboarding no preguntan identidad excluyente.
- M25 transaccional: DISABLE_FROM_PRODUCT / KEEP_FOR_FUTURE.
- Donaciones a terceros: transferencia directa; 0% comisión; sin checkout LeoVer.

## Fuente canónica

```text
docs/00-maestro/LeoVer-Documento-Maestro-v1.1.md
docs/01-producto/D01-Modulos-y-Orden-v1.2.md
```
