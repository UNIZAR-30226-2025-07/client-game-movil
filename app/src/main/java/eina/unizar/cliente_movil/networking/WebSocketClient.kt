package eina.unizar.cliente_movil.networking

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class GameWebSocketClient(
    serverUri: URI,
    private val messageHandler: MessageHandler
) : WebSocketClient(serverUri) {

    private val gson = Gson()
    private val TAG = "GameWebSocketClient"

    override fun onOpen(handshakedata: ServerHandshake?) {
        Log.d(TAG, "Conexión establecida con el servidor")
        messageHandler.onConnectionEstablished()
    }

    override fun onMessage(message: String?) {
        message?.let {
            try {
                val jsonObject = gson.fromJson(it, JsonObject::class.java)
                messageHandler.handleMessage(jsonObject)
            } catch (e: Exception) {
                Log.e(TAG, "Error al procesar mensaje: ${e.message}")
            }
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        Log.d(TAG, "Conexión cerrada: $reason (código: $code)")
        messageHandler.onConnectionClosed(code, reason)
    }

    override fun onError(ex: Exception?) {
        Log.e(TAG, "Error en la conexión: ${ex?.message}")
        messageHandler.onError(ex)
    }

    fun sendAction(action: String, data: Any? = null) {
        val message = JsonObject().apply {
            addProperty("action", action)
            if (data != null) {
                when (data) {
                    is String -> addProperty("data", data)
                    is Number -> addProperty("data", data)
                    is Boolean -> addProperty("data", data)
                    else -> add("data", gson.toJsonTree(data))
                }
            }
        }

        val messageStr = gson.toJson(message)
        send(messageStr)
    }

    // Métodos específicos del juego que envían acciones al servidor
    fun joinGame(username: String, roomId: String? = null) {
        val data = JsonObject().apply {
            addProperty("username", username)
            if (roomId != null) {
                addProperty("roomId", roomId)
            }
        }
        sendAction("join", data)
    }

    fun updateDirection(directionX: Float, directionY: Float) {
        val data = JsonObject().apply {
            addProperty("dx", directionX)
            addProperty("dy", directionY)
        }
        sendAction("move", data)
    }

    fun split() {
        sendAction("split")
    }

    fun eject() {
        sendAction("eject")
    }
}