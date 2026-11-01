# Firma de versión Release (1.3.0)

La app usa `applicationId=com.cafeteria.gestion`, `versionName=1.3.0` y `versionCode=5`.

El workflow `.github/workflows/android.yml` compila:
- `cafeteria-debug-apk` para pruebas;
- `cafeteria-release-signed-apk-v1.3.0` para distribución, solo si la clave de firma está configurada.

## Secretos requeridos en GitHub

En el repositorio abre `Settings → Secrets and variables → Actions → Secrets` y crea estos cuatro **Repository secrets**:

| Nombre | Contenido |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Contenido Base64 completo del archivo JKS permanente |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del almacén JKS |
| `ANDROID_KEY_ALIAS` | Alias de la clave |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave privada |

El archivo JKS nunca se añade al repositorio, al APK ni al ZIP del código fuente. Conserva una copia de respaldo fuera de GitHub. Si se pierde la clave, las instalaciones existentes firmadas con ella no podrán recibir una actualización firmada con otra clave (salvo mecanismos de rotación admitidos por el canal de distribución).

## Keystore entregado para esta versión

Se entregan por separado el archivo `cafeteria-release.jks`, su representación `keystore-base64.txt`, la contraseña y el alias. No subas ninguno al repositorio ni lo compartas en chats públicos. Pega los contenidos en los secretos privados indicados arriba.

## Generar el APK

1. Asegúrate de que el repositorio tenga la variable `API_BASE_URL` configurada en `Settings → Secrets and variables → Actions → Variables`, con solo el origen HTTPS terminado en `/`.
2. Configura los cuatro secretos anteriores.
3. Abre `Actions → Android build and release → Run workflow`.
4. El workflow valida URL y keystore, ejecuta `testDebugUnitTest`, genera Debug y compila Release firmado.
5. Descarga el artefacto `cafeteria-release-signed-apk-v1.3.0`.

No instales un APK Release firmado encima de un Debug con firma de depuración diferente. Para la primera instalación Release, desinstala la versión Debug (eso borra los datos locales de esa app) o conserva el mismo canal/firma desde el principio. Una vez instalada la versión Release firmada con esta clave, todas las actualizaciones deben usar la misma clave y alias.
