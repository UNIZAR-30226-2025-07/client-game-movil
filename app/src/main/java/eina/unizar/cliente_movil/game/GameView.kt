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
            }

            // Update players
            if (gameState.has("players")) {
                val playersArray = gameState.getJSONArray("players")
                val currentPlayerIds = mutableSetOf<String>()

                for (i in 0 until playersArray.length()) {
                    val playerData = playersArray.getJSONObject(i)
                    val id = playerData.getString("id")
                    currentPlayerIds.add(id)

                    val x = playerData.getDouble("x").toFloat()
                    val y = playerData.getDouble("y").toFloat()
                    val radius = playerData.getDouble("radius").toFloat()
                    val color = playerData.optString("color", "#FF0000")
                    val username = playerData.optString("username", "Player")
                    val score = playerData.optInt("score", 0)

                    if (players.containsKey(id)) {
                        // Update existing player
                        players[id]?.apply {
                            this.x = x
                            this.y = y
                            this.radius = radius
                            this.color = Color.parseColor(color)
                            this.username = username
                            this.score = score
                        }
                    } else {
                        // Add new player
                        players[id] = Player(
                            id = id,
                            x = x,
                            y = y,
                            radius = radius,
                            color = Color.parseColor(color),
                            username = username,
                            score = score
                        )
                    }
                }

                // Remove players not in the current update
                val toRemove = players.keys.filter { it !in currentPlayerIds }
                toRemove.forEach { players.remove(it) }
            }

            // Update food
            if (gameState.has("food")) {
                val foodArray = gameState.getJSONArray("food")
                val updatedFood = mutableListOf<Food>()

                for (i in 0 until foodArray.length()) {
                    val foodData = foodArray.getJSONObject(i)
                    val id = foodData.getString("id")
                    val x = foodData.getDouble("x").toFloat()
                    val y = foodData.getDouble("y").toFloat()
                    val radius = foodData.getDouble("radius").toFloat()
                    val color = ColorUtils.parseColor(foodData.optString("color", "#00FF00"))

                    updatedFood.add(Food(id, x, y, radius, color))
                }

                // Actualiza solo los elementos necesarios
                foodItems.clear()
                foodItems.addAll(updatedFood)
            }

            // Update camera position if current player exists
            updateCameraPosition()

            Log.d("GameView", "Jugadores: ${players.keys}, Comida: ${foodItems.size}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCameraPosition() {
        val player = currentPlayerId?.let { players[it] }
        if (player != null) {
            // Center camera on the player
            cameraX = player.x
            cameraY = player.y

            // Calculate zoom based on player size
            // Larger players see more of the map (zoom out)
            scale = max(0.5f, min(1.5f, 30f / player.radius))
        }
    }

    fun updateCurrentPlayerId(playerId: String) {
        currentPlayerId = playerId
    }

    fun setMoveListener(listener: MoveListener) {
        moveListener = listener
    }

    fun update() {
        // Any additional updates that need to happen each frame
    }

    fun render(canvas: Canvas) {
        if (canvas != null) {
            // Clear canvas
            canvas.drawColor(Color.BLACK)

            // Save the current canvas state
            canvas.save()

            // Calculate viewport dimensions
            val viewportWidth = width / scale
            val viewportHeight = height / scale

            // Translate canvas to player center
            canvas.translate(
                width / 2f - cameraX * scale,
                height / 2f - cameraY * scale
            )

            // Scale canvas according to zoom level
            canvas.scale(scale, scale)

            // Draw game boundaries
            val boundaryPaint = Paint().apply {
                color = Color.DKGRAY
                style = Paint.Style.STROKE
                strokeWidth = 10f
            }
            canvas.drawRect(0f, 0f, gameWidth, gameHeight, boundaryPaint)

            // Draw grid lines
            val gridPaint = Paint().apply {
                color = Color.DKGRAY
                strokeWidth = 2f
                alpha = 100
            }

            val gridSize = 200f
            val startX = (cameraX - viewportWidth / 2).coerceAtLeast(0f)
            val endX = (cameraX + viewportWidth / 2).coerceAtMost(gameWidth)
            val startY = (cameraY - viewportHeight / 2).coerceAtLeast(0f)
            val endY = (cameraY + viewportHeight / 2).coerceAtMost(gameHeight)

            var x = (startX / gridSize).toInt() * gridSize
            while (x <= endX) {
                canvas.drawLine(x, startY, x, endY, gridPaint)
                x += gridSize
            }

            var y = (startY / gridSize).toInt() * gridSize
            while (y <= endY) {
                canvas.drawLine(startX, y, endX, y, gridPaint)
                y += gridSize
            }

            // Draw food
            for (food in foodItems) {
                foodPaint.color = food.color
                canvas.drawCircle(food.x, food.y, food.radius, foodPaint)
            }

            // Draw players
            for ((id, player) in players) {
                playersPaint.color = player.color
                canvas.drawCircle(player.x, player.y, player.radius, playersPaint)

                // Draw player name and score
                val text = "${player.username} (${player.score})"
                canvas.drawText(text, player.x, player.y - player.radius - 10, textPaint)

                // Highlight current player
                if (id == currentPlayerId) {
                    val highlightPaint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                    }
                    canvas.drawCircle(player.x, player.y, player.radius + 2, highlightPaint)
                }
            }

            // Restore canvas state
            canvas.restore()

            // Draw UI elements in screen space if needed
            // For example, score display, joystick, etc.
            if (joystickPressed) {
                val joystickPaint = Paint().apply {
                    color = Color.WHITE
                    alpha = 100
                }
                canvas.drawCircle(joystickX, joystickY, 50f, joystickPaint)
            }

            // Draw score for current player
            val currentPlayer = currentPlayerId?.let { players[it] }
            if (currentPlayer != null) {
                val scoreText = "Score: ${currentPlayer.score}"
                val scorePaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 40f
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText(scoreText, 20f, 60f, scorePaint)
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