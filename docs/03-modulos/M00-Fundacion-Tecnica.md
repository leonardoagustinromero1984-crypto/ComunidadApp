# LEOVER — M00 Fundación Técnica

**Versión:** 1.0  
**Documento superior:** D01 — Mapa de Módulos y Orden de Desarrollo  
**Estado:** Aprobado para implementación  
**Uso:** este archivo es la guía operativa que Cursor debe leer antes de modificar código.

---

## 1. Objetivo del módulo

Establecer una fundación técnica segura, compilable, testeable y extensible para toda la plataforma Leover, reutilizando el proyecto Android existente y preparando la incorporación ordenada del backend, web, datos, observabilidad y automatización.

M00 no implementa funcionalidades de negocio. No crea usuarios reales, mascotas, adopciones, alertas, pagos ni mapas. Su resultado es una base técnica comprobable sobre la que se desarrollarán los siguientes módulos.

## 2. Regla principal

**No recrear el proyecto Android ni reemplazar código válido sin necesidad.**

Antes de programar:

1. Leer este documento completo.
2. Leer `D01-Modulos-y-Orden.md`.
3. Auditar la estructura actual del repositorio.
4. Identificar qué requisitos de M00 ya están cumplidos, cuáles están incompletos y cuáles faltan.
5. Proponer un plan de cambios mínimos y ordenados.
6. Implementar por etapas pequeñas, compilables y testeables.

Si la estructura actual difiere de la propuesta, preservar lo que funciona y documentar la adaptación. No realizar migraciones masivas de carpetas en una sola operación.

---

## 3. Decisiones técnicas aprobadas

### 3.1 Aplicación Android

- Kotlin.
- Jetpack Compose.
- Material Design 3.
- Gradle Kotlin DSL.
- Arquitectura por capas y por funcionalidades.
- MVVM o MVI liviano con flujo de datos unidireccional.
- Coroutines y Flow.
- Navigation Compose.
- Inyección de dependencias mediante Hilt, salvo que el proyecto ya posea una alternativa válida y consistente.
- Cliente HTTP mediante Retrofit/OkHttp o Ktor Client, eligiendo una sola alternativa y justificándola.
- Serialización mediante Kotlinx Serialization o Moshi, sin duplicar librerías.

### 3.2 Backend

- NestJS con TypeScript.
- Monolito modular organizado por dominios.
- API REST versionada bajo `/api/v1`.
- Contrato OpenAPI generado desde el backend.
- PostgreSQL como base principal.
- PostGIS habilitado desde la fundación, aunque todavía no se utilicen consultas geográficas.
- ORM aprobado: Prisma o TypeORM. Elegir uno después de auditar necesidades; no instalar ambos.
- Validación de entrada centralizada.
- Identificadores UUID.
- Fechas almacenadas en UTC.

### 3.3 Infraestructura local

- Docker Compose para servicios locales.
- PostgreSQL/PostGIS obligatorio.
- Almacenamiento S3 compatible local opcional mediante MinIO, solo si se utiliza en la fundación de archivos.
- Redis no se incorpora hasta que un caso de uso lo requiera.
- Variables de entorno fuera del código.
- Archivo `.env.example` sin secretos.

### 3.4 Web e infraestructura futura

- Reservar límites y documentación para Next.js, portal de organizaciones y consola administrativa.
- No implementar todavía las interfaces web.
- No crear microservicios.
- No conectar servicios cloud de pago en M00.

---

## 4. Estructura objetivo del repositorio

Cursor debe adaptar esta estructura al proyecto existente con el menor movimiento posible:

```text
Leover/
├── app/                         # módulo Android existente
├── core/                        # módulos Android compartidos, si ya existen o son necesarios
│   ├── common/
│   ├── designsystem/
│   ├── network/
│   ├── model/
│   └── testing/
├── feature/                     # funcionalidades Android desacopladas
├── backend/                     # API NestJS
│   ├── src/
│   │   ├── common/
│   │   ├── config/
│   │   ├── health/
│   │   └── modules/
│   ├── test/
│   └── prisma/ o migrations/
├── web/                         # reservado; no implementar en M00
├── docs/
│   ├── 00-maestro/
│   ├── 01-producto/
│   ├── 02-arquitectura/
│   ├── 03-modulos/
│   └── adr/
├── infra/
│   ├── docker/
│   └── scripts/
├── .github/workflows/
├── docker-compose.yml
├── .env.example
├── README.md
└── CONTRIBUTING.md
```

Si el proyecto Android usa módulos Gradle diferentes, mantenerlos. La estructura anterior expresa responsabilidades, no obliga a mover todo de inmediato.

---

## 5. Alcance obligatorio

### M00-A — Auditoría inicial

Generar `docs/02-arquitectura/M00-auditoria-inicial.md` con:

- Estructura actual.
- Stack detectado.
- Dependencias principales.
- Estado de compilación.
- Pruebas existentes.
- Duplicaciones o riesgos.
- Requisitos de M00 ya cumplidos.
- Cambios propuestos por prioridad.

No modificar arquitectura antes de completar esta auditoría.

### M00-B — Gobierno técnico

Crear o completar:

- `README.md` con ejecución local.
- `CONTRIBUTING.md` con flujo de trabajo.
- Convenciones de ramas y commits.
- Plantilla de pull request.
- Registro de decisiones en `docs/adr/`.
- `docs/02-arquitectura/arquitectura-inicial.md`.

ADR mínimos:

1. ADR-0001: monolito modular y API central.
2. ADR-0002: estructura del repositorio.
3. ADR-0003: ORM seleccionado.
4. ADR-0004: cliente HTTP Android seleccionado.
5. ADR-0005: estrategia de configuración por ambientes.

### M00-C — Ambientes y configuración

Definir al menos:

- `local` o `dev`.
- `staging`.
- `production`.

Requisitos:

- Ningún secreto en Git.
- `.env.example` documentado.
- URLs y flags configurables.
- Configuración Android por build type o product flavor, sin sobrecomplicar.
- Backend con validación temprana de variables requeridas.
- Aplicación debe fallar con mensaje claro cuando una configuración obligatoria sea inválida.

### M00-D — Backend base

Crear o consolidar `backend/` con:

- Proyecto NestJS.
- Prefijo `/api/v1`.
- Endpoint `GET /api/v1/health/live`.
- Endpoint `GET /api/v1/health/ready`.
- Endpoint `GET /api/v1/version`.
- Validación global de DTO.
- Filtro global de excepciones.
- Correlation ID por solicitud.
- Logs estructurados.
- OpenAPI disponible solo donde corresponda.
- CORS configurado por ambiente.
- Cierre ordenado de la aplicación.

Respuesta mínima de salud:

```json
{
  "status": "ok",
  "service": "leover-api",
  "version": "0.1.0",
  "timestamp": "2026-07-14T00:00:00.000Z"
}
```

### M00-E — Base de datos local

- PostgreSQL con PostGIS mediante Docker Compose.
- Migración inicial vacía o de infraestructura.
- Tabla técnica de migraciones administrada por el ORM.
- Convención `snake_case` en base de datos.
- UUID como identificador por defecto.
- Timestamps `created_at` y `updated_at` para entidades persistentes futuras.
- Script o comando documentado para iniciar, migrar, reiniciar y respaldar entorno local.
- No crear todavía tablas de usuarios o mascotas.

### M00-F — Contrato común de errores

Toda API debe devolver errores con un formato único:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Los datos enviados no son válidos.",
    "details": [],
    "correlationId": "uuid",
    "timestamp": "2026-07-14T00:00:00.000Z"
  }
}
```

Reglas:

- `code` es estable y utilizable por clientes.
- `message` es comprensible y no expone internals.
- `details` contiene errores de campo cuando corresponda.
- Nunca devolver stack traces en staging o producción.
- Android debe mapear errores de red, servidor, validación y desconocidos a modelos propios.

### M00-G — Fundación Android

Auditar y completar:

- Navegación centralizada.
- Tema claro y oscuro.
- Componentes base de carga, vacío, error y reintento.
- Abstracción de configuración.
- Abstracción de cliente API.
- Manejo común de errores.
- Dispatcher provider o equivalente testeable.
- Modelos de resultado consistentes, sin múltiples wrappers incompatibles.
- Preview de componentes principales.
- No conectar todavía pantallas a datos reales de negocio.

La aplicación debe poder cambiar entre una implementación simulada y una implementación remota sin modificar la UI.

### M00-H — Calidad automática

Android:

- Compilación de debug.
- Pruebas unitarias.
- Android Lint.
- Formato/lint adicional solo si se integra sin romper el proyecto.

Backend:

- TypeScript strict.
- ESLint.
- Prettier.
- Pruebas unitarias.
- Prueba e2e de health.
- Compilación de producción.

No aceptar una configuración de calidad que genere cientos de errores históricos sin plan gradual.

### M00-I — CI

Crear GitHub Actions que ejecuten, como mínimo:

- Validación Android.
- Validación backend.
- Pruebas.
- Compilación.
- Verificación de que no se suban archivos `.env` reales.

La CI no debe desplegar producción en M00.

### M00-J — Observabilidad mínima

- Logs estructurados en backend.
- Correlation ID.
- Abstracción de logging en Android.
- Evento técnico de inicio correcto de aplicación.
- No enviar datos personales en logs.
- Documentar puntos futuros para crash reporting, métricas y trazas.

### M00-K — Feature flags

Crear una interfaz simple y reemplazable para flags:

- Implementación local por configuración.
- Valores predeterminados seguros.
- No integrar todavía una plataforma remota.
- Los módulos incompletos deben permanecer desactivados o marcados como prototipo.

### M00-L — Seguridad de base

- Secretos fuera del repositorio.
- Dependencias sin vulnerabilidades críticas conocidas.
- HTTPS obligatorio fuera de desarrollo local.
- Network Security Config de Android sin permitir tráfico claro global.
- Cabeceras básicas de seguridad en backend.
- Límites razonables de tamaño de body.
- Rate limiting puede quedar preparado, pero no debe bloquear desarrollo local.
- No almacenar tokens, contraseñas ni datos sensibles en logs.

---

## 6. Fuera de alcance

M00 no debe implementar:

- Registro o inicio de sesión real.
- Entidades de usuario, organización o mascota.
- Firebase/Supabase solo “por si acaso”.
- Redes sociales.
- Adopciones.
- Alertas perdidas/encontradas.
- Mapas.
- Chat.
- Pagos.
- Notificaciones push reales.
- Carga real de archivos.
- Panel web.
- Microservicios.
- Kubernetes.
- Inteligencia artificial.

---

## 7. Criterios de aceptación

M00 se considera terminado únicamente cuando:

1. El proyecto Android existente sigue compilando y ejecutando.
2. Existe una auditoría escrita de la base actual.
3. El backend inicia localmente con un solo procedimiento documentado.
4. PostgreSQL/PostGIS inicia mediante Docker Compose.
5. Los endpoints de health y version responden correctamente.
6. OpenAPI refleja los endpoints disponibles.
7. El contrato común de errores está implementado y probado.
8. Android posee una capa de red reemplazable y manejo común de errores.
9. Existen configuraciones separadas para desarrollo, staging y producción.
10. No hay secretos reales versionados.
11. Las validaciones automáticas Android y backend pasan en local.
12. La CI ejecuta compilación y pruebas.
13. Los ADR mínimos están registrados.
14. README permite a otra persona levantar el entorno desde cero.
15. No se implementó funcionalidad de módulos futuros.

---

## 8. Backlog y orden obligatorio

No implementar todo en una sola modificación.

### Etapa 1 — Auditoría y plan

- M00-001 Auditar repositorio actual.
- M00-002 Ejecutar compilaciones y pruebas existentes.
- M00-003 Crear matriz cumplido/faltante.
- M00-004 Proponer plan y archivos afectados.

**Salida:** informe de auditoría. Sin refactor masivo.

### Etapa 2 — Documentación y gobierno

- M00-005 Crear estructura de documentos.
- M00-006 Completar README y CONTRIBUTING.
- M00-007 Crear ADR-0001 a ADR-0005.
- M00-008 Configurar archivos ignorados y ejemplos de entorno.

### Etapa 3 — Backend y datos locales

- M00-009 Crear backend NestJS.
- M00-010 Configurar validación, errores y logs.
- M00-011 Crear Docker Compose PostgreSQL/PostGIS.
- M00-012 Seleccionar y configurar ORM.
- M00-013 Crear health, ready y version.
- M00-014 Agregar pruebas backend.

### Etapa 4 — Fundación Android

- M00-015 Auditar arquitectura Android.
- M00-016 Crear configuración por ambientes.
- M00-017 Crear abstracción de red.
- M00-018 Crear modelos comunes de resultado/error.
- M00-019 Completar estados comunes de UI.
- M00-020 Agregar pruebas de fundación.

### Etapa 5 — Automatización y seguridad

- M00-021 Crear workflows de CI.
- M00-022 Integrar lint y pruebas.
- M00-023 Revisar secretos y seguridad base.
- M00-024 Documentar observabilidad y flags.

### Etapa 6 — Cierre

- M00-025 Ejecutar verificación completa.
- M00-026 Actualizar auditoría con estado final.
- M00-027 Documentar deuda técnica legítima.
- M00-028 Emitir reporte de criterios de aceptación.

---

## 9. Forma de reporte de Cursor

Al finalizar cada etapa, responder con:

1. Objetivo de la etapa.
2. Archivos creados.
3. Archivos modificados.
4. Comandos ejecutados.
5. Resultado de compilación y pruebas.
6. Decisiones tomadas.
7. Riesgos o deuda pendiente.
8. Criterios de aceptación cumplidos.
9. Próxima etapa propuesta.

No declarar “terminado” si no se ejecutaron los comandos de validación disponibles.

---

## 10. Primer prompt para ejecutar M00

Copiar este mensaje en Cursor después de guardar este archivo en el repositorio:

```text
Actuá como arquitecto y desarrollador senior de Leover.

Leé primero:
1. /docs/01-producto/D01-Modulos-y-Orden.md
2. /docs/03-modulos/M00-Fundacion-Tecnica.md

Estamos implementando exclusivamente M00 — Fundación Técnica.
El proyecto Android ya existe y contiene trabajo válido. No lo recrees y no realices todavía modificaciones masivas.

PRIMERA TAREA:
- Auditá todo el repositorio.
- Detectá stack, estructura, dependencias, configuración, estado de compilación y pruebas.
- Compará lo existente contra los requisitos de M00.
- Creá el informe /docs/02-arquitectura/M00-auditoria-inicial.md.
- Mostrame la matriz de requisitos cumplidos, parciales y faltantes.
- Proponé un plan por etapas y los archivos que modificarías.

En esta primera tarea no implementes todavía el backend ni refactorices la aplicación. Detenete después del informe y el plan.
```

---

## 11. Definition of Done

Una tarea de M00 está terminada cuando:

- Está implementada con el alcance acordado.
- Compila.
- Tiene pruebas cuando corresponde.
- No rompe funcionalidades existentes.
- No contiene secretos.
- Está documentada.
- Respeta convenciones.
- La CI la valida.
- No introduce módulos futuros.
- Cursor informa evidencia concreta de verificación.

