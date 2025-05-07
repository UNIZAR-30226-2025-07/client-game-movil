package eina.unizar.cliente_movil.model

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlin.math.max

class GameState {
    private val _players = MutableLiveData<List<Player>>(emptyList())
    val players: LiveData<List<Player>> = _players

    private val _food = MutableLiveData<List<Food>>(emptyList())
    val food: LiveData<List<Food>> = _food

    private val _leaderboard = MutableLiveData<List<Pair<String, Int>>>(emptyList())
    val leaderboard: LiveData<List<Pair<String, Int>>> = _leaderboard

    private val _connected = MutableLiveData(false)
    val connected: LiveData<Boolean> = _connected

    private val _gameOver = MutableLiveData(false)
    val gameOver: LiveData<Boolean> = _gameOver

    private val _score = MutableLiveData(0)
    val score: LiveData<Int> = _score

    private var playerId: String? = null
    private var worldWidth: Int = 5000
    private var worldHeight: Int = 5000

    fun updatePlayers(newPlayers: List<Player>) {
        _players.postValue(newPlayers)
    }

    fun updateFood(newFood: List<Food>) {
        _food.postValue(newFood)
    }

    fun updateLeaderboard(newLeaderboard: List<Pair<String, Int>>) {
        _leaderboard.postValue(newLeaderboard)
    }

    fun setConnected(isConnected: Boolean) {
        _connected.postValue(isConnected)
    }

    fun setGameOver(finalScore: Int) {
        _gameOver.postValue(true)
        _score.postValue(finalScore)
    }

    fun resetGame() {
        _gameOver.postValue(false)
        _score.postValue(0)
    }

    fun setPlayerId(id: String) {
        playerId = id
    }

    fun getPlayerId(): String? = playerId

    fun setWorldDimensions(width: Int, height: Int) {
        worldWidth = width
        worldHeight = height
    }

    fun getWorldWidth(): Int = worldWidth
    fun getWorldHeight(): Int = worldHeight

    fun getPlayerCells(): List<Player> {
        val allPlayers = _players.value ?: emptyList()
        return allPlayers.filter { it.id == playerId }
    }

    fun getPlayerPosition(): Pair<Float, Float> {
        val playerCells = getPlayerCells()
        if (playerCells.isEmpty()) return Pair(worldWidth / 2f, worldHeight / 2f)

        var totalX = 0f
        var totalY = 0f
        var totalMass = 0f

        playerCells.forEach { cell ->
            val mass = max(cell.radius * cell.radius, 1f)
            totalX += cell.x * mass
            totalY += cell.y * mass
            totalMass += mass
        }

        return if (totalMass > 0) {
            Pair(totalX / totalMass, totalY / totalMass)
        } else {
            Pair(worldWidth / 2f, worldHeight / 2f)
        }
    }
}
