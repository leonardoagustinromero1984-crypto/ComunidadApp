# RC1 — Plan de prueba manual (pre-APK)

Ejecutar cuando se genere APK debug autorizada. Entorno recomendado: **mock mode** o **staging** con credenciales de prueba.

## Precondiciones

- [ ] APK debug instalada (no generada en RC1)
- [ ] Usuario de prueba registrado
- [ ] Conexión estable (o mock mode sin red)
- [ ] Sin expectativa de pagos reales

## Recorridos

### 1. Registro / login
1. Abrir app → pantalla login.
2. Registrar usuario nuevo o iniciar sesión.
3. Completar consentimiento legal si aplica.
4. Verificar llegada a `home`.

### 2. Crear mascota
1. Perfil → Mis mascotas → Agregar.
2. Completar datos mínimos.
3. Guardar y verificar en listado.

### 3. Editar mascota
1. Abrir mascota creada.
2. Modificar nombre o especie.
3. Guardar; confirmar persistencia.

### 4. Comunidad
1. Tab Comunidad.
2. Verificar accesos a M19–M27.
3. Navegar y volver sin crash.

### 5. Adopción
1. Tab Sumate → adopción.
2. Ver detalle publicación.
3. Enviar solicitud.
4. Verificar llegada a "Mis solicitudes" (fix NAV-001).

### 6. Mascota perdida
1. Sumate o Publicar → perdida/encontrada.
2. Crear aviso o ver mapa M13 si disponible.

### 7. Refugio
1. Sumate → Refugios M16.
2. Ver detalle refugio público.
3. Acceder a donaciones/eventos vinculados.

### 8. Evento
1. Sumate → Eventos M18.
2. Ver listado y detalle.
3. Inscribirse si aplica.

### 9. Mensajería
1. Comunidad → Mensajes M20.
2. Abrir conversación.
3. Enviar mensaje de prueba.

### 10. Prestador
1. Comunidad → Prestadores M22.
2. Ver catálogo y detalle servicio.

### 11. Reserva
1. Desde M22 o M23 → solicitar reserva.
2. Confirmar flujo REQUESTED → CONFIRMED (usuario prestador).

### 12. Marketplace
1. Comunidad → Marketplace M25.
2. Ver tienda y productos.
3. Agregar al carrito.

### 13. Pedido sin pago
1. Crear pedido desde carrito.
2. Verificar estados sin "pagado".
3. Flujo merchant: aceptar → preparar → enviar → entregar.

### 14. IA con stub
1. Comunidad → IA M26.
2. Probar asistencia con prompt corto válido.
3. Verificar advertencia IA asistida.

### 15. Integraciones sandbox
1. Comunidad → Integraciones M27.
2. Ver contratos publicados.
3. Registrar webhook sandbox (URL HTTPS).
4. Verificar entrega simulada.

### 16. Administración básica
1. Perfil → opciones admin (si usuario staff).
2. Ver cola moderación o soporte.
3. Salir sin alterar producción.

### 17. Cerrar sesión
1. Perfil → cerrar sesión.
2. Verificar retorno a login.
3. Re-login opcional.

## Criterios de aceptación manual

- Sin crashes en recorridos 1–17.
- Sin PII visible en pantallas públicas.
- Sin referencias a pagos completados.
- Back stack coherente post-adopción (NAV-001).

## Registro de incidencias

Documentar en backlog post-RC1 con ID, módulo, pasos, screenshot si aplica.
