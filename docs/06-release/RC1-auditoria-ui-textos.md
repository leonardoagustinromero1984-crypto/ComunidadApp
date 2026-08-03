# RC1 — Auditoría UI y textos

**Alcance:** strings user-facing M00–M27; advertencias stub; ausencia referencias pagos.

## Convenciones verificadas

| Tema | Estado |
|------|--------|
| Marca **LeoVer** | OK en docs; UI usa LeoVer donde aplica |
| M17/M25 sin procesamiento dinero | Textos indican stub / sin pagos reales |
| M26 advertencia IA asistida | Presente en hub M26 |
| M27 OAuth/webhook simulados | Advertencias sandbox en hub |
| M24 ausente | Sin textos "pagado"/"reembolso" en M25 order flow |
| Errores user-facing | Español; sin stack traces visibles |

## Módulos revisados

- **M17:** "pagos reales todavía no habilitados" en hub — correcto.
- **M25:** estados pedido sin PAID/REFUND — correcto.
- **M26:** recomendaciones con revisión humana — correcto.
- **M27:** entrega webhook simulada — correcto.
- **M23:** estados reserva en español — OK.

## Hallazgos

| ID | Severidad | Descripción | Acción RC1 |
|----|-----------|-------------|------------|
| UI-001 | BAJO | Pantallas legacy (chat, foster) mezclan terminología antigua | Backlog |
| UI-002 | BAJO | M17 tabs Bienes/Voluntariado clicables sin destino | Backlog (NAV-002) |
| UI-003 | MEJORA | Hub naming inconsistente (hub vs home vs list) | Backlog |

## Accesibilidad básica (Parte L)

| Criterio | Estado |
|----------|--------|
| Iconos accionables con contentDescription | Parcial — mayoría OK en M16–M27 |
| Botones con etiqueta | OK en flujos principales |
| Estados no solo por color | OK (texto + icono en Error/Empty) |
| Textos cortados evidentes | No detectados en revisión estática |

Sin rediseño visual en RC1.

## Veredicto

Textos **aptos** para RC1. Sin mensajes que afirmen pagos, OAuth productivo o IA definitiva.
