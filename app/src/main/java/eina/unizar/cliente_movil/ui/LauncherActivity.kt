package eina.unizar.cliente_movil.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import eina.unizar.cliente_movil.R

/**
 * Actividad de inicio para pruebas
 * En una aplicación real, esta funcionalidad sería manejada por otro repositorio según mencionaste
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var etServerUrl: EditText
    private lateinit var etUserName: EditText
    private lateinit var btnPlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServerUrl = findViewById(R.id.etServerUrl)
        etUserName = findViewById(R.id.etUserName)
        btnPlay = findViewById(R.id.btnPlay)

        // Valores por defecto para pruebas
        etServerUrl.setText("ws://10.0.2.2:8080/ws")  // localhost para el emulador de Android
        etUserName.setText("Player${(1000..9999).random()}")

        btnPlay.setOnClickListener {
            startGame()
        }
    }

    private fun startGame() {
        val serverUrl = etServerUrl.text.toString().trim()
        val userName = etUserName.text.toString().trim()

        if (serverUrl.isEmpty() || userName.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        // Generar un ID de usuario simple para propósitos de prueba
        val userId = "user_${System.currentTimeMillis()}"

        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("serverUrl", serverUrl)
            putExtra("userName", userName)
            putExtra("userId", userId)
        }

        startActivity(intent)
    }
}