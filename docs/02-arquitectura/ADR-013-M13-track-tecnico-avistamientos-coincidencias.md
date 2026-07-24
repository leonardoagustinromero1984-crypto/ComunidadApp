# ADR-013 — Continuidad del track técnico con M13

## Estado

```text
APROBADO
```

## Contexto

El roadmap de producto define:

```text
M13 — Avistamientos y coincidencias
```

pero el track técnico ya utilizó M12 para Veterinarias. Además, el repositorio contiene una implementación Lost/Found legacy con avistamientos básicos.

## Decisión

El próximo módulo técnico será:

```text
M13 — Avistamientos y coincidencias
```

M13 reutiliza y amplía el Lost/Found existente. No renumera M12 técnico, no crea un segundo módulo de mascotas perdidas y no elimina el legacy.

La base Lost/Found existente se trata como prerrequisito funcional equivalente a la porción base del M12 producto.

## Consecuencias

### Positivas

- evita duplicación;
- mantiene compatibilidad;
- preserva módulos ya cerrados;
- permite agregar matching y confirmación de forma incremental;
- mantiene trazabilidad del roadmap.

### Restricciones

- Bloque 1 no crea SQL;
- la primera migración posible de M13 será 048 y requiere aprobación separada;
- no se declara M12 técnico cerrado mientras su smoke siga pendiente;
- no se usa IA de imágenes ni autoconfirmación;
- exact location y contacto permanecen privados.

## Fuente canónica

La especificación funcional completa de M13 queda en:

```text
docs/03-modulos/M13-avistamientos-y-coincidencias.md
```
