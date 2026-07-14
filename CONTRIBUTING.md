# Contribuir a Leover

Leé primero:

1. [`docs/01-producto/D01-Modulos-y-Orden.md`](docs/01-producto/D01-Modulos-y-Orden.md)
2. La especificación del módulo en [`docs/03-modulos/`](docs/03-modulos/)
3. Los ADR vigentes en [`docs/adr/`](docs/adr/)

Cursor y humanos: **no inventar alcance** ni implementar módulos futuros sin spec aprobada.

## Convenciones de ramas

| Prefijo | Uso |
|---------|-----|
| `m00/...` | Trabajo del módulo M00 (fundación) |
| `feat/<modulo>-...` | Feature de un módulo (ej. `feat/m12-mapa`) |
| `fix/...` | Corrección acotada |
| `chore/...` | Tooling, docs, CI |
| `checkpoint/...` | Salvaguarda de WIP (no mezclar con release) |
| `wip/...` | Experimental; no mergear a `main` sin limpieza |

- Una rama = un módulo o una tarea pequeña.  
- **Prohibido** mezclar en el mismo PR: fundación M00 + features de negocio (mapas, pagos, adopciones, etc.).

## Convenciones de commits

- Mensajes en inglés o español, pero **consistentes** en la rama.  
- Preferir forma: `tipo: resumen en imperativo`.  
  Ejemplos: `docs: add M00 stage 2 ADRs`, `fix: correct OAuth intent-filter lint`.  
- Un commit = un cambio entendible; no “todo junto”.

## Alcance por cambio

1. Cambios pequeños, compilables y testeables.  
2. No modularizar Gradle ni renombrar el paquete Android sin ADR/tarea.  
3. No añadir NestJS, Docker DB, Prisma/TypeORM (ADR-0001).  
4. No eliminar legado Firebase/docs sin inventario y verificación.  
5. Actualizar docs cuando cambie arquitectura o gobierno.

## Antes de abrir un PR

```bash
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
```

Lint puede fallar por deuda conocida ([plan de calidad](docs/04-calidad/M00-plan-de-calidad.md)); no introduzcan **nuevos** errores de lint en archivos tocados.

## Secretos

- Nunca commitear `local.properties`, `.env`, keys PEM, ni token service_role.  
- Usar los `*.example` con valores ficticios.  
- Revisar el diff antes del push.

## Pull requests

Usá la plantilla en [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md).
