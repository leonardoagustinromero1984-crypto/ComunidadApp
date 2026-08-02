# M24 — Auditoría preliminar (pagos)

**Estado:** preauditoría documentada — **implementación NO iniciada**  
**Fecha:** 2026-08-02

## 1. Nombre y alcance según D01

**M24 Pagos, comisiones y suscripciones:** pago, split, reembolso, conciliación y planes.

M23 cerrado sin pagos reales. M24 es el primer módulo autorizado para cobros.

## 2. ¿LeoVer procesará pagos dentro de la plataforma?

**Pendiente decisión de producto.** Opciones: marketplace facilitator, redirect a proveedor, o híbrido por vertical (donaciones M17 vs reservas M23 vs marketplace M25).

## 3. Actores

- Cliente / adoptante / donante
- Prestador / comercio / refugio (organización M03)
- Plataforma LeoVer (comisión)
- Proveedor de pagos (PSP)
- Administración / conciliación

## 4. Países y monedas iniciales

**Pendiente.** Candidato inicial: Argentina (ARS). Requiere confirmación legal y PSP.

## 5. Entidades involucradas

| Módulo | Uso en pagos |
|---|---|
| M22 prestadores | Cobro de servicios |
| M23 reservas | Señal/cobro post-reserva (futuro) |
| M17 campañas | Donaciones |
| M25 marketplace | Pedidos |
| M18 eventos | Inscripciones pagas |

## 6–14. Capacidades a definir

| Tema | Estado |
|---|---|
| Pagos únicos | Por definir |
| Señales (M23) | Producto debe decidir si aplica |
| Donaciones | M17 preparado conceptualmente; PSP pendiente |
| Reembolsos | Política pendiente |
| Comisiones | Modelo pendiente (% fijo vs tier) |
| Conciliación | Operación pendiente |
| Chargebacks | Proceso pendiente |
| Facturación | AFIP / local pendiente |
| Impuestos | Asesoría pendiente |
| KYC/KYB | Nivel requerido por PSP pendiente |
| PCI | Tokenización obligatoria; no PAN en LeoVer |
| Tokenización | Delegada al PSP |
| Webhooks | Requeridos; diseño pendiente |

## 15–18. Seguridad

- **Idempotencia:** claves por intento de pago (patrón M23 `client_request_id`)
- **Proveedores candidatos:** Mercado Pago, Stripe (internacional), otros LATAM — evaluación pendiente
- **Datos que LeoVer NO debe guardar:** PAN, CVV, PIN, credenciales PSP completas, datos bancarios raw

## 19–22. Decisiones pendientes del responsable

1. ¿Pagos in-app vs redirect?
2. ¿Moneda y país launch?
3. ¿Comisión por reserva M23, donación M17, o ambos?
4. ¿Señal obligatoria en reservas?
5. ¿Suscripciones PRO en alcance inicial?
6. ¿Proveedor único o abstracción multi-PSP?

## 23. Riesgos

- PCI scope si se almacena data de tarjeta
- Conciliación manual sin webhooks
- Chargebacks en donaciones
- Regulatorio AR (PSP local)
- Acoplamiento prematuro M23↔M24

## 24. Propuesta de bloques (no autorizada aún)

| Bloque | Alcance tentativo |
|---|---|
| B1 | Modelo de dominio, estados pago, sin PSP |
| B2 | Migración intents + idempotencia |
| B3 | Integración PSP sandbox |
| B4 | Webhooks, conciliación, cierre |

## 25. Condiciones antes de programar

- [ ] Decisión producto: alcance M24 v1
- [ ] PSP seleccionado y contrato sandbox
- [ ] Política comisiones aprobada
- [ ] M23 cerrado ✅
- [ ] ADR PCI/tokenización
- [ ] Matriz países/monedas

## Código / migración / proveedor

| Artefacto | Creado |
|---|---|
| Código M24 | NO |
| Migración M24 | NO |
| Proveedor integrado | NO |
| M24 Bloque 1 | NO |
