# Cafetería — aplicación Android

Aplicación Android nativa (Kotlin + Jetpack Compose) para gestionar el inventario y las ventas de una cafetería. Versión de producto `1.3.0` (`versionCode 5`). Al abrir, los clientes inician sesión o crean su cuenta; el acceso del personal se mantiene como opción secundaria y los roles reales los determina el servidor.

## Qué incluye la aplicación

- Tres roles: `OWNER` (dueña), `WORKER` (trabajadora) y `CUSTOMER` (cliente). El cliente inicia sesión con su nombre y contraseña; la app separa visualmente el acceso del personal y nunca permite elegir un rol manualmente.
- Catálogo conectado a API: fotografía, descripción, categorías, precios CUP por efectivo y precios opcionales por transferencia, estado público y existencias.
- Inventario por unidades y/o subunidades; conversión automática cuando se conoce la equivalencia entre ambas.
- Registro local de ventas con atribución de trabajadora, hora original con offset, método de pago, precios aplicados y un identificador UUID.
- Colas locales duraderas con Room: guarda ventas y cambios de catálogo (alta/edición de productos e imágenes) antes de contactar al servidor. Las ventas y su deducción local se guardan en una transacción; WorkManager transmite ambas colas al recuperar conexión, ordenadas por fecha, con el mismo ID, hora original y contenido para reintentos idempotentes.
- Historial de ventas, estado de sincronización, avisos programados, administración de trabajadoras, ajustes de inventario y pantallas de estadísticas.
- Almacenamiento cifrado del token de acceso con Android Keystore. Las cuentas de cliente creadas offline usan un verificador PBKDF2 con sal; la contraseña pendiente de primer registro se cifra con Android Keystore y se elimina de la cola al confirmar el servidor.
- Identidad visual cálida Café (espresso/crema) predeterminada, modo claro/oscuro y seis paletas guardadas localmente.
- Ventas por porciones para café/chocolate preparado, incluso sin inventario de recipientes, registrando cada vaso y su importe sin inventar conversiones. Para productos con recipientes y rendimiento desconocido se conserva el número de recipientes y se exige revisión manual al vaciarlo.
- Caché local distingue el indicador público de disponibilidad del inventario exacto: una consulta pública no sobrescribe las existencias privadas descargadas previamente.
- Build Release preparado para firma permanente mediante una clave de firma guardada como secretos de GitHub Actions.
- Pruebas unitarias del cálculo de existencias.

No se han incorporado productos, usuarios, ventas ni precios de demostración. Si aún no hay backend o no hay catálogo cacheado, la app muestra un estado vacío en vez de inventar datos.

## Requisitos

- Android Studio reciente con soporte para Android Gradle Plugin 8.7.3.
- JDK 17.
- Android SDK Platform 35.
- Para probar catálogo, autenticación, sincronización y avisos: una URL HTTPS de backend que implemente el contrato documentado.

## Configuración de backend

La URL base debe ser HTTPS y terminar en `/`. Puedes definirla como propiedad de Gradle en el archivo global `~/.gradle/gradle.properties` (en Windows: `%USERPROFILE%\.gradle\gradle.properties`) o pasarla en la compilación con `-PAPI_BASE_URL="https://servidor-real/"`:

```properties
API_BASE_URL=https://tu-servidor.example/
```

La línea anterior solo ilustra el formato; no es una URL funcional. No configures una URL inventada en producción. La URL debe corresponder al backend real cuando esté implementado.

Opcionalmente, para el registro de notificaciones push, define las propiedades de Firebase que corresponden a tu propia aplicación:

```properties
FIREBASE_API_KEY=...
FIREBASE_APP_ID=...
FIREBASE_PROJECT_ID=...
FIREBASE_SENDER_ID=...
```

No compartas archivos `local.properties`, secretos de CI ni credenciales reales. La app no lleva credenciales de backend ni claves de administración embebidas en el código.

## Abrir y compilar

1. Descomprime este proyecto en una computadora con JDK 17 y Android SDK Platform 35.
2. En Windows ejecuta `gradlew.bat assembleDebug`; en macOS/Linux ejecuta `./gradlew assembleDebug`. El lanzador incluido descarga Gradle 8.9 si todavía no está en caché y compila el APK.
3. Para usar Android Studio, ejecuta primero el paso anterior y abre después la carpeta `CafeteriaApp`. Como este paquete usa un lanzador ligero propio y no incluye el binario estándar `gradle-wrapper.jar`, si Android Studio no puede sincronizarlo automáticamente selecciona la instalación local de Gradle 8.9 en Settings → Build, Execution, Deployment → Build Tools → Gradle. La instalación queda en `%USERPROFILE%\.gradle\cafeteria-gradle-distributions\gradle-8.9` en Windows o `~/.gradle/cafeteria-gradle-distributions/gradle-8.9` en macOS/Linux.
4. Configura `API_BASE_URL` en las propiedades globales de Gradle o pásala como `-PAPI_BASE_URL=...` solo cuando el backend exista.
5. Ejecuta las pruebas de `app/src/test` y compila `app`.

También se incluye un flujo de GitHub Actions para compilar un APK de depuración en un runner Linux y adjuntarlo como artefacto de la ejecución. La compilación Android no pudo ejecutarse en este entorno porque el wrapper necesitaba descargar Gradle y el host de distribución no estaba accesible. El Workflow de GitHub Actions ejecuta `testDebugUnitTest`, construye Debug y construye Release firmado cuando están configurados los secretos indicados en `docs/RELEASE_SIGNING.md`. El Workflow verifica además la firma con `apksigner`.


## Configurar la URL en GitHub Actions (obligatorio para el APK)

La dirección que usa Android se inyecta **durante la compilación**. No basta con configurar `PUBLIC_BASE_URL` en Render: esa variable sirve al backend y no llega al APK.

En GitHub abre `Settings → Secrets and variables → Actions → Variables → New repository variable` y crea exactamente:

- Name: `API_BASE_URL`
- Value: `https://TU-SERVICIO.onrender.com/` (solo el origen, con `/` final; no añadas `/api/v1`).

Debe ser una **Variable**, no un Secret, porque el workflow lee `${{ vars.API_BASE_URL }}`. Después de guardarla, inicia manualmente `Actions → Android build and release → Run workflow`; cambiar una variable no inicia una compilación automáticamente.

La compilación comprueba ahora la variable antes de construir el APK y muestra en los logs qué host se inyectó. Si falta o el formato es incorrecto, el workflow falla expresamente en lugar de generar un APK que muestre “Servicio pendiente de configuración”. Descarga el artefacto `cafeteria-debug-apk` de esa ejecución nueva e instala ese APK.

Si el aviso sigue apareciendo, el teléfono está ejecutando una compilación anterior o el APK se generó desde otro repositorio/flujo. Comprueba que el último run ejecutó el paso `Validate backend URL` y que sus logs muestran `Backend URL injected into APK: https://.../`.

## Comportamiento offline y límites deliberados

- Para vender sin conexión la trabajadora debe haber iniciado sesión antes y haber descargado el catálogo privado con existencias exactas. No se permite vender usando solo la disponibilidad pública aproximada.
- Cada venta conserva `clientSaleId`, `soldAt`, zona/offset del teléfono, identidad de la trabajadora, método de pago, cantidades, precio aplicado y versión de precio como se registraron al confirmar.
- La app no cambia el `soldAt` cuando reintenta sincronizar.
- El servidor debe tratar `clientSaleId`/`Idempotency-Key` como idempotente y aceptar la hora reportada como hora del evento, guardando por separado la hora de recepción.
- Las ventas pendientes se sincronizan usando la cuenta original que las creó. Si se cierra sesión y otra persona inicia sesión en el mismo dispositivo, la cola de la primera trabajadora no se atribuye a la segunda; la primera debe volver a iniciar sesión para enviar sus ventas.
- Si el servidor rechaza una venta por inventario insuficiente o por un cambio de precio, el registro no se borra: pasa a `NEEDS_REVIEW` y necesita revisión antes de reintentarlo.
- Para productos `SUBUNIT` sin equivalencia y stock de recipientes igual a cero (café/chocolate por vaso), cada porción se registra económicamente sin inventar existencias. Si el producto tiene recipientes registrados (helado por bolas), se conserva ese número porque no se conoce el rendimiento; cuando se agote el recipiente, la dueña debe ajustar el inventario manualmente. En `BOTH` sin equivalencia también se exige que quede un recipiente disponible.
- La sincronización push y las publicaciones programadas requieren la configuración del servicio de backend/mensajería correspondiente. Un permiso del teléfono no garantiza entrega si el usuario bloquea notificaciones o no hay conexión.

## Documentación para el backend

Consulta [`docs/API_CONTRACT_V1.md`](docs/API_CONTRACT_V1.md). El backend Node.js/PostgreSQL se entrega como proyecto separado y debe desplegarse y configurarse con la misma versión del contrato antes de usar ventas reales.


## Firma y versión Release

La versión está configurada como `1.3.0` (`versionCode 5`). El Workflow genera Debug y un APK Release firmado. La clave privada no está dentro del código ni del ZIP del proyecto. Configura los secretos `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD` siguiendo [`docs/RELEASE_SIGNING.md`](docs/RELEASE_SIGNING.md). Sin esos secretos, el workflow detiene Release en lugar de entregar un APK sin firma como si fuera distribuible.


## Versión 1.2.1

Se corrigió la obtención del token FCM usando la API pública `FirebaseMessaging.getInstance()` después de inicializar la app Firebase predeterminada. El versionCode es 4.


## Versión 1.3.0

- Nueva bienvenida de YuriCoofe en estilo espresso y crema, con acceso de cliente como flujo principal.
- Alta de cliente con nombre y contraseña, inicio de sesión específico para clientes y acceso del personal separado. El servidor asigna los roles; la app no presenta un selector de permisos.
- Alta de cliente offline: el perfil y su verificador cifrado/local se conservan en el teléfono. La contraseña pendiente se protege con Android Keystore y WorkManager la envía al endpoint público al recuperar conexión. Si se pierde la respuesta del servidor, el reintento inicia sesión con la misma cuenta en vez de duplicarla.
- El nombre visible del cliente puede repetirse; el servidor guarda un identificador interno único que no se muestra al usuario.
- Versionado `versionCode 5`, `versionName 1.3.0`.
