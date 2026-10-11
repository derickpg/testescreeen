package com.example.automirrorlab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream

class CaptureService : Service() {
    companion object {
        const val ACTION_START = "com.example.automirrorlab.START"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "projectionData"
        private const val CHANNEL_ID = "automirror_capture"
        private const val NOTIFICATION_ID = 41
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var server: ServerSocket? = null
    private var serverThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val latestJpeg = AtomicReference<ByteArray?>(null)
    private var port = 8765

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START && !running.get()) {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, notification("Preparando captura…"))
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            @Suppress("DEPRECATION")
            val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) else intent.getParcelableExtra(EXTRA_DATA)
            if (data == null) { stopSelf(); return START_NOT_STICKY }
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            startCapture()
            startServer()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels.coerceAtMost(1280)
        val height = (metrics.heightPixels * (width.toFloat() / metrics.widthPixels)).toInt().coerceAtLeast(1)
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            var image: Image? = null
            try {
                image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val padded = android.graphics.Bitmap.createBitmap(width + rowPadding / pixelStride, height, android.graphics.Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(buffer)
                val bitmap = android.graphics.Bitmap.createBitmap(padded, 0, 0, width, height)
                val out = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 65, out)
                latestJpeg.set(out.toByteArray())
                bitmap.recycle(); padded.recycle(); out.close()
            } catch (_: Exception) {
            } finally { image?.close() }
        }, android.os.Handler(mainLooper))
        virtualDisplay = projection?.createVirtualDisplay(
            "AutoMirrorLab", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, null
        )
    }

    private fun startServer() {
        running.set(true)
        serverThread = Thread {
            try {
                server = ServerSocket(port)
                updateNotification("Transmissão local ativa: porta $port")
                while (running.get()) {
                    val client = server?.accept() ?: break
                    Thread { handleClient(client) }.start()
                }
            } catch (_: Exception) {
                if (running.get()) updateNotification("Falha ao iniciar servidor local")
            }
        }.apply { name = "AutoMirrorHttpServer"; isDaemon = true; start() }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val input = client.getInputStream().bufferedReader()
            val request = input.readLine() ?: return
            while (true) { val line = input.readLine() ?: break; if (line.isEmpty()) break }
            val out = BufferedOutputStream(client.getOutputStream())
            if (request.startsWith("GET / ") || request.startsWith("GET /index.html")) {
                val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>AutoMirror Lab</title><style>body{margin:0;background:#111;color:#eee;font:16px sans-serif;text-align:center}img{width:100%;height:auto;object-fit:contain}p{padding:8px}</style></head><body><p>AutoMirror Lab — receptor de teste</p><img src="/stream.mjpg"><p>Use apenas com o veículo estacionado.</p></body></html>""".toByteArray(Charsets.UTF_8)
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(html); out.flush()
            } else if (request.startsWith("GET /stream.mjpg")) {
                out.write("HTTP/1.1 200 OK\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n".toByteArray()); out.flush()
                while (running.get() && !client.isClosed) {
                    val frame = latestJpeg.get()
                    if (frame != null) {
                        out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n".toByteArray())
                        out.write(frame); out.write("\r\n".toByteArray()); out.flush()
                    }
                    Thread.sleep(100)
                }
            } else {
                val body = "Not found".toByteArray()
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(body); out.flush()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AutoMirror capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("AutoMirror Lab").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_view).setOngoing(true).build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    override fun onDestroy() {
        running.set(false)
        try { server?.close() } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        server = null; virtualDisplay = null; reader = null; projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
