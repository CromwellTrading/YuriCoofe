# YuriCoofe 1.4.0

## Rediseño visual Café
- Se elimina el encabezado duplicado: la marca aparece una sola vez en la barra superior.
- La bienvenida pasa a ser un banner editorial espresso/caramelo, con degradados, formas de luz sutiles, esquinas amplias y entrada animada.
- El catálogo usa tarjetas de producto en dos columnas, fotografías más protagonistas, etiquetas de categoría, jerarquía de precios y elevación/sombras.
- Búsqueda, filtros y avisos usan controles compactos y redondeados para dejar más espacio a los productos.
- El estado de catálogo vacío ahora tiene una presentación integrada con la identidad Café y sin mensajes de datos ficticios.
- En la primera ejecución tras actualizar se aplica el tema claro Café (crema/espresso) una sola vez. El usuario puede volver a cambiar la apariencia desde Ajustes.

## Acceso único por rol
- Se elimina la pantalla separada de personal y el enlace «Acceso del personal».
- Clientes, dueña y trabajadoras entran desde el mismo formulario con nombre/usuario y contraseña.
- Android envía las credenciales al endpoint compartido `POST /api/v1/auth/login`.
- El backend prioriza el usuario/identificador creado para `OWNER` o `WORKER`; si no coincide, valida clientes mediante su nombre visible y contraseña dentro del negocio configurado.
- El rol siempre procede del backend; no existe selector de rol ni registro público de perfiles de personal.
- La ruta `customer-login` se conserva como compatibilidad con versiones anteriores.

## Funcionamiento sin conexión
- Se conserva el registro offline de clientes con verificador PBKDF2 con sal y contraseña temporal cifrada con Android Keystore.
- El login offline solo permite clientes cuyo verificador exista previamente en el dispositivo. Las cuentas de personal requieren validar credenciales con el backend; una sesión de personal ya abierta puede restaurarse desde el almacenamiento seguro existente.

## Versiones
- Android: `versionCode = 6`, `versionName = 1.4.0`.
- Backend: `1.2.0`.
- No requiere nueva migración SQL.
