# M23 — Auditoría inicial

**Módulo:** M23 Agenda y reservas  
**Fecha:** 2026-08-02

## 1. Problema

Usuarios y prestadores necesitan coordinar turnos sobre ofertas M22 sin pagos ni mensajería duplicada.

## 2. Objetivo

Disponibilidad, reservas, confirmación, cancelación y reprogramación con mock local (Bloque 1) y persistencia Supabase (Bloque 2).

## 3. Actores

Cliente, prestador/organización M03, moderador M04 (reportes), administrador.

## 4. Autoridades

M01 identidad, M03 permisos org, M08 mascota opcional, M10 zona horaria, M20 conversación contextual, M21 elegibilidad post-COMPLETED, M22 prestador/sede/oferta.

## 5. Dependencias

M22 obligatorio para providerId/offeringId; M24 pagos fuera de alcance Bloques 1–2.

## 6. Casos de uso

Consultar slots, solicitar reserva, confirmar/rechazar, cancelar, reprogramar, completar, no-show, bloqueos de agenda.

## 7. Privacidad

Sin customerUserId, email, teléfono ni notas internas en proyecciones públicas.

## 8. Permisos

Cliente: propias reservas. Prestador org: agenda y decisiones. Público: slots sanitizados vía RPC futura.

## 9. Estados

REQUESTED, CONFIRMED, REJECTED, CANCELLED_*, COMPLETED, NO_SHOW, EXPIRED — terminales documentados.

## 10. Riesgo doble reserva

Solapamiento detectado en dominio; Bloque 2 usará RPC transaccional.

## 11. Zonas horarias

ZoneId explícito por prestador/regla; Instant interno; UI local.

## 12. Cancelación

Ventana mínima en política snapshot; sin penalización económica (M24).

## 13. Reprogramación

Historial preservado; misma reserva con nuevos instantes.

## 14. No-show

Solo tras ventana de gracia post inicio confirmado.

## 15. Pagos diferidos M24

Precio informativo M22; sin cobro ni seña.

## 16. Notificaciones M06

Adapter stub; infraestructura no disponible no bloquea.

## 17. Integración M21

COMPLETED puede habilitar adaptador; M23 no crea reseñas.

## 18. Propuesta de bloques

B1 fundación mock, B2 SQL 068, B3 operaciones remotas, B4 activación staging.

## 19. Límites

Sin materializar slots infinitos; máx 31 días por consulta en generador.

## 20. Fuera de alcance

Pagos M24, M12 citas vet paralelas, producción SQL en Bloque 1.
