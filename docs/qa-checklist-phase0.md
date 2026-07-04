# Checklist QA manual — Fase 0 Leover

Marcar cada ítem antes de cerrar la fase. Probar en **modo mock** y, si es posible, en **modo Firebase**.

## Autenticación y sesión

- [ ] Registro con email y contraseña (Firebase)
- [ ] Verificación de email recibida y enlace funcional
- [ ] Login con credenciales válidas
- [ ] Login con credenciales inválidas muestra error
- [ ] Recuperar contraseña envía email (Firebase)
- [ ] Sesión persiste al cerrar y reabrir la app
- [ ] Logout desde Mi perfil vuelve a login
- [ ] Usuario demo mock (`maria@email.com` / `123456`) funciona sin Firebase

## Perfil

- [ ] Mi perfil muestra datos del usuario autenticado
- [ ] Editar perfil: nombre, bio, ubicación, teléfono, tipo de cuenta
- [ ] Subir/cambiar foto de perfil
- [ ] Cambios persisten tras reiniciar (Firebase) o se reflejan en sesión (mock)
- [ ] Perfil público de otro usuario accesible desde feed (tap en autor)

## Mascotas

- [ ] Lista "Mis mascotas" muestra mascotas del usuario
- [ ] FAB (+) abre formulario agregar mascota
- [ ] Crear mascota con foto, salud y descripción
- [ ] Detalle de mascota muestra información completa
- [ ] Editar mascota desde detalle
- [ ] Eliminar mascota (detalle o formulario editar)
- [ ] Solo el dueño ve botones editar/eliminar

## Publicaciones

- [ ] Feed de inicio muestra publicaciones ordenadas
- [ ] Fechas relativas en tarjetas (`Hace X min`, etc.)
- [ ] Publicación general con título, contenido, ubicación opcional
- [ ] Publicación general con imagen opcional
- [ ] Publicar adopción crea entrada en feed
- [ ] Publicar perdido/encontrado crea entrada en feed
- [ ] Contenido publicado visible tras reiniciar app (Firebase)

## Navegación

- [ ] Bottom bar: Inicio, Adopciones, Publicar, Refugios, Perfil
- [ ] Back desde pantallas secundarias funciona
- [ ] Rutas deep: detalle adopción, refugio, mascota, perfil público

## Regresión mock vs Firebase

- [ ] App compila y arranca sin `google-services.json`
- [ ] App compila y arranca con `google-services.json`
- [ ] `DataProvider` conmuta repositorios según `BuildConfig.SUPABASE_ENABLED`

## Documentación

- [ ] README actualizado
- [ ] Reglas Firestore/Storage desplegadas en proyecto dev
- [ ] Modelo Firestore documentado

---

**Resultado:** ___ / ___ ítems OK  
**Tester:** _______________  
**Fecha:** _______________  
**Build:** _______________
