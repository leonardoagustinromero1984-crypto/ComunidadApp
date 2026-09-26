# Límites STAGING vs propuesta inicial PRODUCCIÓN

Los valores actuales en STAGING (`security_rate_limit_policies`) **no** son política comercial final.  
Propuesta conservadora para el primer corte de PRODUCCIÓN (presupuesto bajo). Leonardo puede ajustar.

Kill switches (iguales en ambos, default enabled=true):

- `media.video.upload.enabled`
- `reels.create.enabled`
- `imports.enabled`

| operación | STAGING actual | PRODUCCIÓN inicial (propuesta) | ventana | kill / monitor |
|---|---|---|---|---|
| social.post.create | 10 / h | 10 / h | 1 h | audit RATE_LIMIT_EXCEEDED |
| social.reel.create | 6 / h | 4 / h | 1 h | `reels.create.enabled` |
| social.story.create | 12 / h | 10 / h | 1 h | RATE_LIMIT_EXCEEDED |
| social.comment.create | 30 / 10 min | 30 / 10 min | 10 min | RATE_LIMIT_EXCEEDED |
| social.reaction | 60 / 10 min | 60 / 10 min | 10 min | RATE_LIMIT_EXCEEDED |
| social.connection_request | 20 / h | 15 / h | 1 h | RATE_LIMIT_EXCEEDED |
| chat.message.send | 40 / min | 30 / min | 1 min | RATE_LIMIT_EXCEEDED |
| chat.conversation.create | 15 / h | 10 / h | 1 h | RATE_LIMIT_EXCEEDED |
| report.create | 15 / h | 15 / h | 1 h | RATE_LIMIT_EXCEEDED |
| media.register | 30 / h | 20 / h | 1 h | fail closed |
| media.upload.bytes.daily | 200 MiB | 150 MiB | 24 h | QUOTA_EXCEEDED |
| media.video.count.daily | 8 | 4 | 24 h | `media.video.upload.enabled` |
| signed_url.request (RPC) | 60 / 10 min | 40 / 10 min | 10 min | residual SDK P1 |
| vitacora.import.analyze | 6 / h | 4 / h | 1 h | `imports.enabled` |
| vitacora.import.execute | 4 / h | 3 / h | 1 h | `imports.enabled` |
| admin.staff.create | 10 / 10 min | 10 / 10 min | 10 min | AAL2 + staff.manage |
| admin.password_reset | 10 / 10 min | 10 / 10 min | 10 min | AAL2 |
| admin.role_change | 20 / h | 10 / h | 1 h | AAL2 |

Feed / messages: sin cursor. Deuda de costo, no P0. Plan: cursor en bloque funcional posterior; no cap silencioso que esconda historial. Monitorear tamaño de payload RPC.

APIs externas (Maps, Google, Firebase, Klipy): no desplegar `klipy-proxy` / `push` hasta review. Restringir keys por paquete/SHA en consolas (manual).
