# GB Remote

Conecte su computadora o su teléfono con su sistema GB, sin configurar nada.

## Descargar

| Equipo | Descarga |
|---|---|
| Windows 8, 8.1, 10 y 11 | [GBRemote.exe](../../releases/latest/download/GBRemote.exe) |
| Android 5 o más nuevo | [GBRemote.apk](../../releases/latest/download/GBRemote.apk) |

No hace falta instalar nada más. En Windows, el programa es un solo archivo: guárdelo en el
Escritorio y ábralo. En Android, al abrir el archivo el teléfono pide permiso para
"instalar apps de origen desconocido": acéptelo una vez.

## Cómo se usa

1. Abra **GB Remote**.
2. Escriba el **ID de su empresa** (se lo da GB, por ejemplo `farmacia-moma`) y toque **Conectar**.
3. Aparece un **código de 9 números**. GB revisa la solicitud y la **acepta**. Si le llaman, diga ese código.
4. Cuando diga **Conectado**, toque **Abrir mi sistema**.

La próxima vez que abra GB Remote ya estará conectado: solo toque **Abrir mi sistema**.

Si GB rechaza o desconecta el equipo, la app lo dice y vuelve a pedir el ID de la empresa.

## Qué datos envía

Para que soporte sepa qué equipo pide entrar y pueda ayudarle, la app envía junto con la
solicitud: sistema operativo y versión, memoria, disco, procesador, navegadores instalados,
impresoras (Windows), tamaño de pantalla, idioma y zona horaria. No envía archivos, fotos,
contactos, contraseñas ni nada de lo que usted escribe.

El equipo se identifica con una clave secreta que se crea en el propio equipo. GB solo guarda
una huella de esa clave, nunca la clave.

---

## Para soporte (GB)

- Las solicitudes llegan a **GB Core > Soporte > Monitoreo > Dispositivos (GB Remote)**, con
  los detalles del equipo y los botones **Aceptar**, **Rechazar** y **Desconectar**.
- Servidor: módulo `gb_remote` (repositorio gb-erp), API `POST /gb_remote/api/v1/register` y
  `/status`, con límite de solicitudes por dirección.
- Cambiar la dirección de la plataforma sin recompilar:
  - Windows: un archivo `servidor.txt` junto a `GBRemote.exe` con la dirección en una línea.
  - Android: mantener presionado el título **GB Remote**.

### Compilar

Cada push a `main` compila las dos apps en GitHub Actions; una etiqueta `v*` (por ejemplo
`v0.1.0`) además publica la versión en *Releases*, que es lo que enlazan las descargas de arriba.

En **Settings > Secrets and variables > Actions**:

| Nombre | Tipo | Para qué |
|---|---|---|
| `GBREMOTE_SERVER` | variable | Dirección de la plataforma que queda dentro de las apps (ej. `https://soporte.midominio.com`) |
| `GBREMOTE_KEYSTORE_BASE64` | secreto | Llave de firma Android (`base64 -w0 gbremote.jks`) |
| `GBREMOTE_KEYSTORE_PASSWORD`, `GBREMOTE_KEY_ALIAS`, `GBREMOTE_KEY_PASSWORD` | secretos | Datos de esa llave |

Sin la llave, la APK se firma con una llave temporal: se instala bien, pero una versión nueva no
se puede instalar encima de la anterior (hay que desinstalar primero). Cree la llave una vez y
guárdela fuera del repositorio:

```sh
keytool -genkeypair -v -keystore gbremote.jks -alias gbremote -keyalg RSA -keysize 2048 -validity 10000
```

Compilar Windows en local (no requiere instalar nada: `csc.exe` viene con Windows):

```bat
C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe /codepage:65001 /target:winexe /optimize+ ^
  /out:GBRemote.exe /r:System.Management.dll /r:System.Web.Extensions.dll ^
  /r:System.Windows.Forms.dll /r:System.Drawing.dll windows\GBRemote.cs
```

Android en local: JDK 17 y Android SDK 35, luego `gradle assembleRelease` dentro de `android/`.
