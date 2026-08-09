# D08-16 — Identidad pública y username

**Versión:** 1.0 · **Fecha:** 2026-08-06 · **Estado:** RC1.2 (pendiente prueba física)  
**Marca:** LeoVer

---

## Nombre vs username

| Campo | Uso |
|-------|-----|
| Nombre + Apellido | Identidad humana, saludo, perfil |
| Username | Identificador público único (`@usuario`) |

## Cuentas nuevas

- Username **obligatorio** en el alta.
- Formato: 3–30, minúsculas, `[a-z0-9._]`, sin espacios, sin `@` persistido.
- Normalización: trim, lowercase, quitar `@` inicial.
- No autocompletar desde nombre/correo/UUID.
- Disponibilidad con debounce + token (respuestas atrasadas no pisan el valor actual).
- Botón Crear cuenta deshabilitado hasta disponibilidad confirmada y resto válido.

## Unicidad

- Cliente: `is_username_available` (anon + authenticated tras migración 079).
- Backend: trigger `handle_new_user` valida y asigna username atómicamente desde metadata.
- Conflicto concurrente → mensaje recuperable: «Este nombre acaba de ser utilizado. Elegí otro.»

## Reservados

Lista única en cliente (`UsernameValidators.reservedWords`) alineada con `reserved_usernames` (migración 079).

## Persistencia / UI

- Guardar: `veroobregon`
- Mostrar: `@veroobregon`

## Altas parciales

Si falla el perfil/trigger, el signup de Auth no completa (excepción en trigger). El cliente muestra error recuperable y no inventa username.

## Cuentas existentes

**Confirmado:**

- no se modificaron usernames existentes;
- no hay backfill;
- no se fuerza renombre;
- la columna sigue permitiendo NULL histórico;
- el `ON CONFLICT` del trigger no sobrescribe username ya presente.

Migración: `supabase/migrations/079_signup_username_required_non_destructive.sql` (no aplicada remotamente en esta sesión).
