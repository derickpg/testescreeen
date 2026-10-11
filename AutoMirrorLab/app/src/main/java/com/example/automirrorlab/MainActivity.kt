package com.example.automirrorlab

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val captureRequest = 2407

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        layout.addView(TextView(this).apply {
            text = "AutoMirror Lab"
            textSize = 26f
        })
        layout.addView(TextView(this).apply {
            text = "Protótipo experimental de captura e transmissão local da tela. Não integra diretamente com o Android Auto nesta versão."
            textSize = 16f
            setPadding(0, pad / 2, 0, pad)
        })
        status = TextView(this).apply {
            text = "Estado: parado"
            textSize = 16f
        }
        layout.addView(status)
        val start = Button(this).apply {
            text = "Iniciar captura e transmissão"
            setOnClickListener {
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        }
        layout.addView(start, ViewGroup.LayoutParams(-1, -2))
        val stop = Button(this).apply {
            text = "Parar transmissão"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                status.text = "Estado: parado"
            }
        }
        layout.addView(stop, ViewGroup.LayoutParams(-1, -2))
        layout.addView(TextView(this).apply {
            text = "Após iniciar, mantenha o celular e o dispositivo receptor na mesma rede Wi‑Fi. O endereço local será exibido na notificação e no status. Use somente com o veículo estacionado."
            textSize = 14f
            setPadding(0, pad, 0, 0)
        })
        setContentView(layout)
    }

    @Deprecated("Deprecated in Android API, retained for compatibility with Android 13 and earlier")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest) {
            if (resultCode == RESULT_OK && data != null) {
                val service = Intent(this, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_START
                    putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(CaptureService.EXTRA_DATA, data)
                }
                startForegroundService(service)
                status.text = "Estado: iniciando servidor local…"
                Toast.makeText(this, "Captura autorizada", Toast.LENGTH_SHORT).show()
            } else {
                status.text = "Estado: permissão de captura negada"
            }
        }
    }
}
