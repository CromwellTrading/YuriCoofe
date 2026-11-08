# YuriCoofe 1.6.0 — operaciones offline y estación local

## Incluido en esta versión

- Cola persistente `admin_operation_queue` en Room para ajustes de stock y conteos físicos, con UUID, dispositivo, secuencia, hora original, revisión base de inventario, revisión de catálogo y versión de precio/formato.
- Ajustes y conteos físicos guardados localmente en transacción antes de intentar sincronizar. Un conteo que confirma el mismo saldo también queda como evento auditable.
- Desactivación de productos offline como operación de catálogo, sin borrado del historial.
- Cambios de categoría offline mediante eventos de actualización de cada producto de la categoría; no se crea una tabla independiente de categorías.
- Transferencia portátil de operaciones en paquetes cifrados AES-GCM, código aleatorio compartido fuera del archivo, hash SHA-256, límite de tamaño, validación de IDs y almacenamiento de los paquetes recibidos protegido por Android Keystore.
- Modo de estación local con identidad separada del usuario, pantalla limitada a recepción/almacenamiento/consolidación y sin envío autónomo de operaciones al backend.
- Registro, consulta, heartbeat y revocación remota de estaciones desde la cuenta OWNER.
- Reenvío de ventas de trabajadoras desde OWNER mediante una ruta con idempotencia y validación del backend.
- Revisiones monotónicas del catálogo y conflictos de inventario conservados como `NEEDS_REVIEW`.
- Agregaciones CUP de la interfaz con suma exacta para evitar overflow silencioso.

## Límites conocidos

- La transferencia usa archivos cifrados. El usuario los transporta con el mecanismo disponible en Android (Bluetooth/Nearby Share/USB/otro medio físico). No hay todavía Wi-Fi Direct, hotspot administrado, Bluetooth integrado ni QR multiparte.
- La estación almacena y consolida paquetes; no transmite operaciones al servidor por sí sola. Un OWNER debe importar el conjunto para sincronizarlo.
- La interfaz presenta errores/pendientes generales, pero todavía no incluye un flujo completo de resolución manual de cada conflicto de inventario.
- La migración SQL requiere prueba en una base PostgreSQL de staging antes de producción.
- No se certifica el APK de esta versión hasta que GitHub Actions ejecute pruebas y compile Debug/Release en un runner Android.

## Compatibilidad

- Android: `versionName 1.6.0`, `versionCode 8`, Room `7` (migraciones 1→2→3→4→5→6→7).
- Backend: paquete `1.4.0`, Node.js 22–26, PostgreSQL 14+.
- Backend migrations: `002_offline_inventory.sql`, `003_local_stations_and_catalog.sql`, `004_idempotent_product_creates.sql`.
