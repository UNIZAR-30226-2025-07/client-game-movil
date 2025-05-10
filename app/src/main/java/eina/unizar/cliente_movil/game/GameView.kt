package eina.unizar.cliente_movil.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import eina.unizar.cliente_movil.model.Food
import eina.unizar.cliente_movil.model.Player
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import android.util.Log
import eina.unizar.cliente_movil.utils.ColorUtils
import eina.unizar.cliente_movil.utils.Constants
import kotlin.div
import kotlin.text.get
import kotlin.text.toFloat
import kotlin.times

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private val gameThread: GameThread
    private val playersPaint = Paint()
    private val foodPaint = Paint()
    private val textPaint = Paint()

    private val players = mutableMapOf<String, Player>()
    private val foodItems = mutableListOf<Food>()


    private var gameWidth = 5000f  // Default game world width
    private var gameHeight = 5000f // Default game world height
    private var cameraX = 0f
    private var cameraY = 0f
    private var scale = 1f

    private var joystickX = 0f
    private var joystickY = 0f
    private var joystickPressed = false
    private var moveListener: MoveListener? = null

    public var currentPlayerId: String? = null
        private set

    fun updatePlayers(player: Player){
        players[player.id] = player
    }

    fun removePlayer(playerId: String){
        players.remove(playerId)
    }

    fun getPlayer(playerId: String): Player? {
        return players[playerId]
    }

    fun removeIfFood(id: String){
        foodItems.removeIf { it.id == id }
    }

    init {
        // Setup paints
        playersPaint.isAntiAlias = true
        foodPaint.isAntiAlias = true

        textPaint.color = Color.WHITE
        textPaint.textSize = 30f
        textPaint.textAlign = Paint.Align.CENTER

        // Setup surface holder
        holder.addCallback(this)

        // Create game thread
        gameThread = GameThread(holder, this)

        // Make view focusable to handle events
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        gameThread.running = true
        gameThread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int, ) {
        // Not needed for now
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        gameThread.running = false

        while (retry) {
            try {
                gameThread.join()
                retry = false
            } catch (e: InterruptedException) {
                // Try again
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                joystickPressed = true
                joystickX = event.x
                joystickY = event.y
                calculateMovement()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (joystickPressed) {
                    joystickX = event.x
                    joystickY = event.y
                    calculateMovement()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                joystickPressed = false
                // Stop movement when touch is released
                moveListener?.onMove(0f, 0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun calculateMovement() {
        val player = currentPlayerId?.let { players[it] } ?: return

        // Calculate center of screen
        val centerX = width / 2f
        val centerY = height / 2f

        // Calculate direction vector
        val dirX = joystickX - centerX
        val dirY = joystickY - centerY

        // Calculate distance from center
        val distance = sqrt(dirX * dirX + dirY * dirY)

        if (distance > 10) { // Small threshold to prevent tiny movements
            // Normalize the direction
            val normalizedX = dirX / distance
            val normalizedY = dirY / distance

            // Send movement update to listener
            moveListener?.onMove(normalizedX, normalizedY)
        }
    }

    fun updateGameState(gameState: JSONObject) {
        Log.d("GameView", "Actualizando estado del juego: $gameState")
        try {
            // Update game dimensions if provided
            if (gameState.has("worldSize")) {
                val worldSize = gameState.getJSONObject("worldSize")
                gameWidth = worldSize.getDouble("width").toFloat()
                gameHeight = worldSize.getDouble("height").toFloat()
                Log.d("GameView", "Tamaño del mapa actualizado: Width=$gameWidth, Height=$gameHeight")
            }
            // Update players
            if (gameState.has("players")) {
                val playersArray = gameState.getJSONArray("players")
                val updatedPlayers = mutableMapOf<String, Player>()

                for (i in 0 until playersArray.length()) {
                    val playerJson = playersArray.getJSONObject(i)
                    val playerId = playerJson.getString("id")
                    val player = Player(
                        id = playerId,
                        x = playerJson.getDouble("x").toFloat(),
                        y = playerJson.getDouble("y").toFloat(),
                        radius = playerJson.getDouble("radius").toFloat(),
                        color = Color.parseColor(playerJson.getString("color")),
                        username = playerJson.optString("username", "Player"),
                        score = playerJson.optInt("score", 0)
                    )
                    updatedPlayers[playerId] = player
                }

                // Actualizar solo los jugadores necesarios
                Log.d("GameView", "Jugadores antes de actualizar: ${players.keys}")
                players.keys.retainAll(updatedPlayers.keys)
                players.putAll(updatedPlayers)
            }

            // Update food
            if (gameState.has("food")) {
                val foodArray = gameState.getJSONArray("food")
                val updatedFood = mutableMapOf<String, Food>()

                for (i in 0 until foodArray.length()) {
                    val foodJson = foodArray.getJSONObject(i)
                    val foodId = foodJson.getString("id")
                    val food = Food(
                        id = foodId,
                        x = foodJson.getDouble("x").toFloat(),
                        y = foodJson.getDouble("y").toFloat(),
                        radius = foodJson.getDouble("radius").toFloat(),
                        color = Color.parseColor(foodJson.getString("color"))
                    )
                    updatedFood[foodId] = food
                }

                // Mantener los elementos existentes y agregar los nuevos
                //foodItems.removeIf { it.id !in updatedFood.keys }
                updatedFood.values.forEach { newFood ->
                    //if (foodItems.none { it.id == newFood.id }) {
                        foodItems.add(newFood)
                    //}
                }
            }

            // Actualizar posición de la cámara
            updateCameraPosition()

            Log.d("GameView", "Jugadores: ${players.keys}, Comida: ${foodItems.size}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateCameraPosition() {
        val player = currentPlayerId?.let { players[it] }
        if (player != null) {
            // Centrar la cámara en el jugador
            cameraX = player.x
            cameraY = player.y

            // Ajustar el zoom para que sea más cercano
            scale = max(1f, min(2.5f, 50f / player.radius)) // Aumenta el zoom máximo
        }
    }

    fun updateCurrentPlayerId(playerId: String) {
        currentPlayerId = playerId
    }

    fun setMoveListener(listener: MoveListener) {
        moveListener = listener
    }

    // Dentro de GameView.kt

    fun update() {
        // Movimiento suave de los jugadores hacia su posición objetivo
        for (player in players.values) {
            val dx = player.targetX - player.x
            val dy = player.targetY - player.y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance > 1f) {
                val speed = max(2f, 12f - player.radius / 10f)
                val step = min(speed, distance)
                player.x += dx / distance * step
                player.y += dy / distance * step
            } else {
                player.x = player.targetX
                player.y = player.targetY
            }
        }
        // Centrar la cámara en el jugador actual en cada frame
        updateCameraPosition()

        // --- NUEVO: detectar colisión con comida ---
        val currentPlayer = currentPlayerId?.let { players[it] }
        if (currentPlayer != null) {
            val iterator = foodItems.iterator()
            while (iterator.hasNext()) {
                val food = iterator.next()
                val dist = sqrt((currentPlayer.x - food.x) * (currentPlayer.x - food.x) +
                        (currentPlayer.y - food.y) * (currentPlayer.y - food.y))
                if (dist < currentPlayer.radius + food.radius) {
                    // Enviar petición de comer comida al servidor
                    (context as? eina.unizar.cliente_movil.ui.GameActivity)?.let { activity ->
                        activity.sendEatFood(food)
                    }
                    // Opcional: puedes eliminar la comida localmente si quieres feedback inmediato
                    // iterator.remove()
                }
            }
        }
    }

    fun render(canvas: Canvas) {
        if (canvas != null) {
            // Limpiar el canvas
            canvas.drawColor(Color.BLACK)

            // Guardar el estado actual del canvas
            canvas.save()

            // Traducir el canvas al centro del jugador
            canvas.translate(
                width / 2f - cameraX * scale,
                height / 2f - cameraY * scale
            )

            // Escalar el canvas según el nivel de zoom
            canvas.scale(scale, scale)

            // Dibujar los límites del mapa
            val boundaryPaint = Paint().apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawRect(0f, 0f, Constants.DEFAULT_WORLD_WIDTH.toFloat(), Constants.DEFAULT_WORLD_HEIGHT.toFloat(), boundaryPaint)

            // Dibujar la comida
            for (food in foodItems) {
                foodPaint.color = food.color
                canvas.drawCircle(food.x, food.y, food.radius, foodPaint)
            }

            // Dibujar los jugadores
            for ((id, player) in players) {
                playersPaint.color = player.color
                canvas.drawCircle(player.x, player.y, player.radius, playersPaint)

                // Dibujar el nombre del jugador
                canvas.drawText(
                    player.username,
                    player.x,
                    player.y - player.radius - 10,
                    textPaint
                )
            }

            // Restaurar el estado del canvas
            canvas.restore()

            // Dibujar elementos de la interfaz en el espacio de la pantalla
            if (joystickPressed) {
                val joystickPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawCircle(joystickX, joystickY, 50f, joystickPaint)
            }

            // Dibujar la puntuación del jugador actual
            val currentPlayer = currentPlayerId?.let { players[it] }
            if (currentPlayer != null) {
                canvas.drawText(
                    "Puntuación: ${currentPlayer.score}",
                    width / 2f,
                    50f,
                    textPaint
                )
            }
        }
    }

    interface MoveListener {
        fun onMove(directionX: Float, directionY: Float)
    }

    private class GameThread(
        private val surfaceHolder: SurfaceHolder,
        private val gameView: GameView
    ) : Thread() {

        var running = false
        private val targetFPS = 60
        private val targetFrameTime = 1000 / targetFPS

        override fun run() {
            var startTime: Long
            var timeMillis: Long
            var waitTime: Long

            while (running) {
                startTime = System.currentTimeMillis()
                var canvas: Canvas? = null

                try {
                    canvas = surfaceHolder.lockCanvas()
                    synchronized(surfaceHolder) {
                        gameView.update()
                        if (canvas != null) {
                            gameView.render(canvas)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        if (canvas != null) {
                            surfaceHolder.unlockCanvasAndPost(canvas)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                timeMillis = System.currentTimeMillis() - startTime
                waitTime = targetFrameTime - timeMillis

                if (waitTime > 0) {
                    try {
                        sleep(waitTime)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}