# Launcher Amarillo

Pantalla de inicio sin publicidad para una Android TV (Android 12, HiSilicon/TopTech).

- Reloj grande y fecha en español.
- Favoritos: Netflix, YouTube, TV, TV IP y los sitios web guardados.
- Ajustes (debajo de las apps): HDMI, Wi-Fi y ADMIN.
- Todas las apps instaladas, en orden alfabético.
- **Vigilante de Wi-Fi**: si la TV se queda sin red más de 10 segundos, pide reconectar o apaga y prende el Wi-Fi (hasta 5 intentos; luego reintenta cada 5 minutos).
- **TV IP**: 1.153 canales gratuitos de 20 países (Colombia primero), con miniatura de lo que están pasando que se renueva cada minuto.
  - Caracol TV, Canal RCN, Noticias Caracol, Noticias RCN y NTN24 abren su transmisión oficial en YouTube.
  - El resto viene de la lista pública [iptv-org](https://github.com/iptv-org/iptv), filtrada: sin canales de pago, sin retransmisiones desde IP sueltas, y probados uno por uno.
  - Flechas, CH+/CH− o siguiente/anterior cambian de canal.
  - Mantener OK o MENÚ sobre un canal permite cambiar su dirección o restaurar la de fábrica (que nunca se borra). También se pueden agregar canales.
- **Sitios web en Favoritos**, cada uno con su ícono (Mercado Play de fábrica). Se abren con el motor de Firefox (GeckoView 140 ESR), porque el de la TV (Chromium 91) no entiende los sitios modernos. Cursor para el control, sin ventanas emergentes, sin redirecciones a otros sitios y con bloqueo de rastreadores de publicidad. Se agregan o quitan en ADMIN.
- **ADMIN**:
  - Interruptor de ADB por red.
  - Bloqueador de anuncios propio: VPN local que solo filtra el DNS con una lista de 70.581 dominios de [StevenBlack/hosts](https://github.com/StevenBlack/hosts) (MIT). No quita los anuncios de YouTube.
  - Redes Wi-Fi cercanas con su señal.
  - Color de fondo: amarillo, azul noche, negro, verde, rosado o morado.
  - **Una app a la vez**: cada vez que cambia lo que está en pantalla (otra app, la TV, el inicio, un sitio web) y al prender o apagar la TV, se cierra todo lo demás menos el launcher. Usa un servicio de accesibilidad que solo mira el nombre de la app al frente.

`targetSdk` se queda en 28 a propósito: desde Android 10 las apps que apuntan a 29+ no pueden apagar ni prender el Wi-Fi. Por eso Play Protect avisa al instalarlo.

## Compilar

Requiere JDK 17 y el SDK de Android (plataforma 35).

```bash
./gradlew assembleDebug
```

## Instalar por ADB

```bash
adb connect <ip-de-la-tv>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity com.owari.launcher/.MainActivity
```

Permisos que se dan una sola vez (ADB, lista de redes, bloqueador, volver al inicio al prender la TV y una app a la vez):

```bash
adb shell pm grant com.owari.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant com.owari.launcher android.permission.ACCESS_FINE_LOCATION
adb shell appops set com.owari.launcher ACTIVATE_VPN allow
adb shell appops set com.owari.launcher SYSTEM_ALERT_WINDOW allow
# Agregar el servicio de accesibilidad sin quitar los que ya hay (separados por dos puntos):
adb shell settings put secure enabled_accessibility_services <los-que-había>:com.owari.launcher/com.owari.launcher.ForegroundWatcherService
adb shell settings put secure accessibility_enabled 1
```

Para volver a otro launcher, por ejemplo Projectivy:

```bash
adb shell cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity
```
