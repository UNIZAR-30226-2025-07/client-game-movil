package eina.unizar.cliente_movil.model

data class Player(
    val id: String,
    var x: Float,
    var y: Float,
    var radius: Float,
    var color: Int,
    var username: String,
    var score: Int
)
