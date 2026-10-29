# FireGuard Home — guía de puesta en marcha

## Requisitos
- Android Studio compatible con el Gradle Wrapper incluido.
- Android SDK que permita compilar el `compileSdk` definido en `app/build.gradle.kts`.
- Conexión a Internet para descargar dependencias y usar Firebase.
- Proyecto Firebase `fireproyecthouse` (el archivo `app/google-services.json` corresponde a ese proyecto y al paquete `com.example.myapplication`).

## Funciones implementadas
- Registro e inicio de sesión con Firebase Authentication (correo y contraseña).
- Panel FireGuard rediseñado con indicadores visuales, barra de nivel térmico, estados de riesgo y control de riego simulado.
- Notificación local de prioridad alta al detectar el cruce del umbral de temperatura. En Android 13 o superior se debe conceder permiso para notificaciones.
- Sincronización del estado compartido (temperatura, umbral y riego) mediante Firebase Realtime Database.
- Indicador de estado de conexión y cierre de sesión.

## Configuración necesaria en Firebase
1. En Firebase Console, abre el proyecto `fireproyecthouse`.
2. En Authentication → Sign-in method, habilita **Email/Password**.
3. En Realtime Database, crea una base de datos. Copia la URL exacta que muestra Firebase.
4. Abre `app/src/main/java/com/example/myapplication/MainActivity.kt` y reemplaza el valor de `FIREBASE_DATABASE_URL` por la URL exacta de tu base de datos si no coincide con la configurada.
5. Configura las reglas de Realtime Database para que solo usuarios autenticados lean y escriban. Como punto de partida académico, puedes adaptar `database.rules.example.json` desde la pestaña Rules de Firebase Console:

```json
{
  "rules": {
    "fireguard": {
      "shared": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

No uses reglas abiertas como `.read: true` o `.write: true`. Para una implementación de producción, limita el acceso por vivienda/grupo y valida los campos desde reglas o backend.

## Ejecutar en dos dispositivos
1. Abre la carpeta `MyApplication` en Android Studio y espera la sincronización Gradle.
2. Ejecuta la app en Pixel 8/emulador o un teléfono Android.
3. Crea una cuenta en la primera ejecución. En el segundo dispositivo, inicia sesión con una cuenta habilitada en Firebase.
4. Prueba mover el control de temperatura, cambiar el umbral y activar una alerta. El panel del otro dispositivo debe reflejar el estado compartido si ambos están conectados a la misma base de datos.

## Límites importantes
- La temperatura y los actuadores son simulados; no hay cámara térmica FLIR, sensor de proximidad ni válvula física conectados.
- La app no determina por sí sola la distancia real de un incendio exterior ni sustituye alertas oficiales o sistemas certificados.
- No se ha podido certificar una compilación limpia en este entorno porque el Gradle Wrapper necesita descargar Gradle y la conexión de red de este entorno no está disponible. Compila en Android Studio y corrige cualquier requisito de SDK que indique tu instalación.
