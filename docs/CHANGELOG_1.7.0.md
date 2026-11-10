# YuriCoofe 1.7.0

## Traspaso por QR (sin internet)
- Botón QR en la barra superior (Yuri y trabajadoras) y tarjeta en Ajustes.
- **Enviar**: el teléfono muestra los códigos QR en bucle + un PIN de 6 dígitos. **Recibir**: el otro teléfono escanea (cualquier orden, repetidos ignorados), pide el PIN, descifra (AES-GCM), verifica integridad e importa.
- Contenido: ventas sin confirmar que conoce el teléfono; si envía Yuri, también su inventario activo.
- Al recibir ventas de otra persona se descuenta su stock en este teléfono (relevo de turno). Al recibir el inventario de Yuri, la trabajadora lo adopta y vuelve a descontar sus ventas locales que Yuri no conocía.
- 120 ventas ≈ 16 códigos. Límite 600 códigos; si se excede, usar el archivo cifrado.
- El archivo cifrado anterior sigue funcionando igual.

## Rediseño
- Nuevo tema (ui/theme/YuriTheme.kt): papel/espresso, turquesa = dinero/listo, ámbar = pendiente, serif para títulos y cifras, azulejo cubano sutil.
- Inicio de Yuri con "Caja de hoy" (efectivo vs transferencia), tarjetas de producto con menú, venta con botones −/+ y pago en dos botones, ventas con estado claro, login centrado en el equipo.

## Técnico
- Dependencias: CameraX 1.4.1 y ZXing core 3.5.3 (sin Google Play Services). Permiso CAMERA (opcional).
- versionCode 9 / 1.7.0.
