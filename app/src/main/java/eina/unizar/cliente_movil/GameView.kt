package eina.unizar.cliente_movil

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import eina.unizar.cliente_movil.model.Food
import eina.unizar.cliente_movil.model.GameState
import eina.unizar.cliente_movil.model.Player
import eina.unizar.cliente_movil.utils.ColorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private val gameThread: GameThread
    private val circlePaint = Paint()
    private val gridPaint = Paint()
    private val textPaint = Paint()

    private var gameState: GameState? = null
    private var onDirectionChangeListener: ((Float, Float) -> Unit)? = null

    // Variables de control táctil
    private var touchX: Float = 0f
    private var touchY: Float = 0f
    private var isTouching: Boolean = false

    // Factor de zoom de la cámara
    private var zoomFactor: Float = 1f

    init {
        holder.addCallback(this)
        gameThread = GameThread(holder, this)

        // Configurar paint para círculos
        circlePaint.isAntiAlias = true

        // Configurar paint para la cuadrícula
        gridPaint.color = Color.parseColor("#DDDDDD")
        gridPaint.strokeWidth = 1f
        gridPaint.style = Paint.Style.STROKE

        // Configurar paint para texto
        textPaint.color = Color.WHITE
        textPaint.textSize = 30f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isAntiAlias = true
    }

    fun setGameState(state: GameState) {
        this.gameState = state
    }

    fun setOnDirectionChangeListener(listener: (Float, Float) -> Unit) {
        this.onDirectionChangeListener = listener
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        gameThread.running = true
        gameThread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // No necesitamos hacer nada aquí
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        var retry = true
        gameThread.running = false
        while (retry) {
            try {
                gameThread.join()
                retry = false
            } catch (e: InterruptedException) {
                // Intentar de nuevo
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                isTouching = true
                calculateDirection()
            }

            MotionEvent.ACTION_UP -> {
                isTouching = false
                onDirectionChangeListener?.invoke(0f, 0f)
            }
        }
        return true
    }

    private fun calculateDirection() {
        val state = gameState ?: return

        // Calcular el centro de la pantalla (donde está el jugador)
        val centerX = width / 2f
        val centerY = height / 2f

        // Calcular el vector de dirección
        val dirX = touchX - centerX
        val dirY = touchY - centerY

        // Normalizar si es necesario
        val length = kotlin.math.sqrt(dirX * dirX + dirY * dirY)
        if (length > 0) {
            val normalizedX = dirX / length
            val normalizedY = dirY / length
            onDirectionChangeListener?.invoke(normalizedX, normalizedY)
        }
    }

    fun update() {
        // Actualizar la lógica del juego aquí si es necesario
        // Por ejemplo, actualizar la animación de las células
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val state = gameState ?: return

        // Limpiar el canvas
        canvas.drawColor(Color.parseColor("#F0F0F0"))

        // Obtener la posición del jugador para centrar la cámara
        val (playerX, playerY) = state.getPlayerPosition()

        // Calcular el radio de la célula del jugador para el zoom
        val playerCells = state.getPlayerCells()
        val maxRadius = playerCells.maxOfOrNull { it.radius } ?: 30f

        // Ajustar zoom basado en el tamaño del jugador
        zoomFactor = min(8f, 64f / maxRadius)

        // Dibujar grid
        drawGrid(canvas, playerX, playerY)

        // Calcular la traslación de la cámara
        val translateX = width / 2f - playerX * zoomFactor
        val translateY = height / 2f - playerY * zoomFactor

        // Dibujar comida
        drawFood(canvas, translateX, translateY)

        // Dibujar jugadores
        drawPlayers(canvas, translateX, translateY)

        // Dibujar interfaz de usuario
        drawUI(canvas)
    }

    private fun drawGrid(canvas: Canvas, playerX: Float, playerY: Float) {
        val state = gameState ?: return
        val worldWidth = state.getWorldWidth()
        val worldHeight = state.getWorldHeight()

        val gridSize = 100f

        // Calcular límites de la cuadrícula visible
        val translateX = width / 2f - playerX * zoomFactor
        val translateY = height / 2f - playerY * zoomFactor

        val startX = ((-(translateX / zoomFactor) / gridSize).toInt() - 1) * gridSize
        val endX =
            ((-(translateX / zoomFactor) + width / zoomFactor) / gridSize + 1).toInt() * gridSize
        val startY = ((-(translateY / zoomFactor) / gridSize).toInt() - 1) * gridSize
        val endY =
            ((-(translateY / zoomFactor) + height / zoomFactor) / gridSize + 1).toInt() * gridSize

        // Dibujar líneas verticales
        var x = startX
        while (x <= endX) {
            if (x.toInt() in 0..worldWidth.toInt()) {
                val screenX = x * zoomFactor + translateX
                canvas.drawLine(screenX, 0f, screenX, height.toFloat(), gridPaint)
            }
            x += gridSize
        }

        // Dibujar líneas horizontales
        var y = startY
        while (y <= endY) {
            if (y.toInt() in 0..worldHeight.toInt()) {
                val screenY = y * zoomFactor + translateY
                canvas.drawLine(0f, screenY, width.toFloat(), screenY, gridPaint)
            }
            y += gridSize
        }
    }

    private fun drawFood(canvas: Canvas, translateX: Float, translateY: Float) {
        val state = gameState ?: return
        val foodItems = state.food.value ?: emptyList()

        for (food in foodItems) {
            val screenX = food.x * zoomFactor + translateX
            val screenY = food.y * zoomFactor + translateY
            val screenRadius = food.radius * zoomFactor

            // Solo dibujar si está en pantalla
            if (isOnScreen(screenX, screenY, screenRadius)) {
                circlePaint.color = Color.parseColor(food.color)
                canvas.drawCircle(screenX, screenY, screenRadius, circlePaint)
            }
        }
    }

    private fun drawPlayers(canvas: Canvas, translateX: Float, translateY: Float) {
        val state = gameState ?: return
        val players = state.players.value ?: emptyList()

        for (player in players) {
            val screenX = player.x * zoomFactor + translateX
            val screenY = player.y * zoomFactor + translateY
            val screenRadius = player.radius * zoomFactor

            // Solo dibujar si está en pantalla
            if (isOnScreen(screenX, screenY, screenRadius)) {
                // Dibujar célula del jugador
                circlePaint.color = Color.parseColor(player.color)
                canvas.drawCircle(screenX, screenY, screenRadius, circlePaint)

                // Dibujar borde para el jugador actual
                if (player.isSelf || player.id == state.getPlayerId()) {
                    circlePaint.style = Paint.Style.STROKE
                    circlePaint.strokeWidth = 3f
                    circlePaint.color = Color.BLACK
                    canvas.drawCircle(screenX, screenY, screenRadius, circlePaint)
                    circlePaint.style = Paint.Style.FILL
                }

                // Dibujar nombre del jugador
                val textY = screenY - screenRadius - 10
                textPaint.color = Color.BLACK
                canvas.drawText(player.username, screenX, textY, textPaint)
            }
        }
    }

    private fun drawUI(canvas: Canvas) {
        val state = gameState ?: return

        // Dibujar puntuación
        val score = calculatePlayerScore()
        canvas.drawText("Score: $score", width / 2f, 50f, textPaint)

        // Dibujar leaderboard
        drawLeaderboard(canvas)
    }

    private fun drawLeaderboard(canvas: Canvas) {
        val state = gameState ?: return
        val leaderboard = state.leaderboard.value ?: emptyList()

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Leaderboard", width - 200f, 50f, textPaint)

        for ((index, entry) in leaderboard.withIndex().take(5)) {
            val (name, score) = entry
            val y = 80f + index * 30f
            canvas.drawText("${index + 1}. $name: $score", width - 200f, y, textPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun calculatePlayerScore(): Int {
        val state = gameState ?: return 0
        val playerCells = state.getPlayerCells()

        return playerCells.sumOf { (it.radius * it.radius).toInt() }
    }

    private fun isOnScreen(x: Float, y: Float, radius: Float): Boolean {
        return x + radius >= 0 && x - radius <= width &&
                y + radius >= 0 && y - radius <= height
    }

    inner class GameThread(
        private val surfaceHolder: SurfaceHolder,
        private val gameView: GameView
    ) : Thread() {

        var running: Boolean = false
            set(value) {
                isRunning.set(value)
                field = value
            }

        private val isRunning = AtomicBoolean(false)
        private val targetFPS = 60
        private val targetTime = 1000 / targetFPS

        override fun run() {
            var startTime: Long
            var timeMillis: Long
            var waitTime: Long

            while (isRunning.get()) {
                startTime = System.nanoTime()
                var canvas: Canvas? = null

                try {
                    canvas = surfaceHolder.lockCanvas()
                    synchronized(surfaceHolder) {
                        gameView.update()
                        if (canvas != null) {
                            gameView.draw(canvas)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas ?: continue)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                timeMillis = (System.nanoTime() - startTime) / 1000000
                waitTime = targetTime - timeMillis

                try {
                    if (waitTime > 0) {
                        sleep(waitTime)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}