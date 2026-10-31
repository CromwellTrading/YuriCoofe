# YuriCoofe 1.2.1

## Corrección
- Se reemplaza `FirebaseMessaging.getInstance(firebase)` por la API pública `FirebaseMessaging.getInstance()` para utilizar la app Firebase predeterminada inicializada por `CafeteriaApplication.configuredFirebaseApp()`.
- Se incrementa `versionCode` a 4 y `versionName` a 1.2.1 para la siguiente compilación instalable.
- El artefacto Release de GitHub Actions ahora identifica la versión 1.2.1.

La configuración Firebase continúa siendo opcional: si no están presentes los cuatro valores Firebase, no se solicita el token push y la app puede utilizarse sin notificaciones push.
