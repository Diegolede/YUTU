# YUTU

**Hecho por trn1** · [github.com/Diegolede](https://github.com/Diegolede)
*Autor del fork y responsable de la auditoría de seguridad.*

Reproductor de YouTube para Android, sin anuncios, sin cuenta obligatoria y **sin telemetría**.
Nace de [Yuno](https://github.com/danielkanan/Yuno) (GPL-3.0), con el objetivo de tener una app
que no hable con nadie más que con YouTube: nada de analíticas, nada de rastreo y ninguna forma de
que la app se descargue código por su cuenta.

> YUTU no está afiliado a YouTube ni a Google. No aloja vídeos: reproduce los que sirve YouTube.
> Usar un cliente no oficial puede ir contra sus Términos de servicio; úsalo bajo tu responsabilidad.

## Por qué existe

Quería un reproductor de YouTube y quería **saber exactamente a dónde va mi información**. Así que
revisé el cliente original línea por línea, le quité todo lo que podía hablar con terceros y dejé
el tráfico limitado a lo imprescindible. Lo que sigue es lo que hay en el APK, comprobable con
cualquier monitor de red:

| Destino | Para qué |
|---|---|
| `youtube.com`, `youtubei.googleapis.com`, `accounts.google.com` | buscar, reproducir y (si quieres) iniciar sesión |
| `i.ytimg.com`, `yt3.googleusercontent.com` | miniaturas y avatares |
| `sponsor.ajay.app` | saltar patrocinios (opcional, se puede desactivar) |
| `api.github.com` | **solo** consultar si hay versión nueva |

**Nada más.** Sin Firebase Analytics, sin Crashlytics, sin servidores propios.

## Lo que garantiza

- **No hay telemetría ni reporte de fallos remoto.** Los crashes se guardan en el propio móvil y
  solo salen si tú pulsas "Compartir".
- **La app no puede instalarse nada a sí misma.** Ya no pide `REQUEST_INSTALL_PACKAGES` ni
  `UPDATE_PACKAGES_WITHOUT_USER_ACTION`: cuando hay versión nueva, solo avisa y abre la página del
  release en el navegador. La actualización la decides y la haces tú.
- **Tu sesión de Google va cifrada** con AES-256-GCM y una clave del Android Keystore, y las
  cookies solo se envían a `youtube.com`. La app nunca ve tu contraseña: el login ocurre en la
  página real de Google dentro de la app.
- **Permisos mínimos**: red, notificaciones, reproducción en segundo plano, vibración y wake-lock.
  Nada de contactos, SMS, ubicación, cámara ni almacenamiento externo.

Si quieres verificarlo por tu cuenta: instala NetGuard o RethinkDNS y compara el tráfico con la
tabla de arriba. Es exactamente lo que hice yo antes de publicarla.

## Qué cambia respecto a Yuno

- Nombre, icono, paquete (`com.yutu`) y firma propios: convive con Yuno en lugar de sustituirlo.
- Actualizaciones solo con aviso: nunca descarga ni instala nada.
- Iconos Material (Apache-2.0) en lugar de los SF Symbols de Apple, e Inter (OFL) en lugar de
  SF Pro Display: nada de material que no se pueda redistribuir.
- Revisión del tráfico de red y de los permisos del APK, documentada arriba.

Todo lo demás —interfaz, reproductor, Shorts, SponsorBlock, comentarios— es el trabajo original
de Yuno, que sigue siendo la base de este proyecto.

## Instalar

**[Descargar YUTU.apk](https://github.com/Diegolede/YUTU/releases/latest/download/YUTU.apk)** y
abrirla en el móvil, permitiendo instalar apps desconocidas. Como el paquete y la firma son
distintos a los de Yuno, se instala al lado sin tocar la otra app.

## Compilar

Necesitas JDK 21 y el SDK de Android. El script usa `../toolchain` y `../gradle-home` por defecto;
puedes apuntar a otros con `YUNO_TOOLCHAIN`, `JAVA_HOME`, `ANDROID_HOME` o `GRADLE_USER_HOME`.

```sh
./rebuild.sh
```

La firma se lee de `keystore.properties`, que no está en el repositorio. Sin ese fichero y su
`.jks` el release sale sin firmar. **Guarda copia de ambos**: si pierdes la clave no podrás
actualizar la app; si se filtra, alguien podría firmar una actualización que tu Android aceptaría.

Para publicar una versión: escribe los cambios en `CHANGELOG.md` bajo `## Unreleased`, sube
`versionCode`/`versionName` en `app/build.gradle.kts` y ejecuta `scripts/publish-apk.sh`.

## Créditos y licencia

- **YUTU**: trn1 — autor del fork, responsable de la auditoría de seguridad y de los cambios de esta versión.
- **Yuno**: [danielkanan](https://github.com/danielkanan/Yuno) y sus colaboradores — la base.
- **NewPipeExtractor**, AndroidX Media3, OkHttp, Coil, Rhino, jsoup, Haze, Material Icons e Inter,
  cada uno con su licencia (ver `NOTICE` y `licenses/`).

GPL-3.0. Ver `LICENSE` y `NOTICE`.
