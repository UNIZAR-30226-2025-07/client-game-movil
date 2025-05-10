package eina.unizar.cliente_movil.utils

object Constants {
    // URL del servidor WebSocket (ajustar según la configuración de tu servidor)
    const val SERVER_URL = "ws://10.0.2.2:8080/ws"  // Para emulador Android
    // const val SERVER_URL = "ws://192.168.1.x:8080/ws"  // Para dispositivo físico en red local

    // Constantes del juego
    const val DEFAULT_WORLD_WIDTH = 10000
    const val DEFAULT_WORLD_HEIGHT = 10000
    const val DEFAULT_PLAYER_RADIUS = 32f

    // Colores disponibles para jugadores
    val PLAYER_COLORS = listOf(
        "#FF4136", "#0074D9", "#2ECC40", "#FFDC00",
        "#B10DC9", "#01FF70", "#FF851B", "#7FDBFF",
        "#F012BE", "#39CCCC", "#85144b", "#3D9970"
    )
}
