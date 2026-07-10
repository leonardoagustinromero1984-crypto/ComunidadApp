# LeoVer — API pública (stub Fase 4)

Documento de diseño. **No hay servidor HTTP propio aún**; la app usa Supabase (PostgREST + Auth + Storage).

## Endpoints previstos (futuro)

Base sugerida: `https://api.leover.app/v1`

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/adoptions` | Listado público de adopciones disponibles |
| GET | `/adoptions/{id}` | Detalle |
| GET | `/lost-found` | Alertas activas |
| GET | `/events` | Eventos próximos |
| GET | `/services?category=VET` | Directorio de servicios |
| POST | `/webhooks/municipality` | Integración municipios (auth por API key) |

## Matching (actual)

`PlatformRepository.computeAdoptionMatches` calcula un score heurístico en cliente (zona, actividad). Persistencia opcional en `adoption_matches` (migración 012).

## Historial clínico (actual)

Tabla `pet_clinical_records` + UI en detalle de mascota. No reemplaza historia clínica veterinaria formal.

## Integraciones

- Pagos: `payment_intents` con proveedor `MANUAL` / `MERCADOPAGO_STUB` (sin SDK aún).
- Email: Resend SMTP vía Supabase Auth.
