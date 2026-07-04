# Leover

Plataforma móvil para la comunidad animal: adopciones, refugios, avisos de perdidos/encontrados y publicaciones. Conecta personas, mascotas, refugios y organizaciones en un único ecosistema.

## Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM + Navigation Compose
- Firebase Auth y Firestore (opcional con `google-services.json`)
- Datos mock locales para desarrollo sin Firebase

## Requisitos

- Android Studio (Ladybug o superior)
- JDK 17
- minSdk 26, targetSdk 36

## Cómo ejecutar

1. Clonar el repositorio.
2. Abrir la carpeta del proyecto en Android Studio.
3. Sincronizar Gradle.
4. Ejecutar en emulador o dispositivo (`app` → Run).

### Usuario demo (modo mock)

- Email: `maria@email.com`
- Contraseña: `123456`

Con Firebase habilitado, registrate con un email real y verificá la cuenta desde el enlace que llega al correo.

La app recuerda la sesión: si ya iniciaste sesión, al reabrir entrás directo al inicio. Podés cerrar sesión desde **Mi perfil**.

## Estructura

```
app/src/main/java/com/comunidapp/app/
├── navigation/     # Rutas y grafo de navegación
├── ui/             # Pantallas, componentes y tema
├── data/           # Modelos, mock y repositorios
└── viewmodel/      # ViewModels por pantalla
```

## Licencia

Proyecto privado — uso interno del equipo.
