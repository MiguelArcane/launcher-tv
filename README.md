# Launcher Amarillo

Pantalla de inicio sin publicidad para una Android TV (Android 12, HiSilicon/TopTech), con reconexión automática del Wi-Fi.

- Reloj grande y fecha en español.
- Favoritos: Netflix, YouTube, TV, HDMI y Wi-Fi.
- Todas las apps instaladas, en orden alfabético.
- Vigilante de Wi-Fi en segundo plano: si la TV se queda sin red más de 10 segundos, pide reconectar o apaga y prende el Wi-Fi (hasta 5 intentos; luego reintenta cada 5 minutos).
- Pantalla ADMIN (círculo en Favoritos):
  - Interruptor para prender o apagar el ADB por red.
  - Redes Wi-Fi cercanas con su señal (barras, calidad y dBm), para ver cuál llega mejor a la TV.
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
