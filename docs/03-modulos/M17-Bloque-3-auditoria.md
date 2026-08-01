# M17 Bloque 3 — Auditoría

## Autoridades reutilizadas

| Dominio | Uso Bloque 3 |
|---------|--------------|
| M03 | Necesidades y oportunidades pertenecen a organización |
| M01 | Postulaciones y pledges vinculados a userId interno |
| M15 | **No** confundir transporte voluntario con tránsito |
| M16 | Enlace contextual desde detalle refugio → hub M17 |
| M05 | Comprobantes vía `receiptRef` sanitizado |
| M24 | Pagos reales siguen diferidos |

## Modelos previos auditados

- M16 `M16ShelterNeed` — referencia semántica; M17 in-kind es capa propia
- M11 `ShelterVolunteer` legacy — **no duplicado**; M17 voluntariado es track producto M17
- M15 tránsito — sin conversión automática voluntario → placement

## Alcance Bloque 3

- Donaciones de bienes (mock)
- Voluntariado (mock)
- Transparencia de campañas (mock)
- `M17ContributionIntentService` provider-agnostic
- **Sin migración 055**
- **Sin SQL**
- **Sin Supabase remoto** para bienes/voluntariado
