# RC1.1 — Plan de prueba manual integral

**APK:** `artifacts\rc1.1\LeoVer-RC1.1-local-debug.apk`  
**versionName:** `1.1-local` · **versionCode:** `2`  
**Estado global:** prueba física **PENDIENTE**

## Precondiciones

- [ ] APK RC1.1 instalada (o actualización sobre RC1)
- [ ] Usuario de prueba registrado
- [ ] Conexión estable (o mock mode)
- [ ] Sin expectativa de pagos reales

---

## A. Ícono launcher (prioridad)

| ID | Recorrido | Pasos resumidos | Estado |
|----|-----------|-----------------|--------|
| IC-01 | Centrado | Instalar APK → observar ícono en launcher | PENDIENTE |
| IC-02 | Sin recorte | Verificar isotipo perro/gato completo en máscara circular | PENDIENTE |
| IC-03 | Sin borde blanco | Comprobar ausencia de halo cuadrado blanco | PENDIENTE |
| IC-04 | Contraste | Ícono legible sobre fondo claro y oscuro del launcher | PENDIENTE |
| IC-05 | Icono circular | Launcher con forma redonda → isotipo centrado | PENDIENTE |
| IC-06 | Themed icon | Android 13+ → icono temático monocromático correcto | PENDIENTE |
| IC-07 | Actualización RC1 | Instalar RC1.1 sobre RC1 sin desinstalar | PENDIENTE |
| IC-08 | Nombre visible | Label **LeoVer Local** bajo el ícono | PENDIENTE |

---

## B. Onboarding RC1.1

Ver detalle en [`RC1.1-plan-prueba-manual-onboarding.md`](RC1.1-plan-prueba-manual-onboarding.md).

| ID | Recorrido | Estado |
|----|-----------|--------|
| OB-01 | Primera apertura | PENDIENTE |
| OB-02 | Comenzar | PENDIENTE |
| OB-03 | Omitir | PENDIENTE |
| OB-04 | Explorar primero | PENDIENTE |
| OB-05 | Cerrar y continuar | PENDIENTE |
| OB-06 | Registrar mascota | PENDIENTE |
| OB-07 | Pérdida | PENDIENTE |
| OB-08 | Hallazgo | PENDIENTE |
| OB-09 | Adopciones | PENDIENTE |
| OB-10 | Tránsito | PENDIENTE |
| OB-11 | Organización | PENDIENTE |
| OB-12 | Voluntariado | PENDIENTE |
| OB-13 | Privacidad | PENDIENTE |
| OB-14 | Permisos no anticipados | PENDIENTE |
| OB-15 | Ayuda contextual | PENDIENTE |
| OB-16 | Reiniciar tutorial | PENDIENTE |
| OB-17 | Texto ampliado / TalkBack | PENDIENTE |

---

## C. Recorridos generales RC1 (regresión)

| ID | Recorrido | Estado |
|----|-----------|--------|
| RC1-01 | Registro / login | PENDIENTE |
| RC1-02 | Crear mascota | PENDIENTE |
| RC1-03 | Editar mascota | PENDIENTE |
| RC1-04 | Comunidad | PENDIENTE |
| RC1-05 | Adopción | PENDIENTE |
| RC1-06 | Mascota perdida | PENDIENTE |
| RC1-07 | Refugio | PENDIENTE |
| RC1-08 | Evento | PENDIENTE |
| RC1-09 | Mensajería | PENDIENTE |
| RC1-10 | Prestador | PENDIENTE |
| RC1-11 | Reserva | PENDIENTE |
| RC1-12 | Marketplace | PENDIENTE |
| RC1-13 | Pedido sin pago | PENDIENTE |
| RC1-14 | IA con stub | PENDIENTE |
| RC1-15 | Integraciones sandbox | PENDIENTE |
| RC1-16 | Administración básica | PENDIENTE |
| RC1-17 | Cerrar sesión | PENDIENTE |

---

## Criterios de aceptación

- Ícono LeoVer reconocible, sin recortes ni halos
- Onboarding opcional funcional
- Sin crashes en recorridos RC1
- Sin PII expuesta · sin pagos reales
- M24 pospuesto · M28 inexistente

## Registro de incidencias

Documentar en [`RC1-backlog-hallazgos.md`](RC1-backlog-hallazgos.md).
