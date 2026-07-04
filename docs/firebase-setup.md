# Configuración Firebase — Leover

Guía para habilitar Auth, Firestore y Storage en un entorno de desarrollo o staging.

## Prerrequisitos

- Cuenta Google / Firebase Console
- [Firebase CLI](https://firebase.google.com/docs/cli) instalada (`npm install -g firebase-tools`)
- Proyecto Android abierto en Android Studio con JDK 17

## 1. Crear proyecto Firebase

1. Entrá a [Firebase Console](https://console.firebase.google.com/).
2. **Agregar proyecto** → nombre sugerido: `leover-dev`.
3. Desactivá Google Analytics si no lo necesitás (opcional en dev).

## 2. Registrar la app Android

1. En el proyecto Firebase → **Agregar app** → Android.
2. Package name: `com.comunidapp.app` (debe coincidir con `applicationId`).
3. Descargá `google-services.json` y colocalo en:

   ```
   app/google-services.json
   ```

4. Sincronizá Gradle. La app detecta el archivo y activa `FIREBASE_ENABLED=true` automáticamente.

## 3. Habilitar servicios

En Firebase Console:

| Servicio | Acción |
|----------|--------|
| **Authentication** | Activar proveedor **Email/Password** |
| **Firestore** | Crear base de datos en modo producción (reglas se despliegan desde el repo) |
| **Storage** | Activar bucket por defecto |

## 4. Desplegar reglas de seguridad

Desde la raíz del repositorio:

```bash
firebase login
firebase use --add          # seleccionar el proyecto creado
firebase deploy --only firestore:rules,storage
```

Archivos incluidos:

- `firestore.rules` — lectura autenticada; escritura solo del dueño en `users`, `pets`, `posts`
- `storage.rules` — uploads limitados a rutas del usuario autenticado

## 5. Índices compuestos

Si Firestore pide un índice al ejecutar queries, seguí el enlace del logcat o creá manualmente:

| Colección | Campos | Uso |
|-----------|--------|-----|
| `pets` | `ownerId` ASC, `createdAt` DESC | Mascotas por dueño |
| `posts` | `createdAt` DESC | Feed global |

Referencia completa: [`docs/leover-firestore-model.md`](leover-firestore-model.md).

## 6. Verificación rápida

1. Ejecutá la app en emulador/dispositivo **con** `google-services.json`.
2. Registrate con email real → verificá el correo.
3. Editá perfil y subí avatar → revisá documento en `users/{uid}` y archivo en Storage.
4. Agregá una mascota → documento en `pets/{petId}`.
5. Publicá en el feed con imagen → documento en `posts/{postId}`.
6. Cerrá sesión y volvé a entrar → sesión persistente OK.

## 7. Modo mock (sin Firebase)

Si **no** existe `google-services.json`, la app usa datos en memoria:

- Usuario demo: `maria@email.com` / `123456`
- Los datos no persisten entre reinicios
- Ideal para UI y desarrollo offline

## 8. Seed de datos demo (opcional)

Para poblar Firestore manualmente:

1. Creá un usuario de prueba desde la app.
2. Copiá su `uid` desde Authentication.
3. En Firestore, creá documentos siguiendo el esquema de [`leover-firestore-model.md`](leover-firestore-model.md).

No hay script automático de seed en Fase 0; se planifica para Fase 1 si hace falta entornos compartidos.

## Troubleshooting

| Síntoma | Causa probable | Solución |
|---------|----------------|----------|
| `PERMISSION_DENIED` en writes | Reglas no desplegadas | `firebase deploy --only firestore:rules` |
| Upload falla en Storage | Reglas Storage o auth | Verificar login activo y `storage.rules` |
| Feed vacío tras publicar | Query sin índice | Crear índice desde enlace en logcat |
| App usa mock con Firebase | Falta `google-services.json` | Copiar archivo a `app/` y rebuild |
