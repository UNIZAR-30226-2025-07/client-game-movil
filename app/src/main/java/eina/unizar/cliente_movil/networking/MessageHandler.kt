package eina.unizar.cliente_movil.networking

import android.util.Log
import eina.unizar.cliente_movil.model.Food
import eina.unizar.cliente_movil.model.GameState
import eina.unizar.cliente_movil.model.Player
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

interface MessageHandler {
    fun handleMessage(message: JsonObject)
    fun onConnectionEstablished()
    fun onConnectionClosed(code: Int, reason: String?)
    fun onError(ex: Exception?)
}

class GameMessageHandler(private val gameState: GameState) : MessageHandler {
    private val gson = Gson()
    private val TAG = "GameMessageHandler"

    override fun handleMessage(message: JsonObject) {
        if (!message.has("type")) {
            Log.e(TAG, "Mensaje sin tipo: $message")
            return
        }

        val type = message.get("type").asString

        when (type) {
            "update" -> handleUpdateMessage(message)
            "join_success" -> handleJoinSuccess(message)
            "game_over" -> handleGameOver(message)
            "leaderboard" -> handleLeaderboard(message)
            "error" -> handleErrorMessage(message)
            else -> Log.d(TAG, "Tipo de mensaje desconocido: $type")
        }
    }

    private fun handleUpdateMessage(message: JsonObject) {
        if (message.has("players")) {
            val playersType = object : TypeToken<List<Player>>() {}.type
            val players = gson.fromJson<List<Player>>(message.get("players"), playersType)
            gameState.updatePlayers(players)
        }

        if (message.has("food")) {
            val foodType = object : TypeToken<List<Food>>() {}.type
            val food = gson.fromJson<List<Food>>(message.get("food"), foodType)
            gameState.updateFood(food)
        }

        // Actualizar otras entidades según sea necesario
    }

    private fun handleJoinSuccess(message: JsonObject) {
        val data = message.getAsJsonObject("data")
        val playerId = data.get("id").asString
        gameState.setPlayerId(playerId)

        if (data.has("world")) {
            val width = data.getAsJsonObject("world").get("width").asInt
            val height = data.getAsJsonObject("world").get("height").asInt
            gameState.setWorldDimensions(width, height)
        }

        Log.d(TAG, "Join success - Player ID: $playerId")
    }

    private fun handleGameOver(message: JsonObject) {
        val score = if (message.has("score")) message.get("score").asInt else 0
        gameState.setGameOver(score)
        Log.d(TAG, "Game over - Score: $score")
    }

    private fun handleLeaderboard(message: JsonObject) {
        if (message.has("leaderboard")) {
            val leaderboardType = object : TypeToken<List<Pair<String, Int>>>() {}.type
            val leaderboard = gson.fromJson<List<Pair<String, Int>>>(message.get("leaderboard"), leaderboardType)
            gameState.updateLeaderboard(leaderboard)
        }
    }

    private fun handleErrorMessage(message: JsonObject) {
        val errorMsg = if (message.has("message")) message.get("message").asString else "Unknown error"
        Log.e(TAG, "Error del servidor: $errorMsg")
    }

    override fun onConnectionEstablished() {
        gameState.setConnected(true)
    }

    override fun onConnectionClosed(code: Int, reason: String?) {
        gameState.setConnected(false)
        Log.d(TAG, "Conexión cerrada: $reason (código: $code)")
    }

    override fun onError(ex: Exception?) {
        Log.e(TAG, "Error en la conexión: ${ex?.message}")
    }
}