# YuriCoofe Android 1.5.0

- Las estadísticas ahora agrupan las cantidades vendidas por producto usando las líneas reales de cada venta aceptada. Cuenta unidades completas y subunidades (vasos, tazas, porciones, etc.) aunque el producto no tenga control de existencias.
- Nuevo filtro de periodo: hoy, semana actual y mes actual, respetando la zona horaria configurada para la cafetería.
- Desglose de ingresos por producto entre efectivo y transferencia.
- Desglose por trabajadora: operaciones, efectivo, transferencias e importe total.
- Las ventas locales pendientes o marcadas para revisión se muestran separadas y no se mezclan con las ventas confirmadas del servidor.
- No se cambia el modelo de stock, no se descuenta stock adicional y no se requiere migración Room.
- Android `versionCode 7`, `versionName 1.5.0`; requiere backend 1.3.0 para los nuevos desgloses.
