# RC1 — Matriz de integraciones M00–M27

Leyenda estado: **OK** verificado en código/tests; **PARCIAL** legacy paralelo; **N/A** módulo pospuesto.

| Origen | Destino | Contrato | Autoridad | Privacidad | Fallback | Error | Tests | Estado |
|--------|---------|----------|-----------|------------|----------|-------|-------|--------|
| M01 | M02 | sesión → perfil/roles | `auth.uid()` | email no público | mock auth | tipado | auth tests | OK |
| M02 | M03 | permisos org | M02 + membership | orgId interno | mock | tipado | M02 gates | OK |
| M03 | M16 | org → refugio | shelter.manage | perfil público sanitizado | mock seeds | tipado | M16 tests | OK |
| M04 | M19 | moderación posts | moderation.* | metadata oculta | adapter | tipado | M19 foundation | OK |
| M05 | M19 | media posts | file RLS | URLs firmadas | null storage mock | tipado | M05 wiring | OK |
| M08 | M14 | mascota → pasaporte | pet owner | salud declarada | mock null domain | tipado | M14 tests | PARCIAL |
| M10 | M15 | foster core → ops | foster roles | datos placement | split mock | tipado | M15 tests | PARCIAL |
| M11 | M16 | legacy shelter → M16 | org link | público sanitizado | dual repo | tipado | M16 tests | PARCIAL |
| M16 | M17 | refugio → donaciones | org manager | sin PII donante | mock | tipado | M17 tests | OK |
| M16 | M18 | refugio → eventos | event.manage | zona pública | mock | tipado | M18 tests | OK |
| M18 | M19 | evento → feed/ref | content ref | asistencia no expuesta | mock | tipado | — | OK |
| M19 | M20 | post → conversación | participantes | mensajes privados | dual chat | tipado | M20 tests | PARCIAL |
| M20 | M21 | conversación → elegibilidad | contexto soporte excluido | sin userId | mock | tipado | M21 tests | OK |
| M21 | M22 | reseña → prestador | eligibility adapter | sanitizado | mock | tipado | M21 ops | OK |
| M22 | M23 | servicio → reserva | provider + customer | notas privadas | mock IDs nav | tipado | M23 tests | OK |
| M23 | M20 | reserva → mensaje | booking context | thread privado | adapter mock | tipado | M23 B3 | OK |
| M23 | M21 | completada → reseña | SERVICE_COMPLETED | sin teléfono | adapter | tipado | cross-module test | OK |
| M25 | M20 | pedido → mensaje | merchant/customer | dirección oculta | hook unavailable | tipado | M25 ops | OK |
| M25 | M21 | entregado → reseña | orden sin pago | catálogo sanitizado | mock | tipado | M25 ops | OK |
| M26 | M08/M12/M25 | fuentes IA | human review | PII scrubbed | stub | tipado | M26 tests | OK |
| M27 | modelos públicos | API/webhooks | developer role | secret scrub | sandbox | tipado | M27 tests | OK |
| M04 | contenidos | hide/remove | moderator | evidencia privada M05 | mock | tipado | moderation tests | OK |
| M05 | refs públicas | signed URL | FileAuthorization | owner-only | deny unknown | tipado | FileAuth tests | OK |
| M06 | todos | notificaciones | server writers | sin payload PII | ClientDenied | tipado | M06 tests | OK |
| M07 | todos | audit/errors | platform | redacción | mock metrics | tipado | M07 tests | PARCIAL |
| M24 | — | — | — | — | — | — | — | N/A |

## Integraciones críticas revisadas

1. **M16→M17:** navegación desde detalle refugio a `m17/hub`; campañas vinculadas org — OK.
2. **M16→M18:** acceso eventos desde refugio — OK.
3. **M18→M19:** referencias de contenido en modelos M19 — OK.
4. **M19→M20:** sin enlace directo automático; flujos manuales — PARCIAL.
5. **M20→M21:** contextos de soporte excluidos de elegibilidad — OK.
6. **M21→M22:** adapter elegibilidad servicio — OK.
7. **M22→M23:** reserva desde catálogo prestador — OK (mock IDs en nav).
8. **M23→M20:** adapter mensajería en mock — OK.
9. **M23→M21:** booking completed + eligibility records — OK.
10. **M25→M20/M21:** hooks opcionales; catálogo sin buyerId — OK.
11. **M26→fuentes:** solo entidades autorizadas en recomendaciones — OK.
12. **M27→públicos:** sanitización M27PrivacySanitizer — OK.
13. **M04→moderables:** M19 adapter — OK.
14. **M05→público/privado:** FileAuthorization — OK.

## Veredicto

Integraciones **operativas** para RC1. Dual legacy (chat/feed/service/shelter) documentado como PARCIAL, no bloqueante para prueba manual APK.
