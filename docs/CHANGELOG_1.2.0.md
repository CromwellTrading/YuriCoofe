# Cafetería Android 1.2.0

- La app abre directamente en el catálogo público como visitante; no requiere cuenta para consultar la tienda.
- El acceso de dueña y trabajadoras se encuentra en Ajustes. El servidor sigue asignando el rol; la app no permite autoproclamarse administradora.
- Se añaden apariencias Sistema, Claro y Oscuro, y cinco paletas de color persistentes.
- Las altas y ediciones de catálogo realizadas por la dueña se guardan primero en Room. Las fotografías seleccionadas se copian al almacenamiento privado de la app y las operaciones quedan en una cola local duradera.
- Las ventas se guardan localmente con un identificador idempotente, cantidades, precios aplicados, método de pago, trabajadora, zona horaria y hora original. WorkManager las reintenta con conexión disponible.
- El sincronizador ordena cambios de catálogo y ventas para crear productos offline antes de enviar ventas que los referencian.
- El catálogo diferencia la disponibilidad pública del inventario exacto; consultar la tienda no sobrescribe el stock privado que ya descargó el personal.
- Los productos SUBUNIT con stock de recipientes en cero permiten vender vasos/tazas/porciones sin inventar existencias. Los rendimientos desconocidos continúan requiriendo ajustes manuales de recipientes.
- Se prepara `versionCode=3`, `versionName=1.2.0` y generación de Release firmado mediante clave permanente almacenada en GitHub Actions Secrets.

## Requisito previo para trabajar sin conexión

Cada trabajadora y la dueña deben iniciar sesión con su cuenta mientras exista conexión y descargar el catálogo privado/inventario exacto. La cola conserva las operaciones después de cerrar la app y espera a que el sistema operativo detecte conectividad para sincronizar. Las operaciones rechazadas por conflictos no se descartan: quedan en revisión.
