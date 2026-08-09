# LeoVer — Auditoría Documental y Matriz de Vigencia

**Versión:** 1.0  
**Fecha:** 9 de agosto de 2026  
**Estado:** Auditoría prioritaria de transición a Documento Maestro v1.1  
**Ruta recomendada:** `/docs/00-startup/LeoVer-Auditoria-Documental-y-Matriz-de-Vigencia-v1.0.md`

## 0. Resultado ejecutivo

La auditoría confirma que el **Documento Maestro Integral v1.1** puede mantenerse como fuente estratégica superior: su catálogo de la sección 7 define capacidades del ecosistema y delega expresamente en D01 la traducción a identificadores Mxx, preservando identificadores existentes siempre que sea posible.

Se detectó, sin embargo, un **hallazgo crítico en D01 v1.1**: la versión creada el 9 de agosto volvió a asociar varios identificadores Mxx con el mapa original de julio, pero la documentación de ejecución posterior demuestra que esos identificadores ya fueron usados y desarrollados con otros dominios. Por lo tanto, **D01 v1.1 queda clasificado ACTUALIZAR y no debe utilizarse como autoridad para identificar M09–M15 hasta corregir la matriz de equivalencias**.

Esto no invalida el código ya construido ni obliga a renombrar módulos. La corrección debe hacerse en la documentación: **los identificadores ya usados por implementación, migraciones, commits, tests, rutas y cierres se consideran históricos e inmutables**. No se deben reciclar ni reinterpretar.

## 1. Regla de autoridad durante la corrección

Mientras se corrige D01, el orden operativo queda:

1. Documento Maestro v1.1 — estrategia, principios, alcance y decisiones aprobadas.
2. ADR y decisiones posteriores aprobadas que documenten remapeos reales.
3. Documentación de cierre/implementación real de cada módulo y sus migraciones/commits.
4. D01 corregido — una vez emitida la siguiente versión.
5. D01 v1.1 actual — sólo como borrador histórico de transición, no como mapa Mxx operativo.

Esta regla es temporal y existe únicamente para resolver la colisión de numeración descubierta por la auditoría.

## 2. Hallazgo crítico — colisión entre D01 v1.1 y módulos ejecutados

| ID | D01 v1.1 del 09/08 | Evidencia de ejecución previa | Clasificación |
| --- | --- | --- | --- |
| M08 | Mascotas, responsables y custodia | Mascotas y responsables | Compatible, con ajuste por microchip |
| M09 | Pasaporte LeoVer | Adopciones; migraciones 037–039 y cierre técnico | **COLISIÓN** |
| M10 | Búsqueda, ubicación y geoservicios | Hogares de tránsito; migraciones 040–041 | **COLISIÓN** |
| M11 | Plataforma web pública | Refugios / operación de refugios; migraciones 042–045 | **COLISIÓN** |
| M12 | Perdidos/encontrados y red de respuesta | Veterinarias; migraciones 046–047 y documentación de cierre | **COLISIÓN** |
| M13 | Avistamientos y coincidencias | Avistamientos y coincidencias | Compatible |
| M14 | Adopciones y postulaciones | Pasaporte e identidad verificable; ADR-014 de remapeo | **COLISIÓN** |
| M15 | Hogares de tránsito | Hogares de tránsito como capa funcional sobre persistencia M10/M08 | Compatible en nombre, pero depende de M10 ya ocupado por el mismo dominio |
| M16 | Gestión de casos y animales bajo cuidado | No se encontró evidencia de implementación iniciada en el corte auditado | Reservar hasta inventario completo |

### Consecuencia

No se debe “arreglar” esta situación renombrando código, migraciones, tests, rutas o documentos de cierre ya existentes. El costo y riesgo serían innecesarios. La solución correcta es generar un **D01 corregido** que parta de la historia real del repositorio y haga un crosswalk explícito entre capacidades estratégicas y módulos técnicos existentes.

## 3. Matriz de vigencia documental prioritaria

| Documento / familia | Estado | Decisión de auditoría |
| --- | --- | --- |
| Documento Maestro Integral v1.1 | **VIGENTE** | Mantener como fuente estratégica superior. No asigna Mxx rígidos en su catálogo y exige preservar identificadores existentes cuando sea posible. |
| Changelog Maestro v1.0 → v1.1 | **VIGENTE** | Mantener como trazabilidad de decisiones; agregar nota posterior sobre la corrección de numeración de D01. |
| D01 v1.1 del 09/08/2026 | **ACTUALIZAR** | Hallazgo crítico: colisiones M09/M10/M11/M12/M14 con módulos ya ejecutados. No usar para nombrar módulos hasta emitir corrección. |
| D01 v1.0 / Guía Cursor v1.0 | **HISTÓRICO** | Conserva la intención original, pero no refleja los remapeos de implementación posteriores. |
| M00 Fundación v1.0 y ADR que exijan NestJS/Prisma/Docker obligatorio | **ACTUALIZAR** | Supabase es backend autoritativo. Mantener cierres y evidencia histórica, retirar obligaciones incompatibles de las especificaciones vigentes. |
| M01 Identidad y autenticación | **VIGENTE** | Conservar. Revisar sólo alineación final de username obligatorio al alta, consentimientos y superficies iOS/web cuando se reabra. |
| M02 Usuarios, roles y permisos | **ACTUALIZAR** | Conservar roles técnicos/globales; reemplazar lenguaje de roles personales rígidos por capacidades contextuales e intención de onboarding. |
| M03 Organizaciones y equipos | **VIGENTE** | Base útil y coherente. Ajustar nomenclatura cuando corresponda: organización, refugio, establecimiento, profesional y sedes no son sinónimos. |
| M04 Administración, moderación y soporte | **VIGENTE** | Mantener; ampliar más adelante con riesgo publicitario, categorías reguladas y Brand Studio. |
| M05 Archivos, media y documentos | **VIGENTE** | Mantener: privado por defecto, Storage centralizado, referencias seguras, RLS y sin service role en cliente. |
| M06 Notificaciones | **VIGENTE** | Mantener; ampliar contratos para iOS/web y FCM real según implementación del piloto. |
| M07 Auditoría, analítica y observabilidad | **VIGENTE** | Mantener separación entre auditoría, métricas y observabilidad. Los plazos numéricos de retención siguen pendientes. |
| M08 Mascotas y responsables | **ACTUALIZAR** | Conservar responsabilidad, custodia, transferencias, historial y permisos. Retirar microchip del alcance V1/piloto y de gates que lo hagan obligatorio. |
| M09 Adopciones — implementación real | **VIGENTE** | Mantener ID M09 como histórico/técnico. Alinear decisiones de adopción reusable, transferencia trazable y decisión humana según Maestro v1.1. |
| M10 Hogares de tránsito — persistencia real | **VIGENTE** | Mantener ID M10 y migraciones 040/041. No reasignarlo a geoservicios. |
| M11 Refugios — implementación real | **VIGENTE** | Mantener ID M11 y su historia técnica. Ajustar lenguaje de “inventario” a animales bajo cuidado cuando se actualice. No reasignarlo a web pública. |
| M12 Veterinarias — implementación real | **ACTUALIZAR** | Mantener como base existente de veterinarias/agenda. Evolucionar hacia el portal profesional definido en v1.1 sin convertirlo en archivo clínico oficial. No reasignarlo a perdidos/encontrados. |
| M13 Avistamientos y coincidencias | **VIGENTE** | El matching determinista/explicable sigue siendo base válida. Ampliar después con embeddings visuales y confirmación humana; no sustituir el baseline. |
| M14 Pasaporte e identidad verificable | **ACTUALIZAR** | Mantener ID M14 y ADR de remapeo. Retirar microchip de V1/piloto, conservar QR, privacidad, proveniencia y permisos. |
| M15 Hogares de tránsito — capa funcional | **VIGENTE** | Mantener reconciliación ya decidida: M10/M08 son fuente autoritativa y M15 no duplica persistencia. El D01 corregido debe explicar esta relación. |
| M16 y siguientes no implementados en evidencia auditada | **ACTUALIZAR** | No asignar/reasignar hasta inventariar el repositorio completo y confirmar que el ID no fue utilizado. |
| Arquitectura Web nueva | **ACTUALIZAR** | Documento nuevo pendiente antes de implementación; no usar M11 porque M11 ya corresponde a Refugios. |
| Portal Veterinario ampliado | **ACTUALIZAR** | Especificación nueva/evolutiva pendiente; debe partir de M12 existente y decidir si se extiende M12 o se crea ID nuevo, sin duplicar pacientes/agenda. |
| Brand Studio | **ACTUALIZAR** | Especificación nueva pendiente antes de implementación; asignar ID sólo después de inventario de numeración libre. |
| Paquete legal | **ACTUALIZAR** | Pendiente de generación y revisión profesional antes del piloto público. |

## 4. Correcciones funcionales específicas detectadas

### 4.1 Microchip

La documentación M08 y M14 contiene normalizadores, índices, reglas y campos de microchip. El Maestro v1.1 lo dejó fuera de V1/piloto. La corrección documental no requiere borrar historial ni migraciones aplicadas; requiere que **ningún flujo, gate, UI o aceptación del piloto dependa del microchip**. Cualquier código existente puede quedar desactivado/legacy o ser retirado mediante una tarea técnica controlada después de auditar impacto.

### 4.2 Web pública

La capacidad web pública sigue siendo obligatoria, pero **no puede llamarse M11** en la documentación operativa nueva porque M11 ya identifica Refugios en la historia ejecutada. La especificación de Arquitectura Web debe recibir un identificador libre confirmado por inventario o un namespace separado.

### 4.3 Veterinarias

M12 ya fue utilizado para Veterinarias y tiene contratos de agenda/operación que pueden reutilizarse. El nuevo alcance de gestión profesional de salud debe evolucionar esa base, preservar separación entre establecimiento/profesional/atención y mantener la regla de que LeoVer no es el custodio legal primario de una historia clínica oficial.

### 4.4 Perdidos/encontrados y matching

M13 mantiene un baseline determinista basado en especie, tiempo, zona y rasgos, con revisión humana. Esto no contradice la incorporación futura de embeddings visuales: el modelo visual debe actuar como una señal adicional dentro del pipeline y nunca confirmar identidad automáticamente.

### 4.5 Tránsito M10/M15

La documentación del 1 de agosto ya detectó que M10 y M15 representaban el mismo dominio y decidió conservar M10 como persistencia autoritativa, usando M15 como experiencia/capa funcional sin duplicar tablas. Esa decisión debe conservarse. El D01 corregido no puede describir M10 como geoservicios.

## 5. Regla de numeración a adoptar

A partir de esta auditoría se recomienda formalizar la siguiente regla de gobierno:

> **Un identificador Mxx que haya sido usado en código, migraciones, rutas, tests, commits o documento de cierre no se renumera ni se reutiliza para otro dominio.**

Para capacidades nuevas existen dos opciones válidas, que deberán resolverse en el D01 corregido:

- asignar el siguiente Mxx realmente libre después de inventariar M00–Mxx; o
- separar el mapa estratégico de capacidades del ID técnico, utilizando códigos como `WEB`, `VET`, `BRAND`, etc., y vincularlos a uno o más módulos existentes/nuevos mediante una tabla de equivalencias.

La segunda opción reduce el riesgo de volver a acoplar la estrategia del producto a una numeración histórica.

## 6. Estado de los documentos creados el 9 de agosto

| Documento | Estado después de auditoría |
| --- | --- |
| `LeoVer-Documento-Maestro-v1.1` | **VIGENTE** |
| `CHANGELOG-Maestro-v1.0-a-v1.1` | **VIGENTE**, con futura nota de corrección D01 |
| `D01-Modulos-y-Orden v1.1` | **ACTUALIZAR CRÍTICO** |
| Esta auditoría | **VIGENTE** como registro del hallazgo hasta publicar D01 corregido |

## 7. Plan inmediato de corrección

1. No tocar código ni migraciones por este hallazgo.
2. Marcar D01 v1.1 actual como `ACTUALIZAR — NO USAR PARA IDENTIFICADORES M09–M15`.
3. Ejecutar inventario completo de identificadores M00–Mxx en el repositorio: documentos, paquetes, clases, rutas, tests, migraciones, ADR y commits/cierres.
4. Construir una matriz `ID → dominio real → estado → persistencia → migraciones → cierre → capacidad estratégica v1.1`.
5. Emitir **D01 v1.2** con numeración reconciliada, sin reusar IDs ocupados.
6. Agregar una nota al changelog indicando que D01 v1.1 fue una versión transitoria corregida por la auditoría.
7. Recién después actualizar la especificación del siguiente bloque real de desarrollo.

## 8. Criterio de cierre de esta auditoría

La auditoría documental prioritaria se considera cerrada cuando:

- el hallazgo de numeración está registrado;
- D01 v1.1 queda marcado para corrección;
- las familias M00–M15 prioritarias tienen estado de vigencia;
- se preservan módulos ya implementados y migraciones existentes;
- la próxima acción es un inventario técnico focalizado y la emisión de D01 v1.2, no una reimplementación.

**Conclusión:** el Documento Maestro v1.1 sigue siendo válido como fuente estratégica, pero el D01 v1.1 debe corregirse antes de orientar nuevo desarrollo por números Mxx.
