# M14 — Plan funcional y técnico

## Objetivo

Pasaporte e identidad verificable de mascotas sobre M08, sin historia clínica y sin rehacer M09.

## Alcance Bloque 1 (cerrado)

Dominio, fakes, UI `m14/*`, proyección pública local.

## Alcance Bloque 2 (cerrado local + remoto PASS)

Migraciones **050/051** aplicadas; 18 RPC base; repos Supabase; DML directo = 0.

## Alcance Bloque 3 (cerrado localmente)

- Migración **052** creada (no aplicada).
- Revisión humana: open / approve / reject / expire.
- Emisión directa verificada + revocación.
- Rotación `public_code`; QR/deep link sin PII.
- Concurrencia `FOR UPDATE` + decisión única.
- Guard CI highest = **052**.

## Alcance Bloque 4 (cerrado localmente)

- Expiraciones locales deterministas (`PENDING`, `UNDER_REVIEW`, credenciales por `expiresAt`).
- Privacidad final: proyección pública, QR sin PII, microchip enmascarado.
- Métricas agregadas sin PII + UI gestores.
- Hooks M06 preparados; M07 compatible sin ampliar catálogo.
- `SupabaseM14OperationsRepository` → `REMOTE_VALIDATION_PENDING`.
- Sin migración 053; 001–052 intactas.
- Pruebas automáticas **no ejecutadas** (decisión usuario); validación manual diferida.

## Exclusiones

Historia clínica, pagos, GPS, chat, biometría, autoverificación, M15.

## Propuesta Bloque 4 (exacta)

1. Expiraciones automáticas / jobs de cola. — **cerrado localmente**
2. Privacidad final y métricas sin PII. — **cerrado localmente**
3. Endurecimiento QR visual si falta dependencia. — **cerrado localmente**
4. Preparación M06 push real (sin afirmar entrega). — **cerrado localmente**
5. Regresión integral + documentación de cierre. — **documentación completada; tests no ejecutados**
6. Migración **053** solo si hace falta SQL post-apply 052. — **no requerida**
7. Smoke funcional remoto tras apply de 052. — **PENDIENTE EXTERNO**
8. Cierre técnico local; smokes M12/M13 siguen externos. — **COMPLETADO**

## Pendientes

```text
MIGRACIÓN 052 PENDIENTE DE APLICACIÓN REMOTA
VALIDACIÓN ESTRUCTURAL 052 PENDIENTE
M14 SMOKE FUNCIONAL PENDIENTE EXTERNO
M14 CIERRE OFICIAL PENDIENTE
GITHUB ANDROID CI PENDIENTE
M13 SMOKE FUNCIONAL PENDIENTE EXTERNO
M12 SMOKE FUNCIONAL PENDIENTE EXTERNO
```

M15 no iniciado.
