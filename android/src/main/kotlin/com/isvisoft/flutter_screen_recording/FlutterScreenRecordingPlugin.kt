package com.isvisoft.flutter_screen_recording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import io.flutter.app.FlutterApplication
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.io.IOException
import android.graphics.Point


class FlutterScreenRecordingPlugin(private val registrar: Registrar) : MethodCallHandler,
    PluginRegistry.ActivityResultListener {

  private var mScreenDensity: Int = 0
  var mMediaRecorder: MediaRecorder? = null
  private var mProjectionManager: MediaProjectionManager? = null
  var mMediaProjection: MediaProjection? = null
  private var mMediaProjectionCallback: MediaProjectionCallback? = null
  private var mVirtualDisplay: VirtualDisplay? = null
  private var mDisplayWidth: Int = 1280
  private var mDisplayHeight: Int = 720
  private var storePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + File.separator
  private var videoName: String? = ""
  private var recordAudio: Boolean? = false
  private val SCREEN_RECORD_REQUEST_CODE = 333
  private val SCREEN_STOP_RECORD_REQUEST_CODE = 334

  private lateinit var _result: MethodChannel.Result

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      val channel = MethodChannel(registrar.messenger(), "flutter_screen_recording")
      val plugin = FlutterScreenRecordingPlugin(registrar)
      channel.setMethodCallHandler(plugin)
      registrar.addActivityResultListener(plugin)
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {

    if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
      if (resultCode == Activity.RESULT_OK) {
        //initMediaRecorder();

        mMediaProjectionCallback = MediaProjectionCallback()
        mMediaProjection = mProjectionManager?.getMediaProjection(resultCode, data!!)
        mMediaProjection?.registerCallback(mMediaProjectionCallback, null)
        mVirtualDisplay = createVirtualDisplay()

        _result.success(true)
        return true
      } else {
        _result.success(false)
      }
    }

    return false
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "startRecordScreen" -> {
        try {
          _result = result
          mMediaRecorder = MediaRecorder()

          mProjectionManager = registrar.context().applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?

          videoName = call.argument<String?>("name")
          recordAudio = call.argument<Boolean?>("audio")
          initMediaRecorder()
          startRecordScreen()
          //result.success(true)
        } catch (e: Exception) {
          println("Error onMethodCall startRecordScreen")
          println(e.message)
          result.success(false)
        }

      }
      "stopRecordScreen" -> {
        try {
          if (mMediaRecorder != null) {
            stopRecordScreen()
            result.success("${storePath}${videoName}.mp4")
          } else {
            result.success("")
          }
        } catch (e: Exception) {
          result.success("")
        }

      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun calculeResolution(screenSize: Point) {

//        val screenRatio: Double = (screenSize.x.toDouble() / screenSize.y.toDouble())
//
//        println(screenSize.x.toString() + " --- " + screenSize.y.toString())
//        var height: Double = mDisplayWidth / screenRatio;
//        println("height - " + height)
//
//        mDisplayHeight = height.toInt()

    // Use the actual screen size, same as on IOS
    mDisplayWidth = screenSize.x
    mDisplayHeight = screenSize.y

/*        mDisplayWidth = 2560;
        mDisplayHeight = 1440;*/

//        println("Scaled Density")
//        //println(metrics.scaledDensity)
//        println("Original Resolution ")
    //println(metrics.widthPixels.toString() + " x " + metrics.heightPixels)
    println("Calcule Resolution ")
    println("$mDisplayWidth x $mDisplayHeight")
  }

  private fun initMediaRecorder() {
    mMediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)

    //mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

    if (recordAudio!!) {
      mMediaRecorder?.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
      mMediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)//AAC //HE_AAC
      mMediaRecorder?.setAudioEncodingBitRate(16 * 44100)
      mMediaRecorder?.setAudioSamplingRate(44100)
    }

    mMediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

    val windowManager = registrar.context().applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val screenSize = Point()
    windowManager.defaultDisplay.getRealSize(screenSize)
    calculeResolution(screenSize)

    println("$mDisplayWidth $mDisplayHeight")
    mMediaRecorder?.setVideoSize(mDisplayWidth, mDisplayHeight)
    mMediaRecorder?.setVideoFrameRate(30)

    mMediaRecorder?.setOutputFile("${storePath}${videoName}.mp4")

    println("file --- " + "${storePath}${videoName}.mp4")

    mMediaRecorder?.setVideoEncodingBitRate(5 * mDisplayWidth * mDisplayHeight)
    mMediaRecorder?.prepare()
  }

  private fun startRecordScreen() {
    try {
      //mMediaRecorder?.prepare()

      mMediaRecorder?.start()

    } catch (e: IOException) {
      println("ERR")
      Log.d("--INIT-RECORDER", e.message ?: "ERROR")
      println("Error startRecordScreen")
      println(e.message)
    }

    val permissionIntent = mProjectionManager?.createScreenCaptureIntent()
    ActivityCompat.startActivityForResult(registrar.activity()!!, permissionIntent!!, SCREEN_RECORD_REQUEST_CODE, null)
  }

  private fun stopRecordScreen() {
    try {

      mMediaRecorder?.stop()
      mMediaRecorder?.reset()

      mMediaRecorder = null
      println("stopRecordScreen success")

    } catch (e: Exception) {
      Log.d("--INIT-RECORDER", e.message ?: "ERROR")
      println("stopRecordScreen error")
      println(e.message)

    } finally {
      stopScreenSharing()
    }
  }

  private fun createVirtualDisplay(): VirtualDisplay? {
    val windowManager = registrar.context().applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics: DisplayMetrics = DisplayMetrics()
    windowManager.defaultDisplay.getMetrics(metrics)
    val screenSize = Point()
    windowManager.defaultDisplay.getRealSize(screenSize)
    calculeResolution(screenSize)
    mScreenDensity = metrics.densityDpi
    println("density $mScreenDensity")
    println("msurface " + mMediaRecorder?.surface)
    println("aaa$mDisplayWidth $mDisplayHeight")

    return mMediaProjection?.createVirtualDisplay("MainActivity", mDisplayWidth, mDisplayHeight, mScreenDensity,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mMediaRecorder?.surface, null, null)
  }

  private fun stopScreenSharing() {
    if (mVirtualDisplay != null) {
      mVirtualDisplay?.release()
      if (mMediaProjection != null) {
        mMediaProjection?.unregisterCallback(mMediaProjectionCallback)
        mMediaProjection?.stop()
        mMediaProjection = null
      }
      Log.d("TAG", "MediaProjection Stopped")
    }
  }

  inner class MediaProjectionCallback : MediaProjection.Callback() {
    override fun onStop() {
      mMediaRecorder?.stop()
      mMediaRecorder?.reset()

      mMediaProjection = null
      stopScreenSharing()
    }
  }

}