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

## Exclusiones

Historia clínica, pagos, GPS, chat, biometría, autoverificación, M15.

## Propuesta Bloque 4 (exacta)

1. Expiraciones automáticas / jobs de cola.
2. Privacidad final y métricas sin PII.
3. Endurecimiento QR visual si falta dependencia.
4. Preparación M06 push real (sin afirmar entrega).
5. Regresión integral + documentación de cierre.
6. Migración **053** solo si hace falta SQL post-apply 052.
7. Smoke funcional remoto tras apply de 052.
8. Cierre técnico local; smokes M12/M13 siguen externos.

## Pendientes

Apply remoto 052; smoke M14 B3; smokes/cierres oficiales M12/M13; Bloque 4; M15 no iniciado.
