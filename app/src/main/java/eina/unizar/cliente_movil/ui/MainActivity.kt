package eina.unizar.cliente_movil.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import eina.unizar.cliente_movil.R
import eina.unizar.cliente_movil.game.GameView
import eina.unizar.cliente_movil.networking.WebSocketClient
import org.json.JSONObject
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import galaxy.Galaxy.Event
import galaxy.Galaxy.EventType
import galaxy.Galaxy.NewPlayerEvent
import galaxy.Galaxy.NewFoodEvent
import galaxy.Galaxy.PlayerMoveEvent
import galaxy.Galaxy.PlayerGrowEvent
import galaxy.Galaxy.DestroyFoodEvent
import galaxy.Galaxy.DestroyPlayerEvent
import galaxy.Galaxy.JoinEvent
import okio.ByteString
import eina.unizar.cliente_movil.model.Food
import eina.unizar.cliente_movil.model.Player
import eina.unizar.cliente_movil.utils.ColorUtils
import org.json.JSONArray
import kotlin.text.toDouble
import eina.unizar.cliente_movil.utils.Constants
import kotlin.collections.plusAssign
import kotlin.div
import kotlin.text.toFloat
import kotlin.text.toInt
import kotlin.times


class GameActivity : AppCompatActivity(), GameView.MoveListener {

    private lateinit var gameView: GameView
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var btnReconnect: Button

    private var userId: String = ""
    private var userName: String = ""
    private var serverUrl: String = ""
    private var skinName: String? = null

    // Último estado completo recibido
    private var gameState: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // Leer parámetros de la Activity
        intent.extras?.let { b ->
            userId    = b.getString("userId", "")
            userName  = b.getString("userName", "")
            serverUrl = b.getString("serverUrl", "ws://10.0.2.2:8080/ws")
            skinName = b.getString("skinName", null)
        }

        if (userId.isEmpty() || userName.isEmpty() || serverUrl.isEmpty()) {
            Toast.makeText(this, "Faltan parámetros de conexión", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        gameView     = findViewById(R.id.GameView)
        btnReconnect = findViewById(R.id.btnReconnect)
        gameView.setMoveListener(this)

        // Preparamos el WebSocketListener inline
        val listener = object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "onOpen: Conectado al servidor")
                runOnUiThread {
                    Toast.makeText(this@GameActivity,
                        "¡Conectado!",
                        Toast.LENGTH_SHORT).show()
                }
                // Nada más abrir, enviamos el joinGame
                webSocketClient.joinGame(userName)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                try {
                    val event = Event.parseFrom(bytes.toByteArray())
                    Log.d(TAG, "Evento recibido: $event")
                    when (event.eventType) {
                        EventType.EvNewPlayer -> handleNewPlayer(event.newPlayerEvent)
                        EventType.EvNewFood -> handleNewFood(event.newFoodEvent)
                        EventType.EvPlayerMove -> handlePlayerMove(event.playerMoveEvent)
                        EventType.EvPlayerGrow -> handlePlayerGrow(event.playerGrowEvent)
                        EventType.EvDestroyFood -> handleDestroyFood(event.destroyFoodEvent)
                        EventType.EvDestroyPlayer -> handleDestroyPlayer(event.destroyPlayerEvent)
                        EventType.EvJoin -> handleJoin(event.joinEvent)
                        else -> Log.d(TAG, "Unhandled event type: ${event.eventType}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: ${e.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "onClosing: $code / $reason")
                ws.close(1000, null)
                runOnUiThread {
                    Toast.makeText(this@GameActivity,
                        "Servidor cerrando: $reason",
                        Toast.LENGTH_SHORT).show()
                    btnReconnect.visibility = View.VISIBLE
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "onFailure: ${t.message}")
                runOnUiThread {
                    Toast.makeText(this@GameActivity,
                        "Error: ${t.message}. Intentando reconectar...",
                        Toast.LENGTH_LONG).show()
                    reconnectWithDelay()
                }
            }
        }

        // Creamos el cliente y conectamos
        webSocketClient = WebSocketClient(serverUrl, listener)
        btnReconnect.setOnClickListener {
            btnReconnect.visibility = View.GONE
            webSocketClient.connect()
        }
        connectToServer()
    }

    private fun reconnectWithDelay(delayMillis: Long = 3000) {
        Log.d(TAG, "Intentando reconectar en $delayMillis ms...")
        btnReconnect.visibility = View.GONE
        gameView.postDelayed({
            connectToServer()
        }, delayMillis)
    }

    private fun connectToServer() {
        //webSocketClient.close() // Cierra conexiones previas
        btnReconnect.visibility = View.GONE
        webSocketClient.connect()
    }

    /** Cuando recibimos que el jugador actual muere */
    private fun onPlayerDied() {
        var finalScore = 0
        try {
            // Busca en el último estado la puntuación
            val players = gameState?.getJSONArray("players")
            if (players != null) {
                for (i in 0 until players.length()) {
                    val p = players.getJSONObject(i)
                    if (p.getString("id") == gameView.currentPlayerId) {
                        finalScore = p.optInt("score", 0)
                        break
                    }
                }
            }
        } catch (_: Exception) { /* ignore */ }

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage("Tu puntuación: $finalScore")
                .setPositiveButton("Jugar otra vez") { _, _ ->
                    webSocketClient.joinGame(userName)
                }
                .setNegativeButton("Salir") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    /** Desde GameView.MoveListener: enviamos movimiento */
    override fun onMove(directionX: Float, directionY: Float) {
        Log.d(TAG, "Movimiento enviado: X=$directionX, Y=$directionY")
        if (directionX != 0f || directionY != 0f) {
            webSocketClient.sendMovement(directionX, directionY)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient.close()
    }

    // Utilidad para convertir color RGB a ARGB (opacidad completa)
    private fun rgbToArgb(rgb: Int): Int {
        return 0xFF000000.toInt() or (rgb and 0x00FFFFFF)
    }

    private fun handleNewPlayer(event: NewPlayerEvent) {
        val player = Player(
            id = event.playerID.toStringUtf8(),
            x = event.position.x.toFloat(),
            y = event.position.y.toFloat(),
            radius = event.radius.toFloat(),
            color = rgbToArgb(event.color),
            skinName = skinName,
            username = userName,
            score = 0
        )
        runOnUiThread {
            gameView.updatePlayers(player)
            gameView.invalidate()
        }
    }

    private fun handleNewFood(event: NewFoodEvent) {
        try {
            // Validar datos del evento
            if (event.position == null) {
                Log.e(TAG, "EvNewFood: posición nula")
                return
            }

            val food = Food(
                id = "${event.position.x},${event.position.y}",
                x = event.position.x.toFloat(),
                y = event.position.y.toFloat(),
                radius = 20f, // Ajustar si el servidor envía un radio
                color = ColorUtils.parseColor("#${Integer.toHexString(event.color)}") // Convertir color
            )

            runOnUiThread {
                gameView.updateGameState(JSONObject().apply {
                    put("food", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", food.id)
                            put("x", food.x.toDouble())
                            put("y", food.y.toDouble())
                            put("radius", food.radius.toDouble())
                            put("color", "#${Integer.toHexString(food.color)}")
                        })
                    })
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar evento EvNewFood: ${e.message}")
        }
    }

    private fun handlePlayerMove(event: PlayerMoveEvent) {
        val playerId = event.playerID.toStringUtf8()
        val player = gameView.getPlayer(playerId)

        if (player != null) {
            // Actualizar las coordenadas del jugador
            player.targetX = event.position.x.toFloat()
            player.targetY = event.position.y.toFloat()

            // Validar que las coordenadas estén dentro de los límites del mapa
            if (player.x < 0 || player.x > Constants.DEFAULT_WORLD_WIDTH || player.y < 0 || player.y > Constants.DEFAULT_WORLD_HEIGHT) {
                Log.e(TAG, "Jugador fuera de los límites: ID=$playerId, X=${player.x}, Y=${player.y}")
            }

            // Si es el jugador actual, actualizar la cámara
            if (playerId == gameView.currentPlayerId) {
                gameView.updateCameraPosition()
            }

            runOnUiThread {
                gameView.invalidate() // Redibujar el juego
            }
        } else {
            Log.e(TAG, "Jugador no encontrado: ID=$playerId")
        }
    }

    private fun handlePlayerGrow(event: PlayerGrowEvent) {
        val playerId = event.playerID.toStringUtf8()
        val player = gameView.getPlayer(playerId)
        //val player = gameView.players[playerId]
        if (player != null) {
            player.radius = event.radius.toFloat()
            runOnUiThread {
                gameView.invalidate() // Redibuja el juego
            }
        }
    }

    private fun handleDestroyFood(event: DestroyFoodEvent) {
        val foodId = "${event.position.x},${event.position.y}"
        gameView.removeIfFood(foodId)
        //gameView.foodItems.removeIf { it.id == foodId }
        runOnUiThread {
            gameView.invalidate() // Redibuja el juego
        }
    }

    private fun handleDestroyPlayer(event: DestroyPlayerEvent) {
        val playerId = event.playerID.toStringUtf8()
        gameView.removePlayer(playerId)
        //gameView.players.remove(playerId)
        runOnUiThread {
            gameView.invalidate() // Redibuja el juego
            if (playerId == gameView.currentPlayerId) {
                onPlayerDied()
            }
        }
    }

    private fun handleJoin(event: JoinEvent) {
        val playerId = event.playerID.toStringUtf8()
        val spawnX = event.position.x.toFloat()
        val spawnY = event.position.y.toFloat()

        gameView.updateCurrentPlayerId(playerId)
        val player = Player(
            id = playerId,
            x = spawnX,
            y = spawnY,
            radius = event.radius.toFloat(),
            color = rgbToArgb(event.color),
            skinName = skinName,
            username = userName,
            score = 0
        )
        runOnUiThread {
            gameView.updatePlayers(player)
            gameView.invalidate()
        }
    }

    fun sendEatFood(food: Food) {
        val player = gameView.currentPlayerId?.let { gameView.getPlayer(it) }
        if (player != null) {
            // Suma de áreas: área total = área jugador + área comida
            val playerArea = Math.PI * player.radius * player.radius
            val foodArea = Math.PI * food.radius * food.radius
            val newArea = playerArea + foodArea
            val newRadius = Math.sqrt(newArea / Math.PI).toFloat()
            webSocketClient.sendEatFood(food.x, food.y, newRadius)
            player.score += 1
        }
    }

    fun sendEatPlayer(other: Player) {
        val player = gameView.currentPlayerId?.let { gameView.getPlayer(it) }
        if (player != null) {
            // Suma de áreas: área total = área jugador + área del otro jugador
            val playerArea = Math.PI * player.radius * player.radius
            val otherArea = Math.PI * other.radius * other.radius
            val newArea = playerArea + otherArea
            val newRadius = Math.sqrt(newArea / Math.PI).toFloat()
            webSocketClient.sendEatPlayer(other.id, newRadius)
            player.score += 1
        }
    }

    companion object {
        private const val TAG = "GameActivity"
    }
}