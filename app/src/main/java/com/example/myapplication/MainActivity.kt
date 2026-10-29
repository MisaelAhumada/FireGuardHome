package com.example.myapplication


import android.os.Bundle
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

private const val TEMPERATURA_LIMITE = 65f
private const val TIEMPO_ACTIVACION_MS = 5000L
// Si Firebase Console muestra otra URL de Realtime Database, reemplaza esta dirección.
private const val FIREBASE_DATABASE_URL = "https://fireproyecthouse-default-rtdb.firebaseio.com"
private const val CANAL_ALERTAS = "fireguard_alertas"
private const val ID_NOTIFICACION_TEMPERATURA = 1001

private fun crearCanalNotificaciones(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val canal = NotificationChannel(
            CANAL_ALERTAS,
            "Alertas de incendio",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos cuando la temperatura supera el límite configurado"
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(canal)
    }
}

private fun enviarNotificacionTemperatura(context: Context, temperatura: Float, limite: Float) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val notificacion = NotificationCompat.Builder(context, CANAL_ALERTAS)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("🔥 FireGuard Home: temperatura elevada")
        .setContentText("Detectados ${temperatura.toInt()} °C. Límite configurado: ${limite.toInt()} °C.")
        .setStyle(NotificationCompat.BigTextStyle().bigText(
            "La temperatura alcanzó o superó el umbral establecido. Temperatura: ${temperatura.toInt()} °C; límite: ${limite.toInt()} °C. Verifica el entorno y recuerda que este prototipo utiliza sensores simulados."
        ))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(ID_NOTIFICACION_TEMPERATURA, notificacion)
}


class MainActivity : ComponentActivity() {

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        crearCanalNotificaciones(this)

        setContent {
            MaterialTheme {
                androidx.compose.runtime.key(auth.currentUser?.uid) {
                    val usuarioInicial = auth.currentUser

                    var usuarioAutenticado by remember {
                        mutableStateOf(usuarioInicial != null)
                    }

                    // Firebase mantiene la sesion entre aperturas.
                    androidx.compose.runtime.DisposableEffect(auth) {
                        val listener =
                            FirebaseAuth.AuthStateListener { firebaseAuth ->
                                usuarioAutenticado =
                                    firebaseAuth.currentUser != null
                            }

                        auth.addAuthStateListener(listener)

                        onDispose {
                            auth.removeAuthStateListener(listener)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (usuarioAutenticado) {
                            PanelIncendios(
                                onLogout = {
                                    auth.signOut()
                                }
                            )
                        } else {
                            LoginScreen(auth = auth)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun LoginScreen(auth: FirebaseAuth) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var esRegistro by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "FireGuard Home",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (esRegistro) {
                "Crear una cuenta"
            } else {
                "Iniciar sesion"
            },
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electronico") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contrasena") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val correo = email.trim()
                mensaje = ""

                when {
                    correo.isBlank() || password.isBlank() -> {
                        mensaje = "Completa el correo y la contrasena."
                    }

                    !android.util.Patterns.EMAIL_ADDRESS
                        .matcher(correo).matches() -> {
                        mensaje = "Introduce un correo valido."
                    }

                    esRegistro && password.length < 6 -> {
                        mensaje =
                            "La contrasena debe tener al menos 6 caracteres."
                    }

                    else -> {
                        cargando = true

                        val tarea = if (esRegistro) {
                            auth.createUserWithEmailAndPassword(
                                correo,
                                password
                            )
                        } else {
                            auth.signInWithEmailAndPassword(
                                correo,
                                password
                            )
                        }

                        tarea.addOnCompleteListener { resultado ->
                            cargando = false

                            if (resultado.isSuccessful) {
                                mensaje = ""
                            } else {
                                mensaje = if (esRegistro) {
                                    "No se pudo crear la cuenta. " +
                                            "Comprueba los datos e intenta de nuevo."
                                } else {
                                    "No se pudo iniciar sesion. " +
                                            "Comprueba tus credenciales e internet."
                                }
                            }
                        }
                    }
                }
            },
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    cargando -> "Procesando..."
                    esRegistro -> "Crear cuenta"
                    else -> "Iniciar sesion"
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                esRegistro = !esRegistro
                mensaje = ""
            },
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (esRegistro) {
                    "Ya tengo una cuenta"
                } else {
                    "Registrarme"
                }
            )
        }

        if (mensaje.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = mensaje,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

enum class EstadoSistema {
    VIGILANDO,
    TEMPERATURA_ELEVADA,
    CUENTA_REGRESIVA,
    RIEGO_ACTIVO,
    PARADA_SOLICITADA,
    MODO_MANTENIMIENTO,
    SIN_CONEXION
}
@Composable
fun PanelIncendios(onLogout: () -> Unit) {
    val context = LocalContext.current
    val permisoNotificaciones = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* El usuario puede habilitarlo luego desde Ajustes de Android. */ }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val azulFondo = Color(0xFF07182B)
    val azulTarjeta = Color(0xFF102B46)
    val azulBorde = Color(0xFF244968)
    val verde = Color(0xFF00C99A)
    val rojo = Color(0xFFFF3838)
    val blanco = Color(0xFFF4F8FC)
    val gris = Color(0xFFB5C7D9)

    var temperatura by remember { mutableStateOf(25f) }
    var limiteTemperatura by remember { mutableStateOf(65f) }
    // Control de la cuenta regresiva
    var cuentaRegresivaActiva by remember {
        mutableStateOf(false)
    }
// Segundos restantes para la activación
    var segundosRestantes by remember {
        mutableIntStateOf(5)
    }
// Número de cancelaciones consecutivas
    var cancelacionesConsecutivas by remember {
        mutableIntStateOf(0)
    }
    // Estado del apagado temporal
    var sistemaApagadoTemporal by remember {
        mutableStateOf(false)
    }

// Momento en que debe finalizar el apagado
    var finApagadoTemporal by remember {
        mutableStateOf<Long?>(null)
    }

// Tiempo restante, expresado en milisegundos
    var tiempoRestanteApagado by remember {
        mutableLongStateOf(0L)
    }

// Mostrar la ventana de configuración del apagado
    var mostrarApagadoTemporal by remember {
        mutableStateOf(false)
    }
    // Controlar el tiempo restante del apagado temporal.
    LaunchedEffect(sistemaApagadoTemporal, finApagadoTemporal) {
        while (sistemaApagadoTemporal && finApagadoTemporal != null) {
            val restante = finApagadoTemporal!! - System.currentTimeMillis()

            if (restante <= 0L) {
                sistemaApagadoTemporal = false
                finApagadoTemporal = null
                tiempoRestanteApagado = 0L
                break
            }

            tiempoRestanteApagado = restante
            delay(1000)
        }
    }

    val maxCancelaciones = 3

// Control de la alerta crítica
    var alertaCriticaVisible by remember {
        mutableStateOf(false)
    }
// Evita repetir la alerta crítica en el mismo incidente
    var alertaCriticaEmitida by remember {
        mutableStateOf(false)
    }
    var inicioTemperaturaElevada by remember {
        mutableStateOf<Long?>(null)
    }
    var riegoActivo by remember { mutableStateOf(false) }
    var avisoVisible by remember { mutableStateOf(false) }
    var estabaSobreLimite by remember { mutableStateOf(false) }
    var avisosDescartados by remember { mutableStateOf(0) }
    var estadoConexion by remember { mutableStateOf("Conectando a la nube…") }
    var sincronizacionInicialLista by remember { mutableStateOf(false) }
    val estadoCompartido = remember {
        FirebaseDatabase.getInstance(FIREBASE_DATABASE_URL)
            .reference.child("fireguard").child("shared")
    }

    // Sincroniza lecturas y estado con todos los dispositivos autenticados.
    LaunchedEffect(estadoCompartido) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.child("temperatura").getValue(Float::class.java)?.let { temperatura = it.coerceIn(0f, 100f) }
                snapshot.child("limiteTemperatura").getValue(Float::class.java)?.let { limiteTemperatura = it.coerceIn(40f, 90f) }
                snapshot.child("riegoActivo").getValue(Boolean::class.java)?.let { riegoActivo = it }
                sincronizacionInicialLista = true
                estadoConexion = "Conectado · datos compartidos en tiempo real"
            }
            override fun onCancelled(error: DatabaseError) {
                estadoConexion = "Sin conexión a la base de datos: revisa Firebase y sus reglas"
            }
        }
        estadoCompartido.addValueEventListener(listener)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            estadoCompartido.removeEventListener(listener)
        }
    }

    LaunchedEffect(temperatura, limiteTemperatura, riegoActivo, sincronizacionInicialLista) {
        if (!sincronizacionInicialLista) return@LaunchedEffect
        val datos = mapOf(
            "temperatura" to temperatura,
            "limiteTemperatura" to limiteTemperatura,
            "riegoActivo" to riegoActivo,
            "actualizadoEn" to System.currentTimeMillis()
        )
        estadoCompartido.updateChildren(datos)
            .addOnFailureListener { estadoConexion = "No se pudo sincronizar: revisa Firebase" }
    }

    var mostrarAjustes by remember { mutableStateOf(false) }
    var mostrarConfirmacionParada by remember {
        mutableStateOf(false)
    }

    // Detectar cuando la temperatura supera o deja de superar el limite.

    LaunchedEffect(
        temperatura >= limiteTemperatura,
        sistemaApagadoTemporal
    ) {
        val elevada = temperatura >= limiteTemperatura

        if (sistemaApagadoTemporal) {
            cuentaRegresivaActiva = false
            riegoActivo = false

        } else if (elevada && !estabaSobreLimite) {
            // Se inicia un nuevo incidente de temperatura elevada.
            inicioTemperaturaElevada = System.currentTimeMillis()
            cancelacionesConsecutivas = 0
            alertaCriticaEmitida = false
            avisoVisible = true
            cuentaRegresivaActiva = true
            segundosRestantes = 5

            enviarNotificacionTemperatura(
                context,
                temperatura,
                limiteTemperatura
            )

        } else if (!elevada) {
            // La temperatura ha vuelto al rango normal.
            inicioTemperaturaElevada = null
            cuentaRegresivaActiva = false
            riegoActivo = false
            segundosRestantes = 5
            cancelacionesConsecutivas = 0
            alertaCriticaVisible = false
            alertaCriticaEmitida = false
            avisoVisible = false
        }

        estabaSobreLimite = elevada
    }

    val temperaturaElevada = temperatura >= limiteTemperatura

    // Reiniciar el conteo si la temperatura sigue elevada
    // y el usuario ya canceló el intento anterior.
    LaunchedEffect(
        temperatura >= limiteTemperatura,
        cuentaRegresivaActiva,
        riegoActivo
    ) {
        if (
            temperatura >= limiteTemperatura &&
            !cuentaRegresivaActiva &&
            !riegoActivo &&
            !sistemaApagadoTemporal
        ) {
            delay(1000)

            if (
                temperatura >= limiteTemperatura &&
                !cuentaRegresivaActiva &&
                !riegoActivo &&
                !sistemaApagadoTemporal
            ) {
                segundosRestantes = 5
                cuentaRegresivaActiva = true
            }
        }
    }

    // Cuenta regresiva para activar el riego simulado.
    LaunchedEffect(cuentaRegresivaActiva) {
        if (cuentaRegresivaActiva) {
            while (segundosRestantes > 0) {
                delay(1000)
                segundosRestantes--
            }

            if (
                temperatura >= limiteTemperatura &&
                cuentaRegresivaActiva &&
                !sistemaApagadoTemporal
            ) {
                riegoActivo = true
            }

            cuentaRegresivaActiva = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(azulFondo)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // CABECERA Y BOTON LATERAL DE AJUSTES
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "FireGuard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = blanco
                )

                Text(
                    "HOME  |  PROTECCION INTELIGENTE",
                    style = MaterialTheme.typography.labelSmall,
                    color = gris
                )
            }

            OutlinedButton(
                onClick = { mostrarAjustes = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = blanco
                )
            ) {
                Text("⚙")
                Text(" Ajustes")
            }
        }

        Text(
            estadoConexion,
            style = MaterialTheme.typography.bodySmall,
            color = if (estadoConexion.startsWith("Conectado")) verde else gris
        )

        if (sistemaApagadoTemporal) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF4A1820)
                ),
                border = BorderStroke(1.dp, rojo)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "⚠ PROTECCIÓN AUTOMÁTICA DESACTIVADA",
                        color = rojo,
                        style = MaterialTheme.typography.titleMedium
                    )

                    val segundos = tiempoRestanteApagado / 1000
                    val horas = segundos / 3600
                    val minutos = (segundos % 3600) / 60
                    val segundosRestantesUI = segundos % 60

                    Text(
                        "Reactivación automática en: " +
                                String.format(
                                    java.util.Locale.getDefault(),
                                    "%02d:%02d:%02d",
                                    horas,
                                    minutos,
                                    segundosRestantesUI
                                ),
                        color = blanco
                    )

                    Text(
                        "El riego automático está suspendido durante este periodo.",
                        color = blanco
                    )

                    Button(
                        onClick = {
                            sistemaApagadoTemporal = false
                            finApagadoTemporal = null
                            tiempoRestanteApagado = 0L

                            if (temperatura >= limiteTemperatura) {
                                inicioTemperaturaElevada = System.currentTimeMillis()
                                segundosRestantes = 5
                                cuentaRegresivaActiva = true
                                avisoVisible = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = verde,
                            contentColor = Color(0xFF052D2A)
                        )
                    ) {
                        Text("Reactivar protección")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (temperatura >= limiteTemperatura) Color(0xFF4A1820) else Color(0xFF10372F)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (temperatura >= limiteTemperatura) rojo.copy(alpha = 0.65f) else verde.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (temperatura >= limiteTemperatura) "⚠" else "✓",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (temperatura >= limiteTemperatura) rojo else verde
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (temperatura >= limiteTemperatura) "ALERTA DE TEMPERATURA ACTIVA" else "VIGILANCIA ACTIVA",
                        color = blanco,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (temperatura >= limiteTemperatura) "Se genera una notificación al detectar el umbral." else "El sistema avisará si se supera el límite.",
                        color = gris,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // TARJETA DE TEMPERATURA
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = azulTarjeta
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                azulBorde
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "TEMPERATURA ACTUAL",
                    style = MaterialTheme.typography.labelLarge,
                    color = gris
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${temperatura.toInt()}°",
                        style = MaterialTheme.typography.displayMedium,
                        color = blanco
                    )

                    Text(
                        "C",
                        style = MaterialTheme.typography.headlineSmall,
                        color = gris
                    )
                }

                Text(
                    "Limite establecido: ${limiteTemperatura.toInt()} °C",
                    color = if (temperaturaElevada) rojo else gris
                )

                Text(
                    if (temperaturaElevada) {
                        "● Temperatura fuera del rango establecido"
                    } else {
                        "● Temperatura dentro del rango establecido"
                    },
                    color = if (temperaturaElevada) rojo else verde
                )

                // Sensor simulado para realizar pruebas.
                Slider(
                    value = temperatura,
                    onValueChange = { temperatura = it },
                    valueRange = 0f..100f,
                    steps = 99
                )

                Text(
                    "Simulacion del sensor de temperatura",
                    style = MaterialTheme.typography.bodySmall,
                    color = gris
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nivel térmico", color = gris, style = MaterialTheme.typography.labelMedium)
                    Text(
                        when {
                            temperatura >= limiteTemperatura -> "CRÍTICO"
                            temperatura >= limiteTemperatura * 0.75f -> "PRECAUCIÓN"
                            else -> "NORMAL"
                        },
                        color = when {
                            temperatura >= limiteTemperatura -> rojo
                            temperatura >= limiteTemperatura * 0.75f -> Color(0xFFFFB547)
                            else -> verde
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (temperatura / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = when {
                        temperatura >= limiteTemperatura -> rojo
                        temperatura >= limiteTemperatura * 0.75f -> Color(0xFFFFB547)
                        else -> verde
                    },
                    trackColor = azulBorde
                )
            }
        }

        // ALERTA CENTRAL CIRCULAR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    if (temperaturaElevada) {
                        avisoVisible = true
                    }
                },
                enabled = temperaturaElevada,
                modifier = Modifier.size(218.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = rojo,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF203449),
                    disabledContentColor = gris
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (temperaturaElevada) "!" else "✓",
                        style = MaterialTheme.typography.displayLarge
                    )

                    Text(
                        if (temperaturaElevada) "ALERTA" else "NORMAL",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        if (temperaturaElevada) {
                            "Temperatura elevada"
                        } else {
                            "Sistema en vigilancia"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // BOTON PRINCIPAL PARA DETENER EL RIEGO
        Button(
            onClick = {
                mostrarConfirmacionParada = true
            },
            enabled = riegoActivo,
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = verde,
                contentColor = Color(0xFF052D2A),
                disabledContainerColor = Color(0xFF263B4C),
                disabledContentColor = gris
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    "DETENER SISTEMA DE RIEGO",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "ANTE INCENDIOS",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        Text(
            if (riegoActivo) {
                "● Sistema de riego: ACTIVO (SIMULADO)"
            } else {
                "● Sistema de riego: INACTIVO"
            },
            color = if (riegoActivo) verde else gris
        )

        // TARJETAS DE ESTADO
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = azulTarjeta
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                azulBorde
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "ESTADO DEL SISTEMA",
                    style = MaterialTheme.typography.labelLarge,
                    color = blanco
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("IoT", color = gris)
                        Text("Simulado", color = blanco)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Proteccion", color = gris)
                        Text(
                            if (riegoActivo) "Activa" else "Detenida",
                            color = if (riegoActivo) verde else blanco
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Umbral", color = gris)
                        Text(
                            "${limiteTemperatura.toInt()} °C",
                            color = blanco
                        )
                    }
                }
            }
        }

        Text(
            "Prototipo academico. No conectado a una bomba real.",
            style = MaterialTheme.typography.bodySmall,
            color = gris
        )

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = blanco
            )
        ) {
            Text("Cerrar sesion")
        }
    }

    // VENTANA DE AJUSTES
    if (mostrarAjustes) {
        AlertDialog(
            onDismissRequest = { mostrarAjustes = false },
            containerColor = azulTarjeta,
            title = {
                Text("Ajustes del sistema", color = blanco)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Limite de temperatura",
                        color = blanco
                    )

                    Text(
                        "${limiteTemperatura.toInt()} °C",
                        style = MaterialTheme.typography.headlineMedium,
                        color = verde
                    )

                    Slider(
                        value = limiteTemperatura,
                        onValueChange = {
                            limiteTemperatura = it
                        },
                        valueRange = 40f..90f,
                        steps = 49
                    )

                    Text(
                        "Para las pruebas puedes modificar el umbral. " +
                                "No lo aumentes durante una posible emergencia " +
                                "sin verificar primero el entorno.",
                        color = gris
                    )

                    Text(
                        "El control IoT esta simulado.",
                        color = gris
                    )
                    HorizontalDivider(color = gris)

                    Text(
                        "Protección automática",
                        color = blanco,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Puedes suspender temporalmente la protección " +
                                "automática durante tareas de mantenimiento.",
                        color = gris
                    )

                    TextButton(
                        onClick = {
                            mostrarAjustes = false
                            mostrarApagadoTemporal = true
                        }
                    ) {
                        Text(
                            "Apagar sistema temporalmente",
                            color = rojo
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { mostrarAjustes = false }
                ) {
                    Text("Guardar ajustes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        limiteTemperatura = 65f
                        mostrarAjustes = false
                    }
                ) {
                    Text("Restablecer")
                }
            }
        )
    }

    if (mostrarApagadoTemporal) {
        AlertDialog(
            onDismissRequest = {
                mostrarApagadoTemporal = false
            },
            containerColor = azulTarjeta,
            title = {
                Text(
                    "Apagado temporal",
                    color = blanco
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "La protección automática quedará suspendida " +
                                "durante el tiempo seleccionado.",
                        color = blanco
                    )

                    Text(
                        "Mientras esté apagada, el riego automático " +
                                "no se activará.",
                        color = rojo
                    )

                    TextButton(
                        onClick = {
                            sistemaApagadoTemporal = true
                            finApagadoTemporal =
                                System.currentTimeMillis() + 1L * 60 * 60 * 1000
                            tiempoRestanteApagado = 1L * 60 * 60 * 1000

                            cuentaRegresivaActiva = false
                            segundosRestantes = 5
                            riegoActivo = false

                            mostrarApagadoTemporal = false
                        }
                    ) {
                        Text("Apagar durante 1 hora", color = blanco)
                    }

                    TextButton(
                        onClick = {
                            sistemaApagadoTemporal = true
                            finApagadoTemporal =
                                System.currentTimeMillis() + 2L * 60 * 60 * 1000
                            tiempoRestanteApagado = 2L * 60 * 60 * 1000

                            cuentaRegresivaActiva = false
                            segundosRestantes = 5
                            riegoActivo = false

                            mostrarApagadoTemporal = false
                        }
                    ) {
                        Text("Apagar durante 2 horas", color = blanco)
                    }

                    TextButton(
                        onClick = {
                            sistemaApagadoTemporal = true
                            finApagadoTemporal =
                                System.currentTimeMillis() + 3L * 60 * 60 * 1000
                            tiempoRestanteApagado = 3L * 60 * 60 * 1000

                            cuentaRegresivaActiva = false
                            segundosRestantes = 5
                            riegoActivo = false

                            mostrarApagadoTemporal = false
                        }
                    ) {
                        Text("Apagar durante 3 horas", color = blanco)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarApagadoTemporal = false
                    }
                ) {
                    Text("Cancelar", color = blanco)
                }
            }
        )
    }


    // CONFIRMACION ANTES DE DETENER EL RIEGO
    if (mostrarConfirmacionParada) {
        AlertDialog(
            onDismissRequest = {
                mostrarConfirmacionParada = false
            },
            containerColor = azulTarjeta,
            title = {
                Text(
                    "Detener sistema de riego",
                    color = blanco
                )
            },
            text = {
                Text(
                    "¿Confirmas que deseas detener el riego simulado? " +
                            "Si existe un incendio real, verifica primero " +
                            "que sea seguro interrumpir la proteccion.",
                    color = gris
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        riegoActivo = false
                        estadoCompartido.child("riegoActivo").setValue(false)
                        mostrarConfirmacionParada = false
                    }
                ) {
                    Text("Confirmar detencion")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mostrarConfirmacionParada = false
                    }
                ) {
                    Text("Volver")
                }
            }
        )
    }

    // AVISO DE TEMPERATURA
    if (avisoVisible && temperaturaElevada) {
        AlertDialog(
            onDismissRequest = {
                avisoVisible = false
                avisosDescartados++
            },
            containerColor = azulTarjeta,
            title = {
                Text("Alerta de temperatura", color = rojo)
            },
            text = {
                Text(
                    "La temperatura es de ${temperatura.toInt()} °C " +
                            "y ha alcanzado o superado el limite de " +
                            "${limiteTemperatura.toInt()} °C. " +
                            "Verifica el entorno y el sistema de proteccion.",
                    color = blanco
                )
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (
                                cuentaRegresivaActiva &&
                                cancelacionesConsecutivas < maxCancelaciones
                            ) {
                                cuentaRegresivaActiva = false
                                segundosRestantes = 5
                                cancelacionesConsecutivas++
                            }

                            avisoVisible = false
                            avisosDescartados++
                        }
                    ) {
                        Text(
                            if (cuentaRegresivaActiva) {
                                "Cancelar activación"
                            } else {
                                "Cerrar"
                            },
                            color = rojo
                        )
                    }

                    TextButton(
                        onClick = {
                            avisoVisible = false
                            avisosDescartados++
                        }
                    ) {
                        Text("Entendido", color = blanco)
                    }
                }
            }
        )
    }
}

