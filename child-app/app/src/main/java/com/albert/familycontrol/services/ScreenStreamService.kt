package com.albert.familycontrol.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import com.albert.familycontrol.firebase.FirebaseManager
import kotlinx.coroutines.*
import kotlinx.coroutines.currentCoroutineContext
import org.java_websocket.server.WebSocketServer
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class ScreenStreamService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var wsServer: ScreenWsServer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streaming = false

    companion object {
        const val CHANNEL_ID = "fc_stream"
        const val NOTIF_ID = 1003
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val WS_PORT = 8765
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        if (resultCode != -1 && data != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            startCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels / 2   // половина разрешения для экономии трафика
        val height = metrics.heightPixels / 2
        val dpi = metrics.densityDpi / 2

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenStream", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        // WebSocket сервер — родительское приложение подключается к порту 8765
        wsServer = ScreenWsServer(WS_PORT) { ws ->
            streaming = true
            scope.launch { streamFrames(ws, width, height) }
        }
        wsServer?.isReuseAddr = true
        wsServer?.start()
    }

    private suspend fun streamFrames(ws: WebSocket, width: Int, height: Int) {
        while (streaming && currentCoroutineContext().isActive && ws.isOpen) {
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer: ByteBuffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride, height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream)
                    bitmap.recycle()

                    ws.send(stream.toByteArray())
                }
                delay(500)  // 2 FPS — достаточно для мониторинга, экономно по трафику
            } catch (e: Exception) {
                break
            }
        }
        streaming = false
    }

    override fun onDestroy() {
        super.onDestroy()
        streaming = false
        scope.cancel()
        wsServer?.stop()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Трансляция экрана", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Трансляция экрана активна")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).build()
}

class ScreenWsServer(port: Int, private val onClient: (WebSocket) -> Unit) :
    WebSocketServer(InetSocketAddress(port)) {
    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) { onClient(conn) }
    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {}
    override fun onMessage(conn: WebSocket, message: String) {}
    override fun onError(conn: WebSocket?, ex: Exception) {}
    override fun onStart() {}
}
