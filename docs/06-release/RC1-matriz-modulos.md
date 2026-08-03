# RC1 — Matriz de módulos M00–M27

| Módulo | Nombre | Estado | Migraciones | Tests | Validación remota | Smoke | Integración | Riesgos | Listo prueba manual |
|--------|--------|--------|-------------|-------|-------------------|-------|-------------|---------|---------------------|
| M00 | Fundación técnica | Cerrado | 001–012 | CI base | N/A | N/A | OK | Legacy debt | Sí |
| M01 | Identidad y auth | Cerrado | 004,015–016 | Auth tests | Staging | N/A | OK | — | Sí |
| M02 | Usuarios y permisos | Cerrado | 018 | M02 gates | Staging | N/A | OK | — | Sí |
| M03 | Organizaciones | Cerrado | 019–021 | Org tests | Staging | N/A | OK | — | Sí |
| M04 | Admin/moderación | Cerrado | 022–023 | Moderation | Staging | N/A | OK | — | Sí |
| M05 | Archivos/medios | Cerrado | 002,017,024–025 | M05 wiring | Staging | N/A | OK | null storage mock | Sí |
| M06 | Notificaciones | Cerrado | 013,026–028 | M06 stages | Staging | N/A | OK | ClientDenied delivery | Sí |
| M07 | Observabilidad | Cerrado | 029–034 | M07 stages | Staging | N/A | PARCIAL | metrics mock | Sí |
| M08 | Mascotas | Cerrado | 003,035–036 | M08 regression | Staging | N/A | PARCIAL | domain Supabase-only | Sí |
| M09 | Adopciones | Cerrado | 037–038 (+039 local) | Adoption tests | Staging | N/A | OK | SQL-001 | Sí |
| M10 | Ubicación/foster base | Cerrado | 040–041 local | Foster tests | Parcial | N/A | PARCIAL | 039–052 gap | Sí (mock) |
| M11 | Refugios legacy | Cerrado | 042–045 local | M11 tests | Parcial | N/A | PARCIAL | dual M16 | Sí |
| M12 | Veterinaria | Cerrado | 046–047 local | M12 tests | Parcial | N/A | OK | SQL-001 | Sí |
| M13 | Avistamientos | Cerrado | 048–049 local | M13 tests | Parcial | N/A | OK | SQL-001 | Sí |
| M14 | Pasaporte | Cerrado | 050–052 local | M14 tests | Parcial | N/A | OK | SQL-001 | Sí |
| M15 | Hogares tránsito | Cerrado | — | M15 tests | N/A | N/A | PARCIAL | split M10 mock | Sí |
| M16 | Refugios org | Cerrado | 053 | M16 tests | 130/130 | 25/25 | OK | — | Sí |
| M17 | Donaciones/voluntariado | Cerrado | 054–057 | M17 tests | 130/130 | 25/25 | OK | sin pagos | Sí |
| M18 | Eventos | Cerrado | 058–059 | M18 tests | 110/110 | N/A | OK | — | Sí |
| M19 | Red social | Cerrado | 060–061 | M19 tests | 105/105 | N/A | PARCIAL | dual feed | Sí |
| M20 | Mensajería | Cerrado | 062–063 | M20 tests | 125/125 | 25/25 | PARCIAL | dual chat | Sí |
| M21 | Reputación | Cerrado | 064–065 | M21 tests | 130/130 | 25/25 | OK | — | Sí |
| M22 | Prestadores | Cerrado | 066–067 | M22 tests | 75/75 | 25/25 | OK | — | Sí |
| M23 | Agenda/reservas | Cerrado | 068–069 | M23 tests | 110/110 | 25/25 | OK | mock nav IDs | Sí |
| **M24** | **Pagos** | **POSPUESTO** | **—** | **—** | **—** | **—** | **N/A** | **No iniciado** | **Fuera RC1** |
| M25 | Marketplace | Cerrado | 070–071 | M25 tests | 120/120 | 25/25 | OK | sin pagos | Sí |
| M26 | IA asistida | Cerrado | 072–074 | M26 tests | 125/125 | 25/25 | OK | stub | Sí |
| M27 | Integraciones/API | Cerrado | 075–077 | M27 tests | 130/130 | 25/25 | OK | sandbox | Sí |

## M24 — fuera de RC1

- Estado: **POSPUESTO**
- Código: no iniciado
- Migraciones: ninguna
- Navegación: ausente
- Referencia: `docs/03-modulos/M24-auditoria-preliminar.md`

## M28

No catalogado en D01 v1.0. **No existe.**
