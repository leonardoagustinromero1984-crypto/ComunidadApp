# ADR-014 — Remapeo técnico de M14 a Pasaporte

## Estado

```text
APROBADO
```

## Contexto

El roadmap de producto contiene:

```text
M14 — Adopciones y postulaciones
```

pero el track técnico ya implementó y cerró ese alcance como:

```text
M09 — Adopciones y postulaciones
```

Reutilizar M14 técnico para volver a desarrollar adopciones produciría duplicación, regresiones y numeración inconsistente.

El roadmap también contiene el producto Pasaporte de la mascota, que todavía no fue consolidado como módulo técnico independiente.

## Decisión

El siguiente módulo del track técnico será:

```text
M14 — Pasaporte e identidad verificable de mascotas
```

La equivalencia de roadmap queda:

```text
Producto M14 Adopciones -> cubierto por M09 técnico
Producto Pasaporte -> implementado como M14 técnico
```

M14 se construye sobre M08 y puede integrar credenciales de M09 y M12, sin duplicar esos módulos.

## Consecuencias positivas

- evita rehacer adopciones;
- cubre un vacío funcional real;
- preserva la numeración técnica;
- crea una identidad estable de mascota;
- permite credenciales verificables y vista pública segura;
- mantiene separación entre identidad y clínica.

## Restricciones

- M08 conserva la autoridad sobre mascota y responsables;
- M14 no es historia clínica;
- Bloque 1 no crea SQL;
- la próxima migración posible será 050 y requiere aprobación;
- no existe autoverificación;
- no se expone PII ni documentos completos;
- M12 y M13 continúan con smokes externos pendientes.

## Fuente canónica

```text
docs/03-modulos/M14-pasaporte-identidad-verificable.md
```
