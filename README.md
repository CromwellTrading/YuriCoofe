# Cafetería — aplicación Android

Aplicación Android nativa (Kotlin + Jetpack Compose) para gestionar el inventario y las ventas de una cafetería. La app se está construyendo antes del backend; por eso las pantallas y el flujo local ya existen, pero las operaciones que necesitan el servidor mostrarán un error de conexión hasta que se implemente el contrato de `docs/API_CONTRACT_V1.md`.

## Qué incluye la aplicación

- Tres roles: `OWNER` (dueña), `WORKER` (trabajadora) y `CUSTOMER` (cliente).
- Catálogo conectado a API: fotografía, descripción, categorías, precios CUP por efectivo y precios opcionales por transferencia, estado público y existencias.
- Inventario por unidades y/o subunidades; conversión automática cuando se conoce la equivalencia entre ambas.
- Registro local de ventas con atribución de trabajadora, hora original con offset, método de pago, precios aplicados y un identificador UUID.
- Cola local duradera con Room: guarda la venta y descuenta el inventario local dentro de una sola transacción SQLite. WorkManager la transmite cuando hay internet; se repite el mismo ID, hora y cuerpo para permitir reintentos idempotentes.
- Historial de ventas, estado de sincronización, avisos programados, administración de trabajadoras, ajustes de inventario y pantallas de estadísticas.
- Almacenamiento cifrado del token de acceso con Android Keystore.
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

También se incluye un flujo de GitHub Actions para compilar un APK de depuración en un runner Linux y adjuntarlo como artefacto de la ejecución. La compilación local no pudo validarse en el entorno donde se generó este ZIP porque no estaba instalado Android SDK/Gradle y no había acceso al host de distribución de Gradle.

## Comportamiento offline y límites deliberados

- Para vender sin conexión la trabajadora debe haber iniciado sesión antes y tener el producto en el catálogo cacheado. No se permite inventar un producto para registrar una venta.
- Cada venta conserva `clientSaleId`, `soldAt`, zona/offset del teléfono, identidad de la trabajadora, método de pago, cantidades, precio aplicado y versión de precio como se registraron al confirmar.
- La app no cambia el `soldAt` cuando reintenta sincronizar.
- El servidor debe tratar `clientSaleId`/`Idempotency-Key` como idempotente y aceptar la hora reportada como hora del evento, guardando por separado la hora de recepción.
- Las ventas pendientes se sincronizan usando la cuenta original que las creó. Si se cierra sesión y otra persona inicia sesión en el mismo dispositivo, la cola de la primera trabajadora no se atribuye a la segunda; la primera debe volver a iniciar sesión para enviar sus ventas.
- Si el servidor rechaza una venta por inventario insuficiente o por un cambio de precio, el registro no se borra: pasa a `NEEDS_REVIEW` y necesita revisión antes de reintentarlo.
- En productos con subunidades y equivalencia desconocida (por ejemplo, bolas de helado sin rendimiento conocido), el sistema registra cantidades e importe pero no inventa cuántas porciones consume un recipiente. Debe haber al menos un recipiente disponible para registrar porciones.
- La sincronización push y las publicaciones programadas requieren la configuración del servicio de backend/mensajería correspondiente. Un permiso del teléfono no garantiza entrega si el usuario bloquea notificaciones o no hay conexión.

## Documentación para el backend

Consulta [`docs/API_CONTRACT_V1.md`](docs/API_CONTRACT_V1.md). El backend todavía debe implementarse por separado; los endpoints de ese documento son el contrato al que apunta esta app, no una afirmación de que ya estén disponibles.
