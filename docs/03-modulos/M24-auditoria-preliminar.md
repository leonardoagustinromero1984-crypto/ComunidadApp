# M24 — Auditoría preliminar (pagos)

**Estado:** preauditoría documentada — **implementación NO iniciada**  
**Fecha:** 2026-08-02  
**Prerrequisito cumplido:** M23 cerrado oficialmente (`e1379e5`)

---

## 1. Nombre oficial (D01)

**M24 Pagos, comisiones y suscripciones**

## 2. Alcance según D01

Pago, split, reembolso, conciliación y planes de suscripción. Primer módulo autorizado para cobros reales tras M23 (agenda sin pagos).

## 3. Decisión pendiente: pagos in-platform

**Pendiente decisión de Leonardo / producto.** Opciones: facilitator in-app, redirect al PSP, o híbrido por vertical (M17 donaciones, M23 señales, M25 marketplace).

## 4. Actores

| Actor | Rol |
|---|---|
| Cliente / donante | Paga |
| Prestador / comercio / refugio | Recibe (vía split o liquidación) |
| Organización M03 | Titular comercial |
| LeoVer | Comisión / conciliación |
| PSP | Procesamiento, tokenización, chargebacks |
| Admin | Conciliación, soporte, disputas |

## 5. Donaciones (M17)

Cobro de campañas; trazabilidad M17 existente; **sin checkout real hoy**. Requiere PSP + política de comisión donación.

## 6. Reservas (M23)

Señal o cobro post-reserva **no implementado**. M23 deja `policy_snapshot` y estados; M24 debe definir si aplica seña.

## 7. Prestadores (M22)

Catálogo y sedes listos; falta vínculo cuenta de cobro / KYB prestador.

## 8. Comercios (M25)

Marketplace futuro; M24 debe ser extensible a pedidos sin acoplar solo a M23.

## 9. Monedas

**Pendiente.** Candidato: **ARS**. Multi-moneda diferida.

## 10. País inicial

**Pendiente.** Candidato: **Argentina**. Requiere asesoría fiscal y PSP local.

## 11. Pagos únicos

Caso base v1 (donación, reserva, pedido). Modelo de intent + estado terminal.

## 12. Señales (M23)

Producto debe decidir: obligatorias, opcionales, reembolsables, plazo de captura.

## 13. Reembolsos

Política pendiente: total/parcial, plazos, quién autoriza (cliente vs prestador vs admin).

## 14. Comisiones

Modelo pendiente: % fijo, tier por volumen, comisión donación vs servicio.

## 15. Conciliación

Webhooks PSP + tabla de intents; matching diario; excepciones manuales.

## 16. Chargebacks

Proceso pendiente; reserva de fondos; disputas con evidencia M23/M17.

## 17. Facturación

AFIP / comprobantes según jurisdicción — **pendiente definición**.

## 18. Impuestos

IVA, retenciones, tratamiento donaciones — **asesoría pendiente**.

## 19. KYC/KYB

Nivel según PSP y montos; prestadores/comercios probablemente KYB antes de cobrar.

## 20. PCI

**LeoVer no almacena PAN/CVV.** Scope mínimo vía tokenización delegada al PSP.

## 21. Tokenización

Delegada al PSP (Payment Element / Checkout Pro / equivalente).

## 22. Webhooks

Obligatorios para estados finales; idempotencia por evento; firma verificada.

## 23. Idempotencia

Patrón análogo M23 `client_request_id` → `payment_intent_id` + clave cliente.

## 24. Proveedores candidatos

Mercado Pago (AR), Stripe (internacional), otros LATAM — **evaluación pendiente**, sin integración.

## 25. Datos que LeoVer NUNCA debe guardar

PAN, CVV, PIN, magnetic data, credenciales PSP completas, CBU/cuenta bancaria raw sin tokenizar, secretos webhook en cliente.

## 26. Riesgos

- PCI scope por almacenamiento indebido
- Conciliación manual sin webhooks
- Chargebacks en donaciones
- Regulatorio AR / AFIP
- Acoplamiento prematuro M23↔cobro
- Fragmentación multi-PSP

## 27. Decisiones requeridas de Leonardo

1. ¿Pagos in-app vs redirect?
2. ¿País y moneda launch?
3. ¿Alcance v1: donaciones, reservas, ambos?
4. ¿Seña obligatoria en M23?
5. ¿Comisión LeoVer y %?
6. ¿Suscripciones PRO en v1?
7. ¿PSP único o abstracción multi-proveedor?
8. ¿Quién factura al cliente final?

## 28. Propuesta de bloques (no autorizada)

| Bloque | Alcance tentativo |
|---|---|
| B1 | Dominio, estados, mocks — sin PSP |
| B2 | Migración intents + idempotencia + RLS |
| B3 | PSP sandbox (un proveedor) |
| B4 | Webhooks, conciliación, cierre |

## 29. Condiciones previas para desarrollar

- [ ] Decisiones §27 aprobadas por Leonardo
- [ ] PSP sandbox contratado
- [ ] ADR PCI/tokenización
- [ ] Política comisiones y reembolsos
- [ ] Matriz países/monedas
- [x] M23 cerrado

## 30. Recomendación

**No iniciar M24 Bloque 1** hasta aprobar decisiones de producto y PSP. La preauditoría documenta riesgos y dependencias; no sustituye sign-off comercial/legal.

---

## Artefactos M24

| Artefacto | Creado |
|---|---|
| Código Kotlin M24 | **NO** |
| Migración SQL M24 | **NO** |
| Tablas / RPC pago | **NO** |
| Proveedor integrado | **NO** |
| Pagos reales procesados | **NO** |
| M24 Bloque 1 | **NO iniciado** |
