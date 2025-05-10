package eina.unizar.cliente_movil.networking

import android.util.Log
import eina.unizar.cliente_movil.utils.Constants
import org.json.JSONObject
import okhttp3.*
import galaxy.Galaxy.Operation
import galaxy.Galaxy.OperationType
import galaxy.Galaxy.MoveOperation
import galaxy.Galaxy.Vector2D
import okio.ByteString

class WebSocketClient(private val serverUrl: String, private val listener: WebSocketListener) {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    fun connect() {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, listener)
        // Nota: OkHttp lanza los callbacks en hilos del dispatcher interno
        Log.d(TAG, "WebSocket: conexión iniciada a $serverUrl")
    }

    /** Envía el movimiento (x,y) serializado en JSON */
    fun sendMovement(x: Float, y: Float) {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket no conectado. No se puede enviar movimiento.")
            return
        }

        val scaledX = ((x + 1) / 2 * Constants.DEFAULT_WORLD_WIDTH).toInt()
        val scaledY = ((y + 1) / 2 * Constants.DEFAULT_WORLD_HEIGHT).toInt()

        Log.d(TAG, "Enviando movimiento escalado: X=$scaledX, Y=$scaledY")

        val moveOperation = Operation.newBuilder()
            .setOperationType(OperationType.OpMove)
            .setMoveOperation(
                MoveOperation.newBuilder()
                    .setPosition(
                        Vector2D.newBuilder()
                            .setX(scaledX)
                            .setY(scaledY)
                            .build()
                    )
                    .build()
            )
            .build()

        val messageBytes = moveOperation.toByteArray()
        val ok = webSocket?.send(ByteString.of(*messageBytes)) ?: false
        if (!ok) Log.e(TAG, "Error al enviar movimiento")
    }


    /** Emite petición de unirse al juego */
    fun joinGame(userName: String) {
        val randomColor = (0xFFFFFF and (Math.random() * 0xFFFFFF).toInt()) // Genera un color aleatorio
        val joinOperation = Operation.newBuilder()
            .setOperationType(OperationType.OpJoin)
            .setJoinOperation(
                galaxy.Galaxy.JoinOperation.newBuilder()
                    .setUsername(userName)
                    .setColor(randomColor) // Asigna el color generado
                    .build()
            )
            .build()

        val messageBytes = joinOperation.toByteArray()
        val ok = webSocket?.send(ByteString.of(*messageBytes)) ?: false
        if (!ok) Log.e(TAG, "Failed to send join operation")
    }

    /** Cierra la conexión limpiamente */
    fun close() {
        webSocket?.close(1000, "Client closing")
        client.dispatcher.executorService.shutdown()
        Log.d(TAG, "WebSocket: conexión cerrada")
    }


    companion object {
        private const val TAG = "WebSocketClient"
    }
}