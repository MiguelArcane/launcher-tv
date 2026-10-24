# Launcher Amarillo

Pantalla de inicio sin publicidad para una Android TV (Android 12, HiSilicon/TopTech).

- Reloj grande y fecha en español.
- Favoritos: Netflix, YouTube, TV, TV IP, Web, HDMI, Wi-Fi y ADMIN.
- Todas las apps instaladas, en orden alfabético.
- **Vigilante de Wi-Fi**: si la TV se queda sin red más de 10 segundos, pide reconectar o apaga y prende el Wi-Fi (hasta 5 intentos; luego reintenta cada 5 minutos).
- **TV IP**: 1.153 canales gratuitos de 20 países (Colombia primero), con miniatura de lo que están pasando que se renueva cada minuto.
  - Caracol TV, Canal RCN, Noticias Caracol, Noticias RCN y NTN24 abren su transmisión oficial en YouTube.
  - El resto viene de la lista pública [iptv-org](https://github.com/iptv-org/iptv), filtrada: sin canales de pago, sin retransmisiones desde IP sueltas, y probados uno por uno.
  - Flechas, CH+/CH− o siguiente/anterior cambian de canal.
  - Mantener OK o MENÚ sobre un canal permite cambiar su dirección o restaurar la de fábrica (que nunca se borra). También se pueden agregar canales.
- **Web**: sitios guardados (Mercado Play de fábrica) en un navegador con cursor para el control, que bloquea ventanas emergentes, redirecciones a otros sitios y anuncios.
- **ADMIN**:
  - Interruptor de ADB por red.
  - Bloqueador de anuncios propio: VPN local que solo filtra el DNS con una lista de 70.581 dominios de [StevenBlack/hosts](https://github.com/StevenBlack/hosts) (MIT). No quita los anuncios de YouTube.
  - Redes Wi-Fi cercanas con su señal.
  - Color de fondo: amarillo, azul noche, negro, verde, rosado o morado.

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

Permisos que se dan una sola vez, para el interruptor de ADB y la lista de redes:

```bash
adb shell pm grant com.owari.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant com.owari.launcher android.permission.ACCESS_FINE_LOCATION
```

Para volver a otro launcher, por ejemplo Projectivy:

```bash
adb shell cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity
```
