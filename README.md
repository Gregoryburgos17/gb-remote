# GBRemote · Kotlin + SQLite

Cliente nativo de GB ERP para trabajar con una copia local de los datos permitidos de cada usuario.

## Descargar Windows

**[Instalar GBRemote 0.7.4 para Windows x64](https://github.com/Gregoryburgos17/gb-remote/releases/download/kotlin-v0.7.4-preview/GBRemote-0.7.4-unsigned.exe)**

[Ver publicación, huella SHA-256 e información de firma](https://github.com/Gregoryburgos17/gb-remote/releases/tag/kotlin-v0.7.4-preview).

Esta es una versión preliminar **sin firma pública**. El instalador fue compilado y empaquetado; la validación funcional corresponde al propietario. No se ejecutaron pruebas ni se inició la aplicación para esta publicación.

## Uso

1. Instale el EXE. La ventana debe mostrar **GBRemote · 0.7.4 · Kotlin**.
2. Entre con su usuario y contraseña de GB ERP y cree un PIN para abrir su copia local.
3. La plataforma resuelve la empresa: no se introduce URL ni nombre de base de datos.
4. Abra las aplicaciones autorizadas de su cuenta desde el menú original de GB ERP.
5. Pulse **Sincronizar** para el modo manual. En el pie o menú de usuario, elija **Configurar sincronización** para activar el modo automático y su intervalo.

Los cambios se guardan primero en este equipo. La aplicación conserva los pendientes durante una caída de conexión y los identifica hasta recibir respuesta del servidor. Los datos y claves se mantienen separados por cuenta.

## Qué incluye 0.7.4

- Iconos originales y navegación por secciones y subgrupos de las aplicaciones autorizadas.
- Listas y formularios nativos a partir de los campos y vistas del usuario.
- Avatares, actividades, búsqueda local y paginación.
- Sincronización manual o automática cada 2, 5, 15 o 30 minutos, con pausa durante la edición y reintentos espaciados ante fallos.
- Estados diferenciados de última descarga, último intento y sincronización completa.

El cliente utiliza Kotlin, Compose y SQLite. No incluye un servidor Odoo ni instala Python o PostgreSQL en el equipo del empleado.

## Actualización necesaria en GB ERP

Para recibir la cola genérica, el servidor debe cargar **gb_remote_offline 20.0.0.6.0**, publicar `/gb_remote/v1/erp/catalog` y `/gb_remote/v1/erp/push` y anunciar `native_sync.protocol=1`.

El [módulo y la guía de actualización están en gb-erp](https://github.com/Gregoryburgos17/gb-erp/tree/main/addons/gb_remote_offline) (repositorio privado). Subir el código a GitHub no lo despliega en el servidor. Si falta esa API, GBRemote mantiene la cola en el dispositivo y lo informa.

## Administración y soporte

Con una cuenta interna de la plataforma que tenga el permiso **SaaS Platform/User**, incluida su administración, 0.7.4 permite abrir GB Core o buscar un cliente para soporte. También aparece **Clientes para soporte** en el menú de cuenta de la plataforma.

Cada entrada usa un token firmado de un solo uso y el usuario espejo del soporte web. La visita y la sesión se registran en GB ERP. La contraseña central no se envía al cliente y la copia queda separada por usuario, empresa y base. El permiso local de soporte dura como máximo 30 minutos sin renovación y la sesión tiene un máximo de ocho horas.

El servidor requiere **gb_saas_master 20.0.1.7.2** en la central, **gb_saas_tenant 20.0.1.1.1** y **gb_remote_offline 20.0.0.6.0** en las bases correspondientes. El paquete de servidor incluye los tres addons y su guía. La actualización debe aplicarse en el Odoo activo; publicarla en GitHub no la despliega.
## Alcance actual

Esta versión todavía requiere adaptadores Kotlin para acciones especiales, asistentes, métodos de botones, pagos, confirmaciones y edición de líneas x2many. Mostrar el icono de una aplicación no significa que todos sus procesos estén implementados.

El cliente Kotlin actual todavía no tiene APK. Los archivos `windows/GBRemote.cs`, `android/` y su workflow corresponden al cliente de conexión anterior; no producen este instalador 0.7.4. La fuente actual está en [gb-erp/clients/gbremote](https://github.com/Gregoryburgos17/gb-erp/tree/main/clients/gbremote).

Los instaladores se distribuyen como archivos de Releases. `windows/kotlin-release.json` identifica la descarga vigente de esta vista previa; no constituye un actualizador automático del programa.
