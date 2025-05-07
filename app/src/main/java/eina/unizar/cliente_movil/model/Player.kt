package eina.unizar.cliente_movil.model

data class Player(
    val id: String,
    val username: String,
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: String,
    val isSelf: Boolean = false
)
