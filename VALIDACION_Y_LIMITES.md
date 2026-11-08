# YuriCoofe 1.6.0 — informe de implementación y validación

Fecha de trabajo: 6 de noviembre de 2026.

## Proyectos entregados

- `YuriCoofe-main`: proyecto Android Kotlin/Jetpack Compose completo, con todos sus fuentes, Gradle, configuración, recursos, pruebas y workflows.
- `YuriBackend-main`: backend Node.js/Express/PostgreSQL completo, con fuentes, scripts, migraciones, tests, configuración de ejemplo y workflows.

No se incluyen contraseñas, archivos `.env`, tokens, claves de Firebase, keystores de firma ni datos reales de clientes.

## Cambios principales

### Android

- Room sube a versión 7 con migraciones incrementales para cola administrativa, transferencias, businessId, desactivación y revisión de catálogo.
- La venta conserva su cola y su identidad UUID original.
- Se añade outbox administrativa para ajustes y conteos físicos, con revisión base de inventario/catálogo y versión de precio/formato.
- Los ajustes y conteos se guardan en Room dentro de transacciones antes de sincronizar.
- Un conteo físico sin cambio de saldo puede registrarse como evento auditable.
- La desactivación offline se guarda como evento y se sincroniza como baja lógica.
- La renominación de categoría se implementa como una secuencia de actualizaciones de los productos de esa categoría; el proyecto no introduce una tabla de categorías independiente.
- Los paquetes de transferencia se cifran con AES-GCM; la clave se deriva mediante PBKDF2-HMAC-SHA256 y se verifica el contenido con SHA-256. Las copias locales de paquetes se protegen con Android Keystore.
- Se añade modo `LOCAL_STATION`, registro remoto cuando hay internet, consulta/revocación y heartbeat limitado.
- La estación recibe, conserva y consolida los paquetes; no se le entrega un token OWNER/WORKER ni se permite que ejecute administración/ventas.
- La app OWNER puede reenviar ventas de trabajadoras conservando la identidad y el payload original.
- Los totales CUP locales usan sumas exactas con detección de overflow.

### Backend

- Migraciones aditivas `002_offline_inventory.sql`, `003_local_stations_and_catalog.sql` y `004_idempotent_product_creates.sql`.
- Operaciones de inventario idempotentes y registro de conflictos `NEEDS_REVIEW`.
- Revisiones monotónicas de catálogo por cafetería/producto y comprobación de revisión base en actualizaciones.
- Altas y ediciones de productos con `clientOperationId` idempotente.
- Desactivación de producto idempotente.
- Estaciones con registro, listado, revocación y heartbeat autenticado con secreto específico.
- Ruta OWNER `/api/v1/sales/forwarded` para reenvío de ventas originales de trabajadoras.
- Conversión de PostgreSQL BIGINT valida el rango seguro de JavaScript; las estadísticas agregan con `numeric` y rechazan resultados que no puedan representarse exactamente.

## Validaciones ejecutadas en este entorno

| Comprobación | Resultado |
|---|---|
| `npm run check` backend | 27 archivos JS con sintaxis válida; 25/25 pruebas pasan |
| `StockCalculator.kt` aislado | Compila con `kotlinc` |
| Comprobaciones directas de aritmética Kotlin | 7/7 en el harness: ajuste y venta de subunidades, porciones sin stock ficticio, negativos, overflow, conteo físico sin diferencia y rechazo de ajuste vacío |
| Análisis sintáctico de todos los `.kt` con `kotlinc` | 0 errores de parser detectados; kotlinc informa miles de referencias no resueltas porque no tiene el classpath AndroidX/Compose/Room/Retrofit. Esto NO equivale a compilación Android |
| Gradle Android `testDebugUnitTest` + `assembleDebug` | No pudo arrancar: no se resolvió `services.gradle.org` para descargar Gradle 8.9 |
| Migraciones PostgreSQL | Revisadas estáticamente; no ejecutadas en una base de staging disponible |
| Transferencia entre dispositivos físicos | No ejecutada; requiere al menos dos Android reales |

## Límites funcionales que deben quedar explícitos

1. La transferencia offline implementada es por archivo cifrado, transportado con el medio que Android permita (Bluetooth/Nearby Share/USB u otro medio físico). No se añadió descubrimiento automático Wi-Fi Direct, hotspot administrado, Bluetooth integrado dentro de la app ni QR multiparte.
2. La estación local almacena y consolida; no reenvía por sí misma operaciones al backend. OWNER importa el paquete y su dispositivo sincroniza el outbox.
3. No hay todavía una pantalla completa para resolver/rebasar cada conflicto `NEEDS_REVIEW`; se conserva el evento y se impide la aplicación automática.
4. La estación y el código de transferencia deben probarse entre dispositivos, incluyendo revocación, clave equivocada, archivo truncado, doble importación, ID reutilizado, interrupción de transferencia y vuelta del recibo al origen.
5. La migración debe probarse sobre una copia de staging tomada de la estructura real de producción, con backup previo y restauración comprobada.

## Recomendación de despliegue

**No aplicar estas migraciones directamente sobre producción ni instalar un APK Release como si estuviera certificado.** Primero abrir los dos proyectos, ejecutar el workflow Android y `npm run check`; después aplicar las migraciones en staging, probar ventas/stock con reintentos y realizar transferencias físicas entre dos teléfonos. Solo tras resolver cualquier fallo de esas pruebas debe programarse la actualización de producción.
