package eina.unizar.cliente_movil

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import eina.unizar.cliente_movil.databinding.ActivityMainBinding
import eina.unizar.cliente_movil.model.GameState
import eina.unizar.cliente_movil.networking.GameMessageHandler
import eina.unizar.cliente_movil.networking.GameWebSocketClient
import eina.unizar.cliente_movil.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URISyntaxException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val gameState = GameState()
    private var webSocketClient: GameWebSocketClient? = null
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Forzar orientación horizontal
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Configurar GameView
        binding.gameView.setGameState(gameState)
        binding.gameView.setOnDirectionChangeListener { dirX, dirY ->
            webSocketClient?.updateDirection(dirX, dirY)
        }

        // Configurar observadores básicos
        setupObservers()

        // Configurar botones de control
        //setupControlButtons()

        // Conectar automáticamente al servidor
        connectToServer()
    }

    private fun setupObservers() {
        // Observar cambios en el estado de conexión
        gameState.connected.observe(this, Observer { isConnected ->
            if (isConnected) {
                Log.d(TAG, "Conectado al servidor")
            } else {
                Log.d(TAG, "Desconectado del servidor")
                showToast("Desconectado del servidor")
            }
        })

        // Observar puntuación (si mantienes el TextView scoreText en tu layout)
        gameState.score.observe(this, Observer { score ->
            binding.scoreText.text = "Score: $score"
        })
    }

    /*private fun setupControlButtons() {
        // Botón para dividir células
        binding.splitButton.setOnClickListener {
            webSocketClient?.split()
        }

        // Botón para expulsar masa
        binding.ejectButton.setOnClickListener {
            webSocketClient?.eject()
        }
    }*/

    private fun connectToServer() {
        try {
            val serverUri = URI(Constants.SERVER_URL)
            val messageHandler = GameMessageHandler(gameState)
            webSocketClient = GameWebSocketClient(serverUri, messageHandler)

            // Conectar al servidor
            webSocketClient?.connect()

            // Esperar a que se establezca la conexión
            CoroutineScope(Dispatchers.IO).launch {
                var attempts = 0
                while ((webSocketClient?.isOpen != true) && attempts < 10) {
                    Thread.sleep(500)
                    attempts++
                }

                if (webSocketClient?.isOpen != true) {
                    runOnUiThread {
                        showToast("No se pudo conectar al servidor")
                    }
                }
            }
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Error al crear URI: ${e.message}")
            showToast("Error al conectar: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient?.close()
    }
}