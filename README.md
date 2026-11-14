# YUTU

Reproductor de YouTube para Android, sin anuncios y sin cuenta obligatoria.
Este proyecto es un **fork modificado de [Yuno](https://github.com/danielkanan/Yuno)**,
bajo licencia **GPL-3.0** (ver `LICENSE` y `NOTICE`).

> YUTU no está afiliado a YouTube ni a Google. No aloja vídeos: reproduce los
> que sirve YouTube. Usar un cliente no oficial puede ir contra sus Términos de
> servicio y Google podría sancionar la cuenta; úsalo bajo tu responsabilidad.

## Qué cambia respecto a Yuno

- **Nombre, icono y paquete propios** (`com.yutu`), para que conviva con el original.
- **Actualizaciones solo con aviso**: la app comprueba si hay versión nueva y abre
  la página del release en el navegador. **Nunca descarga ni instala nada**, y ya
  no pide permisos de instalación.
- **Iconos Material** en lugar de los SF Symbols de Apple (no redistribuibles).
- **Fuente Inter** (OFL) en lugar de SF Pro Display (propiedad de Apple).

Todo lo demás —interfaz, reproductor, Shorts, SponsorBlock, comentarios— es el
trabajo original de Yuno.

## Instalar

Descarga el APK desde la sección de releases y ábrelo en el móvil, permitiendo
instalar apps desconocidas. Si tenías Yuno instalado, desinstálalo primero: la
firma es distinta y Android no deja actualizar por encima.

## Compilar

Necesitas JDK 21 y el SDK de Android. El script usa `../toolchain` y `../gradle-home`
por defecto; puedes apuntar a otros con `YUNO_TOOLCHAIN`, `JAVA_HOME`, `ANDROID_HOME`
o `GRADLE_USER_HOME`.

```sh
./rebuild.sh
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.

La firma se lee de `keystore.properties` (no está en el repositorio). Sin ese
fichero y su `.jks` no se puede compilar el release. **Haz copia de seguridad de
ambos**: si pierdes la clave no podrás actualizar la app; si se filtra, alguien
podría firmar una actualización que tu Android aceptaría.

## Antes de publicar

Cambia en `app/build.gradle.kts`:

- `SOURCE_URL` por la URL de **este** repositorio (la GPL exige ofrecer el código
  correspondiente al binario que distribuyes).
- `GITHUB_RELEASES_REPO` si quieres que el aviso de versión nueva apunte a tus
  releases en vez de a los de Yuno.

## Licencia

GPL-3.0. Ver `LICENSE`, `NOTICE` y `licenses/Inter-OFL.txt`.
