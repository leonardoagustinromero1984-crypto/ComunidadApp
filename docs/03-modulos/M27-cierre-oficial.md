# M27 — Cierre oficial

**Módulo:** Integraciones y API pública (LeoVer)  
**Fecha:** 2026-08-03  
**Staging:** `wystsapjfpdtoprlmizz`

## Capacidades entregadas

- Apps integradoras (DRAFT→ARCHIVED), scopes allowlist, ambientes SANDBOX/STAGING
- Claves API: hash, prefijo, reveal-once, rotación/revocación
- Webhooks: endpoints, suscripciones, eventos sanitizados, entregas simuladas, firma stub, reintentos/dead-letter
- Rate limiting, sandbox aislado, OAuth stub (sin proveedor externo)
- SSRF en URLs de webhook; auditoría append-only; RLS deny + RPC SECURITY DEFINER

## Evidencia

| Verificación | Resultado |
|--------------|-----------|
| Validación remota | 130/130 PASS |
| Smoke remoto | 25/25 PASS |
| Tests unitarios M27 | 54/54 PASS |
| Migraciones 075–077 | Aplicadas en staging |

## Límites declarados

- Sin entrega HTTP productiva a Internet (worker futuro)
- Sin OAuth Google/Meta/Microsoft
- Sin pagos (M24 pospuesto)
- Sin secretos en Android ni logs

## Siguiente paso producto

**M28 no está definido en D01** (catálogo termina en M27). No iniciar M28/M29 sin especificación aprobada.
