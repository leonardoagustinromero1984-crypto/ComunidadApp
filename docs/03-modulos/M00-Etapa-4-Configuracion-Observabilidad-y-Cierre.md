# LEOVER — M00 Etapa 4: Configuración, observabilidad y cierre

**Módulo:** M00 — Fundación Técnica  
**Etapa:** 4 — Configuración, observabilidad, seguridad base y cierre  
**Estado de entrada:** Etapa 3 aprobada  
**Backend oficial:** Supabase  
**Objetivo:** completar la fundación técnica sin desarrollar funcionalidades de negocio.

---

## 1. Documentos obligatorios de entrada

Leer en este orden:

1. `/docs/01-producto/D01-Modulos-y-Orden.md`
2. `/docs/03-modulos/M00-Fundacion-Tecnica.md`
3. `/docs/02-arquitectura/M00-auditoria-inicial.md`
4. `/docs/02-arquitectura/M00-etapa-2-cierre.md`
5. `/docs/02-arquitectura/M00-etapa-3-cierre.md`
6. `/docs/adr/ADR-0001-Supabase-como-backend-principal.md`
7. `/docs/adr/ADR-0002-Arquitectura-Android-monolito-modular.md`
8. `/docs/adr/ADR-0003-Proveedores-e-inyeccion-de-dependencias.md`
9. `/docs/adr/ADR-0004-Acceso-a-datos-y-clientes-de-red.md`
10. `/docs/adr/ADR-0005-Ambientes-secretos-y-configuracion.md`
11. `/docs/04-calidad/M00-plan-de-calidad.md`
12. Este documento.

Las decisiones de los ADR aprobados tienen prioridad sobre cualquier requisito histórico incompatible.

---

## 2. Alcance autorizado

Implementar solamente:

1. Abstracción central de configuración de aplicación.
2. Configuración diferenciada y segura para debug y release.
3. Feature flags locales, tipadas y documentadas.
4. Abstracción de logging sin datos personales ni secretos.
5. Modelo común de resultados y errores técnicos.
6. Componentes reutilizables para loading, vacío, error y reintento.
7. Network Security Config y revisión de tráfico claro.
8. Pruebas unitarias de la nueva fundación.
9. Verificación local de build, tests y lint.
10. Revisión del workflow CI y documentación de su estado.
11. Cierre formal de M00.

---

## 3. Fuera de alcance

No realizar:

- Autenticación nueva ni cambios funcionales de login.
- Perfiles de usuario o mascotas.
- Migraciones funcionales de Supabase.
- Mapas, GPS, pagos o Mercado Pago.
- Nuevas pantallas de producto.
- NestJS, Docker, Prisma, TypeORM o segunda base de datos.
- Hilt o refactor masivo de inyección de dependencias.
- Modularización Gradle.
- Renombre de `applicationId`, `namespace` o paquete.
- Actualización masiva de dependencias.
- Eliminación de archivos Firebase legacy sin aprobación.
- Integración de Crashlytics, Sentry u otro proveedor externo.
- Cambios visuales generales de la aplicación.

---

## 4. Protección del repositorio

Antes de modificar:

1. Ejecutar `git status`.
2. Confirmar la rama actual.
3. Crear una rama limpia llamada:

```text
m00/etapa-4-config-observabilidad
```

4. Si existen cambios locales, preservarlos mediante commit checkpoint, stash nombrado o rama backup.
5. No incorporar a esta rama el WIP de GPS, mapas o pagos.
6. Registrar el estado inicial en el cierre.

---

## 5. Entregables técnicos

### 5.1 AppConfig

Crear una única fuente tipada para consultar configuración de la app.

Debe exponer, como mínimo:

- nombre del ambiente;
- modo mock/remoto;
- URL pública de Supabase sin exponer claves en logs;
- indicador debug;
- versión de la app;
- versión de código;
- configuración de logs;
- flags activas.

Reglas:

- La UI no debe leer directamente `BuildConfig` o `local.properties`.
- No duplicar valores en pantallas o repositorios.
- Una configuración ausente debe producir un comportamiento seguro y un mensaje técnico claro.
- Mantener el modo mock cuando no hay credenciales válidas.
- No registrar tokens, claves o datos personales.

Ubicación sugerida, adaptándola a la estructura existente:

```text
app/src/main/java/com/comunidapp/app/core/config/
```

No mover todo el proyecto solo para crear este paquete.

### 5.2 Ambientes

Mantener una solución liviana:

- `debug`: desarrollo local, mock permitido, logging técnico habilitado.
- `release`: no permitir tráfico claro, logging reducido y sin secretos.
- No crear flavors complejos si no son necesarios.
- No inventar credenciales de staging.
- Documentar cómo se incorporaría staging cuando exista infraestructura real.

Actualizar:

- `local.properties.example`
- README si corresponde
- ADR-0005 únicamente si la implementación exige una aclaración real

### 5.3 FeatureFlags

Crear una interfaz tipada y una implementación local.

Flags iniciales permitidas:

- `useSupabase`
- `enablePaymentsStub`
- `enableMapsExperimental`
- `enableVerboseLogging`

Reglas:

- Valores conservadores por defecto.
- Pagos y mapas experimentales deben permanecer desactivados en la rama limpia salvo que el código existente requiera otra cosa.
- No usar strings dispersos.
- Las flags no reemplazan permisos ni seguridad.
- Agregar pruebas unitarias de defaults y overrides.

Ubicación sugerida:

```text
app/src/main/java/com/comunidapp/app/core/featureflags/
```

### 5.4 AppLogger

Crear una abstracción pequeña:

- niveles debug, info, warning y error;
- implementación Android basada en `Log`;
- silenciamiento/reducción en release;
- soporte opcional de `Throwable`;
- sanitización básica.

Nunca registrar:

- contraseñas;
- tokens;
- claves;
- contenido completo de documentos;
- ubicación exacta;
- teléfono, email u otros datos personales;
- payloads completos de autenticación o pagos.

No integrar proveedores externos en M00.

Ubicación sugerida:

```text
app/src/main/java/com/comunidapp/app/core/logging/
```

### 5.5 Resultado y errores comunes

Definir un modelo común para nueva infraestructura, sin reescribir todos los módulos existentes.

Debe contemplar:

- éxito;
- error de red;
- no autorizado;
- prohibido;
- no encontrado;
- validación;
- conflicto;
- límite de solicitudes;
- servidor;
- configuración;
- desconocido.

Reglas:

- Separar mensaje técnico de mensaje visible al usuario.
- No filtrar excepciones internas en UI.
- Crear mappers incrementales para código nuevo.
- No migrar masivamente todos los repositorios en esta etapa.

Ubicación sugerida:

```text
app/src/main/java/com/comunidapp/app/core/result/
```

### 5.6 Estados comunes de UI

Completar componentes reutilizables:

- loading;
- contenido vacío;
- error;
- reintento.

Requisitos:

- Material 3;
- accesibles;
- textos parametrizables;
- acción de reintento opcional;
- previews cuando sea razonable;
- sin modificar masivamente las pantallas existentes.

Ubicación sugerida:

```text
app/src/main/java/com/comunidapp/app/ui/components/state/
```

### 5.7 Seguridad de red

Crear y asociar:

```text
app/src/main/res/xml/network_security_config.xml
```

Requisitos:

- tráfico claro deshabilitado por defecto;
- permitir excepciones locales únicamente cuando sean necesarias y solo en debug;
- no inventar dominios;
- mantener Supabase por HTTPS;
- revisar `AndroidManifest.xml`;
- documentar cualquier excepción.

### 5.8 Pruebas

Agregar pruebas unitarias para:

- AppConfig;
- FeatureFlags;
- sanitización o comportamiento básico de AppLogger;
- modelos/mapeo de errores;
- defaults seguros cuando faltan credenciales.

No eliminar ni debilitar los siete tests existentes.

Objetivo mínimo al cierre:

```text
tests existentes + nuevos tests = todos en verde
```

### 5.9 CI

Revisar `.github/workflows/android-ci.yml`.

Comprobar:

- build debug;
- unit tests;
- lint;
- ausencia de secretos;
- carga de reportes aun cuando una tarea falle.

Si la rama se publica y existe acceso al resultado remoto, documentarlo. Si no hay acceso o autenticación, declarar la validación local y dejar la ejecución remota como verificación del PR, sin inventar un resultado exitoso.

---

## 6. Orden de implementación

Ejecutar en bloques pequeños:

### Bloque 1 — Protección y análisis

- Git status, rama y backup.
- Listar archivos a crear/modificar.
- Confirmar que no se mezcla WIP funcional.

### Bloque 2 — Configuración y flags

- AppConfig.
- FeatureFlags.
- Pruebas.
- Build + tests + lint.

### Bloque 3 — Logging y errores

- AppLogger.
- Result/Error comunes.
- Pruebas.
- Build + tests + lint.

### Bloque 4 — Estados UI y seguridad

- Loading/Empty/Error/Retry.
- Network Security Config.
- Revisión Manifest.
- Build + tests + lint.

### Bloque 5 — Documentación y cierre

- Actualizar arquitectura y calidad.
- Revisar CI.
- Generar cierre M00.
- Ejecutar validación final.

Después de cada bloque, detenerse si build, tests o lint fallan y corregir antes de continuar.

---

## 7. Criterios de aceptación

M00 puede cerrarse solamente si:

- [ ] No se perdió WIP del usuario.
- [ ] La rama no contiene GPS, mapas o pagos funcionales.
- [ ] Supabase sigue siendo el único backend.
- [ ] Existe AppConfig central y tipado.
- [ ] Debug y release poseen defaults seguros.
- [ ] FeatureFlags son tipadas y tienen pruebas.
- [ ] AppLogger no expone datos sensibles.
- [ ] Existe modelo común de errores para código nuevo.
- [ ] Existen componentes loading/empty/error/retry.
- [ ] Network Security Config está asociado correctamente.
- [ ] No se renombró el paquete.
- [ ] No se realizó refactor masivo.
- [ ] Los tests anteriores siguen pasando.
- [ ] Los nuevos tests pasan.
- [ ] `assembleDebug` pasa.
- [ ] `testDebugUnitTest` pasa.
- [ ] `lintDebug` pasa con 0 errores.
- [ ] CI continúa configurado sin secretos.
- [ ] La documentación refleja el sistema real.
- [ ] Se creó el informe final de M00.

---

## 8. Archivos de cierre

Crear exactamente:

```text
/docs/02-arquitectura/M00-etapa-4-cierre.md
/docs/02-arquitectura/M00-cierre-final.md
```

`M00-etapa-4-cierre.md` debe incluir:

- rama y commits;
- estado inicial de Git;
- archivos creados, modificados y eliminados;
- decisiones tomadas;
- pruebas agregadas;
- comandos y resultados;
- revisión de seguridad;
- estado de CI;
- riesgos y deuda;
- checklist de aceptación.

`M00-cierre-final.md` debe resumir:

- qué fundación quedó lista;
- ADR vigentes;
- stack definitivo;
- controles de calidad;
- deuda aceptada;
- qué queda expresamente fuera;
- confirmación de que el siguiente módulo habilitado es M01;
- condiciones de entrada para M01.

---

## 9. Instrucción final

No iniciar M01 durante esta etapa.

Detenerse cuando ambos archivos de cierre hayan sido creados y las validaciones locales hayan finalizado. Presentar un resumen y esperar revisión explícita.
