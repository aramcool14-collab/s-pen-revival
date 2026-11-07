package com.spenrevival

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class CameraActivity : Activity(), TextureView.SurfaceTextureListener {
    private lateinit var preview: TextureView
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val lock = Semaphore(1)
    private var cameraId: String? = null
    private var sensorRect: Rect? = null
    private var maxZoom = 1f
    private var zoom = 1f
    private var flash = false
    private var exposure = 0
    private var exposureRange: android.util.Range<Int> = android.util.Range(0, 0)
    private var controls: LinearLayout? = null
    private var zoomLabel: TextView? = null
    private var flashButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        buildUi()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (preview.isAvailable) openCamera() else preview.surfaceTextureListener = this
        } else requestPermissions(arrayOf(Manifest.permission.CAMERA), 42)
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = TextureView(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0x66000000)
        }
        val back = Button(this).apply { text = "‹"; textSize = 28f; setOnClickListener { finish() } }
        val title = TextView(this).apply {
            text = "S PEN CAMERA"; textSize = 14f; setTextColor(Color.WHITE)
        }
        top.addView(back, LinearLayout.LayoutParams(dp(54), dp(48)))
        top.addView(title)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(20))
            setBackgroundColor(0x99000000.toInt())
        }
        val tools = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        flashButton = toolButton("Flash off") { flash = !flash; flashButton?.text = if (flash) "Flash on" else "Flash off"; updatePreview() }
        val switch = toolButton("Switch") { switchCamera() }
        val timer = toolButton("3 sec") { android.os.Handler(mainLooper).postDelayed({ capturePhoto() }, 3000); Toast.makeText(this@CameraActivity, "Photo in 3 seconds", Toast.LENGTH_SHORT).show() }
        tools.addView(flashButton); tools.addView(switch); tools.addView(timer)
        bottom.addView(tools)
        zoomLabel = TextView(this).apply { text = "1.0×"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
        bottom.addView(zoomLabel)
        val zoomBar = SeekBar(this).apply {
            max = 100
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    zoom = 1f + (maxZoom - 1f) * progress / 100f
                    zoomLabel?.text = String.format("%.1f×", zoom)
                    if (fromUser) updatePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        bottom.addView(zoomBar, LinearLayout.LayoutParams(-1, dp(34)))
        val exposureTitle = TextView(this).apply { text = "Exposure"; textSize = 12f; setTextColor(Color.LTGRAY) }
        bottom.addView(exposureTitle)
        val exposureBar = SeekBar(this).apply {
            max = 12; progress = 6
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    exposure = p - 6
                    if (fromUser) updatePreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        bottom.addView(exposureBar, LinearLayout.LayoutParams(-1, dp(30)))
        val captureRow = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        val shutter = Button(this).apply {
            text = "●"; textSize = 30f; setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4285F4.toInt()))
            setOnClickListener { capturePhoto() }
        }
        captureRow.addView(shutter, LinearLayout.LayoutParams(dp(76), dp(76)))
        bottom.addView(captureRow)
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        controls = bottom
        setContentView(root)
        preview.surfaceTextureListener = this
    }

    private fun toolButton(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 12f; setOnClickListener { click() }
    }

    private fun startThread() {
        thread = HandlerThread("SpenCamera").also { it.start() }
        handler = Handler(thread!!.looper)
    }

    private fun openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        startThread()
        try {
            val id = cameraId ?: cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return
            cameraId = id
            val chars = cameraManager.getCameraCharacteristics(id)
            sensorRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            maxZoom = (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceAtLeast(1f)
            exposureRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: android.util.Range(0, 0)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(SurfaceTexture::class.java)?.maxByOrNull { it.width * it.height }
                ?: android.util.Size(1280, 720)
            reader = ImageReader.newInstance(size.width, size.height, android.graphics.ImageFormat.JPEG, 2)
            reader?.setOnImageAvailableListener({ imageReader ->
                val image = imageReader.acquireNextImage() ?: return@setOnImageAvailableListener
                val bytes = image.planes[0].buffer.let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }
                image.close()
                savePhoto(bytes)
            }, handler)
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; createPreviewSession() }
                override fun onDisconnected(device: CameraDevice) { device.close(); camera = null }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close(); camera = null
                    runOnUiThread { Toast.makeText(this@CameraActivity, "Camera error: $error", Toast.LENGTH_LONG).show() }
                }
            }, handler)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera unavailable: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createPreviewSession() {
        val texture = preview.surfaceTexture ?: return
        val device = camera ?: return
        val size = reader?.surface ?: return
        texture.setDefaultBufferSize(preview.width.coerceAtLeast(640), preview.height.coerceAtLeast(480))
        val surface = Surface(texture)
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            }
            device.createCaptureSession(listOf(surface, size), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try { s.setRepeatingRequest(request.build(), null, handler) } catch (_: Exception) { }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    runOnUiThread { Toast.makeText(this@CameraActivity, "Preview setup failed", Toast.LENGTH_SHORT).show() }
                }
            }, handler)
        } catch (e: Exception) {
            Toast.makeText(this, "Preview error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePreview() {
        val device = camera ?: return
        val s = session ?: return
        val texture = preview.surfaceTexture ?: return
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(Surface(texture))
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, if (flash) CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH else CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposure.coerceIn(exposureRange.lower, exposureRange.upper))
                setZoom(this)
            }
            s.setRepeatingRequest(request.build(), null, handler)
        } catch (_: Exception) { }
    }

    private fun setZoom(builder: CaptureRequest.Builder) {
        val rect = sensorRect ?: return
        val factor = zoom.coerceIn(1f, maxZoom)
        val cropW = (rect.width() / factor).roundToInt()
        val cropH = (rect.height() / factor).roundToInt()
        val left = rect.centerX() - cropW / 2
        val top = rect.centerY() - cropH / 2
        builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + cropW, top + cropH))
    }

    private fun capturePhoto() {
        val device = camera ?: return
        val s = session ?: return
        val output = reader?.surface ?: return
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(output)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, if (flash) CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH else CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.JPEG_ORIENTATION, 90)
                setZoom(this)
            }
            s.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    runOnUiThread { Toast.makeText(this@CameraActivity, "Photo captured!", Toast.LENGTH_SHORT).show() }
                }
            }, handler)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not take photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePhoto(bytes: ByteArray) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Spen_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/S Pen Revival")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        } catch (_: Exception) {
            runOnUiThread { Toast.makeText(this, "Could not save photo", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun switchCamera() {
        val current = cameraId ?: return
        val facing = cameraManager.getCameraCharacteristics(current).get(CameraCharacteristics.LENS_FACING)
        val next = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) != facing
        } ?: return
        closeCamera()
        cameraId = next
        openCamera()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { openCamera() }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
        else { Toast.makeText(this, "Camera permission is needed for the camera screen", Toast.LENGTH_LONG).show(); finish() }
    }

    private fun closeCamera() {
        try { session?.close(); session = null; camera?.close(); camera = null; reader?.close(); reader = null } catch (_: Exception) { }
        thread?.quitSafely()
        try { thread?.join(500) } catch (_: InterruptedException) { }
        thread = null; handler = null
    }

    override fun onPause() { closeCamera(); super.onPause() }
    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized && preview.isAvailable && camera == null &&
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
