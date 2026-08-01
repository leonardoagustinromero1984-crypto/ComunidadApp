# M15 Bloque 1 — Validación

## Estado

```text
M15 BLOQUE 1 CERRADO LOCALMENTE
COMPILACIÓN KOTLIN PASS (ver SHA post-commit)
PRUEBAS AUTOMÁTICAS NO EJECUTADAS
VALIDACIÓN FUNCIONAL MANUAL PENDIENTE
```

## Revisión manual realizada

| Ítem | Resultado |
|------|-----------|
| Transiciones solicitud | SUBMITTED→ACCEPTED reserva placement RESERVED |
| Disponibilidad derivada | ACTIVE + cupos → AVAILABLE/LIMITED/FULL |
| Privacidad | `privateAddressText` ausente en `M15FosterHomePublicListing` |
| Permisos | Solo owner revisa; solicitante M08 en mock |
| Navegación | Rutas `m15/hub`, `m15/homes`, … registradas |
| Legacy M10 | Sin modificaciones en Foster* / foster_* |
| SQL / 053 | Ausente |
| Secretos | No incluidos |
| M05/M06/M07 | Hooks y audit locales; sin push real |

## Compilación

```powershell
.\gradlew.bat compileLocalDebugKotlin --no-configuration-cache --max-workers=1 --console=plain
```

## Limitaciones

- Store M15 independiente de M10 (datos no compartidos en B1).
- Entrada UI M15 hub no enlazada desde Sumate (legacy foster sigue activo).
- Supabase M15 inexistente.

## Pendientes externos preservados

M14 052, M13/M12 cierres, GitHub CI.
