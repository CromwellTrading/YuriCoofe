# Contrato de API v1 para Cafetería

Este documento define el contrato que consume la app Android. Especifica el comportamiento esperado para implementar el backend después. No significa que el servidor ya exista.

## Convenciones generales

- Prefijo de API: `/api/v1/`.
- La dirección base del cliente debe usar HTTPS y terminar en `/`.
- JSON UTF-8 salvo carga multipart de imágenes.
- Respuesta de éxito/error con sobre común:
  `{"data": <dato o null>, "message": "mensaje legible", "code": "CODIGO_OPCIONAL"}`.
- Los importes son enteros en CUP, nunca números decimales de punto flotante.
- Roles: `OWNER`, `WORKER`, `CUSTOMER`. La autorización se verifica en el servidor en cada endpoint, no solo en la interfaz Android.
- Un negocio representa un espacio de datos aislado. Ninguna consulta debe leer/modificar productos, cuentas, ventas, tokens o anuncios de otro negocio.
- Los endpoints administrativos requieren una sesión autenticada de `OWNER`; las ventas requieren `WORKER` o `OWNER`; el catálogo público y anuncios publicados pueden consultarse sin sesión, según los permisos especificados.

## Autenticación

### `POST /api/v1/auth/login`

Request: `{"identifier":"usuario-o-correo", "password":"..."}`.

Data de respuesta:

```json
{
  "accessToken": "token-de-sesion",
  "userId": "id-estable-de-usuario",
  "displayName": "Nombre visible",
  "role": "OWNER|WORKER|CUSTOMER",
  "businessId": "id-del-negocio",
  "businessTimeZone": "America/Havana"
}
```

`businessTimeZone` debe ser un identificador IANA. `POST /api/v1/auth/logout` invalida la sesión cuando corresponda. `GET /api/v1/auth/me` devuelve los datos de la sesión vigente.

## Productos e inventario

### `GET /api/v1/products`

Query params:

- `visibility=public` para catálogo de clientes; solo productos públicos activos.
- `visibility=all` para dueña/trabajadoras autorizadas.
- Opcionales: `category`, `sort=name|updatedAt|unitPriceCup|stockUnits`, `direction=asc|desc`.

La app actualmente ordena también localmente. El servidor debe filtrar por negocio y permisos, además de aplicar un orden estable.

Cada producto contiene estos campos (los opcionales pueden ser `null`):

```json
{
  "id":"id-del-producto",
  "name":"Nombre",
  "description":"Descripción",
  "categoryName":"Panadería",
  "imageUrl":"https://...",
  "unitName":"caja",
  "subunitName":"cigarro",
  "saleMode":"UNIT|SUBUNIT|BOTH",
  "subunitsPerUnit":20,
  "unitPriceCup":150,
  "unitPriceTransfer":145,
  "subunitPriceCup":10,
  "subunitPriceTransfer":10,
  "stockUnits":10,
  "stockSubunits":0,
  "minimumStock":2,
  "publicVisible":true,
  "updatedAt":"2026-10-27T09:30:00-04:00",
  "priceVersion":"version-opaca-de-precios"
}
```

`unitPriceCup` es obligatorio cuando se vende por unidad; puede ser `null` para `SUBUNIT`. `unitPriceTransfer` y `subunitPriceTransfer` son opcionales. Un precio de transferencia ausente o nulo significa que ese formato no puede cobrarse por transferencia. `stockSubunits` debe ser un número cuando se conoce la equivalencia; si no se conoce, debe ser `null`, no una cantidad inventada.

`priceVersion` es un token opaco que cambia cada vez que se modifica cualquier precio, moneda/método habilitado o formato que afecte al cálculo de precio. Los cambios de existencias por sí solos no deben cambiar `priceVersion`. El cliente lo envía en cada venta para detectar ventas con precios antiguos. `updatedAt` cambia ante modificaciones de producto de cualquier tipo.

### `POST /api/v1/products` y `PUT /api/v1/products/{id}`

Multipart form-data:

- `product`: JSON con los campos editables.
- `image`: opcional, binario de la imagen.

La creación puede incluir `stockUnits` y `stockSubunits`. La edición desde la app no pretende modificar las existencias directamente; el stock existente se modifica con ajuste de inventario y auditoría. Si no se envía nueva imagen al editar, el servidor debe conservar la imagen anterior. `DELETE /api/v1/products/{id}` debe desactivar lógicamente el producto, no borrar su historial de ventas.

### `POST /api/v1/inventory/adjustments`

Request:

```json
{"productId":"...", "unitDelta":5, "subunitDelta":0, "reason":"Reposición de mercancía"}
```

Solo `OWNER`. Debe actualizar el stock en una transacción y registrar actor, hora, delta anterior/nuevo y motivo en el historial de movimientos. Rechazar cualquier ajuste que produzca existencias negativas. Para productos con equivalencia desconocida, `subunitDelta` debe ser `null` y los ajustes se realizan sobre unidades/recipientes.

## Ventas, idempotencia y cola offline

### `POST /api/v1/sales`

Cabecera obligatoria: `Idempotency-Key: <clientSaleId>`.

Body completo generado una vez al confirmar la venta y almacenado sin modificaciones en SQLite:

```json
{
  "clientSaleId":"UUID",
  "soldAt":"2026-10-27T09:24:18-04:00",
  "deviceTimeZoneId":"America/Havana",
  "deviceOffsetMinutes":-240,
  "workerId":"id-de-la-trabajadora",
  "workerName":"Nombre guardado al vender",
  "paymentMethod":"CASH_CUP|TRANSFER_CUP",
  "currency":"CUP",
  "items":[
    {
      "productId":"id-producto",
      "productName":"Nombre al vender",
      "unitName":"caja",
      "subunitName":"cigarro",
      "unitQuantity":5,
      "subunitQuantity":30,
      "subunitsPerUnitSnapshot":20,
      "priceVersionSnapshot":"version-de-precios",
      "unitPriceAppliedCUP":150,
      "subunitPriceAppliedCUP":10,
      "totalCUP":1050
    }
  ],
  "totalCUP":1050,
  "appVersion":"1.0.0"
}
```

Valores del ejemplo solo ilustran la estructura del contrato; no son productos/precios precargados en la app.

El backend debe cumplir los puntos siguientes:

1. Autenticar a la persona y verificar que `workerId` corresponde al principal autenticado. No confiar en `workerName`, `totalCUP` ni precios enviados por el cliente como autoridad.
2. Antes de modificar nada, buscar si ya existe una venta con `clientSaleId` para ese negocio. Si existe, devolver la respuesta original y no descontar stock ni registrar caja una segunda vez.
3. Procesar inventario, detalle de venta, movimiento de stock, importes y resultado idempotente en una sola transacción de base de datos.
4. Verificar precio/versión en el servidor, recalcular el importe a partir de cantidades y precios vigentes, y validar que el método de pago esté habilitado en cada formato vendido. La app registra el precio aplicado al confirmar; si la versión cambió y el servidor no puede aceptar la venta con seguridad, devolver `409` con un mensaje que permita revisión, sin mutar parcialmente el inventario.
5. Si hay stock insuficiente o una versión incompatible que requiere intervención, responder `409` (conflicto) o `422` (validación). El registro local se conserva y se marca `NEEDS_REVIEW`.
6. Responder `401` si la sesión no es válida. Fallos transitorios o `5xx` deben poder reintentarse.
7. Conservar `soldAt` como hora del evento recibida y guardar aparte `receivedAt`/hora del servidor. No sustituir el evento original por la hora de sincronización. Usar `deviceTimeZoneId` y `deviceOffsetMinutes` solo como metadatos de contexto; para los informes del negocio usar la zona horaria configurada del negocio.
8. La respuesta exitosa devuelve `saleId`, `clientSaleId`, `status` y `soldAt` dentro de `data`.

La app escribe primero la venta y la deducción local en la misma transacción Room. WorkManager solo intenta enviar cuando hay conectividad y reutiliza el UUID, el header y el JSON original. Nunca se elimina una venta local al recibir error o al perder internet. La sincronización está limitada a la cuenta trabajadora original para evitar reasignar ventas a otra persona en un dispositivo compartido.

### `GET /api/v1/sales`

Params opcionales: `from`, `to` (ISO-8601 con offset), `workerId`, `limit`. La dueña puede consultar a todo su equipo; una trabajadora solo puede recibir sus operaciones; clientes no tienen acceso. Las respuestas deben contener `id`, `clientSaleId`, `soldAt`, `workerId`, `workerName`, `summary`, `paymentMethod`, `totalCUP`, `status`.

## Estadísticas

### `GET /api/v1/dashboard?from=...&to=...`

Solo `OWNER`. La respuesta `data` contiene:

- `cashCup`: total de ventas registradas como efectivo CUP en el periodo.
- `transferCup`: total de ventas registradas como transferencia en el periodo.
- `saleCount`: operaciones aceptadas.
- `lowStockCount`: productos activos por debajo del umbral mínimo.
- `totalInventoryValueCash`: valoración potencial al vender el stock disponible con precios de efectivo; `null` si no puede calcularse íntegramente.
- `totalInventoryValueTransfer`: valoración potencial por transferencia; `null` cuando no todos los productos/formas tienen precio transfer habilitado o la valoración no es exacta.

Los totales de transferencia son registros declarados por la operación; la API no debe presentarlos como confirmación bancaria automática si no hay integración de verificación.

## Avisos y ofertas

### `GET /api/v1/announcements?includeScheduled=false`

Público/clientes: solo avisos publicados y no vencidos. Dueña: puede pedir `includeScheduled=true` para revisar lo programado.

### `POST /api/v1/announcements`

Request: `title`, `message`, `publishAt`, `expiresAt` opcional y `sendPushNotification`. Las fechas deben ser ISO-8601 con offset. El servidor debe conservar la hora objetivo e implementar un planificador durable que publique/envíe el aviso a esa hora aunque el teléfono de la dueña esté apagado. El proceso debe ser idempotente para evitar notificaciones duplicadas. Las notificaciones push se dirigen a clientes suscritos al negocio y que no hayan revocado el permiso/token.

`PATCH /api/v1/announcements/{id}/cancel` cancela una publicación o aviso programado. La app espera `id`, `title`, `message`, `publishAt`, `expiresAt`, `status`, `imageUrl`.

## Equipo

- `GET /api/v1/team`: solo dueña; devuelve miembros del negocio.
- `POST /api/v1/team`: solo dueña; acepta `displayName`, `identifier`, `temporaryPassword`, `role=WORKER`. Nunca permitir crear otro `OWNER` desde este endpoint.
- `PATCH /api/v1/team/{id}/status`: solo dueña; body `{"active":true|false}`. Desactivar debe bloquear nuevas sesiones y revocar sesiones vigentes de esa cuenta. No se borran sus ventas históricas.

Las contraseñas deben almacenarse con un algoritmo de hash lento de contraseñas en el backend (no cifrado reversible). No devolver la contraseña ni datos sensibles en la respuesta de equipo.

## Notificaciones push

### `POST /api/v1/devices/push-token`

Body: `{"token":"token-FCM", "deviceId":"id-de-dispositivo", "platform":"ANDROID"}`. Asignar el token al negocio y, cuando aplique, a la cuenta autenticada; para cliente visitante se permite registro público con controles antiabuso. Debe deduplicar tokens, permitir su actualización/revocación y enviar los avisos desde el servidor. Nunca aceptar tokens para otros negocios por un `businessId` enviado libremente por el cliente.

La app obtiene el token de Firebase solo si las propiedades de Firebase están configuradas en la compilación. El backend necesita credenciales de servidor de Firebase almacenadas en su configuración protegida; no deben estar en APK ni en este repositorio.

## Requisitos mínimos de seguridad y consistencia

- Permisos por rol y aislamiento por negocio en todas las consultas y mutaciones.
- Validación de cantidades, importes, estados y tamaños de imagen en el servidor.
- Transacciones para venta + stock + movimientos + idempotencia.
- Claves únicas de idempotencia por negocio y `clientSaleId`.
- Auditoría para ajustes de stock, cancelaciones, correcciones y cambios de precios.
- Rate limiting y mensajes de error públicos que no revelen infraestructura, proveedores, trazas, SQL ni secretos.
- Imágenes servidas mediante URL segura; limitar tamaño/tipo y no confiar en el nombre local del fichero.
- Copias de seguridad y pruebas de recuperación antes de producción.
