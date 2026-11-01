# YuriCoofe 1.3.0

## Experiencia de cliente y diseño
- La pantalla inicial se transforma en una bienvenida de YuriCoofe con identidad espresso/crema y formulario de cliente.
- El cliente puede iniciar sesión o crear su cuenta con nombre y contraseña antes de entrar al catálogo.
- El acceso de administración y trabajadoras se separa del flujo principal y no muestra un selector de roles.
- El tema Café queda como predeterminado, con modo claro/oscuro y las demás paletas disponibles en Ajustes.

## Cuentas de cliente y modo offline
- Se conecta Android con los endpoints `POST /api/v1/auth/register-customer` y `POST /api/v1/auth/customer-login`.
- Las cuentas creadas sin conexión quedan disponibles localmente en ese teléfono.
- El verificador de contraseña local usa PBKDF2-HMAC-SHA256 con sal; la contraseña necesaria para el primer envío se cifra con Android Keystore y se elimina de la cola después de la confirmación.
- WorkManager reintenta el alta al recuperar internet. Si el servidor aceptó la cuenta pero se perdió la respuesta, el reintento inicia sesión con los mismos datos en vez de crear una cuenta duplicada.
- El servidor aplica límite de frecuencia adicional al registro de cuentas de cliente.
- Los nombres visibles pueden repetirse; cada cuenta tiene un identificador interno único que no se muestra al cliente.

## Versión
- `versionCode = 5`
- `versionName = 1.3.0`
