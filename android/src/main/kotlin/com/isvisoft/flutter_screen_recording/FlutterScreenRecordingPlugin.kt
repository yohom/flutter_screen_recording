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
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.io.IOException
import android.graphics.Point
import java.util.Timer
import kotlin.concurrent.schedule

class FlutterScreenRecordingPlugin(
        private val registrar: Registrar
) : MethodCallHandler,
        PluginRegistry.ActivityResultListener {

    val _TAG = "FlutterScreenRecording";

    var mScreenDensity: Int = 0
    var mMediaRecorder: MediaRecorder? = null
    var mProjectionManager: MediaProjectionManager? = null
    var mMediaProjection: MediaProjection? = null
    var mMediaProjectionCallback: MediaProjectionCallback? = null
    var mVirtualDisplay: VirtualDisplay? = null
    var mDisplayWidth: Int = 1280
    var mDisplayHeight: Int = 720
    var storePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + File.separator
    var videoName: String? = ""
    var recordAudio: Boolean? = false;

    val DEFAULT_WARNING_DELAY : Long = 300; // in milliseconds
    var warningDelay: Long = DEFAULT_WARNING_DELAY;

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Boolean {

        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(_TAG, "onActivityResult: Screen record permission granted");

                mMediaProjectionCallback = MediaProjectionCallback()
                mMediaProjection = mProjectionManager?.getMediaProjection(resultCode, data)
                mMediaProjection?.registerCallback(mMediaProjectionCallback, null)

                mVirtualDisplay = createVirtualDisplay()
                Timer("delayTimer", false).schedule(warningDelay) {
                    mMediaRecorder?.start()
                }


                _result.success(true)
            } else {
                Log.d(_TAG,"onActivityResult: Screen record permission NOT granted");
                _result.success(false)
            }
            return true; // result has been handled
        }
        return false
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "startRecordScreen") {
            try {
                _result = result
                mMediaRecorder = MediaRecorder()

                mProjectionManager = registrar.context()
                        .applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?

                videoName = call.argument<String?>("name")
                recordAudio = call.argument<Boolean?>("audio")
                val width = call.argument<Int?>("width");
                val height = call.argument<Int?>("height");
                warningDelay = call.argument<Int?>("delay")?.toLong() ?: DEFAULT_WARNING_DELAY;
                setVideoDimensions(width, height);
                initMediaRecorder();
                startRecordScreen()
                //result.success(true)
            } catch (e: Exception) {
                Log.e(_TAG, "onMethodCall/startRecordScreen: error", e);
                result.success(false)
            }

        } else if (call.method == "stopRecordScreen") {
            try {
                if (mMediaRecorder != null) {
                    stopRecordScreen()
                    result.success("${storePath}${videoName}.mp4")
                } else {
                    result.success("")
                }
            } catch (e: Exception) {
                Log.e(_TAG, "onMethodCall/stopRecordScreen error:", e);
                result.success("")
            }

        } else {
            result.notImplemented()
        }
    }

    fun setVideoDimensions(width: Int?, height: Int?) {

        val windowManager = registrar.context().applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenSize = Point()
        windowManager.defaultDisplay.getRealSize(screenSize); // in pixels

        // Default to the actual screen size, same as on IOS
        mDisplayWidth = width ?: screenSize.x;
        mDisplayHeight = height ?: screenSize.y;

/*        mDisplayWidth = 2560;
        mDisplayHeight = 1440;*/
        Log.d(_TAG, "setVideoDimensions: w x h = $mDisplayWidth x $mDisplayHeight");
    }

    fun initMediaRecorder() {
        mMediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)

        //mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

        if (recordAudio!!) {
            mMediaRecorder?.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            mMediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//AAC //HE_AAC
            mMediaRecorder?.setAudioEncodingBitRate(16 * 44100);
            mMediaRecorder?.setAudioSamplingRate(44100);
        }

        mMediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

        println(mDisplayWidth.toString() + " " + mDisplayHeight);
        mMediaRecorder?.setVideoSize(mDisplayWidth, mDisplayHeight)
        mMediaRecorder?.setVideoFrameRate(30)

        mMediaRecorder?.setOutputFile("${storePath}${videoName}.mp4")

        println("file --- " + "${storePath}${videoName}.mp4")

        mMediaRecorder?.setVideoEncodingBitRate(5 * mDisplayWidth * mDisplayHeight)
        mMediaRecorder?.prepare()
    }

    fun startRecordScreen() {
        try {
            if (mMediaProjection == null) {
                // Ask for permission.
                Log.d(_TAG, "startRecordScreen: asking permission");
                val permissionIntent = mProjectionManager?.createScreenCaptureIntent();
                ActivityCompat.startActivityForResult(
                        registrar.activity(),
                        permissionIntent!!,
                        SCREEN_RECORD_REQUEST_CODE,
                        null /* options bundle*/);
                return; // continues in onActivityResult
            }
            // Else we have permission
            mVirtualDisplay = createVirtualDisplay();
            mMediaRecorder?.start();
            Log.d(_TAG, "startRecordScreen: recording started");
            _result.success(true);
        } catch (e: IOException) {
            Log.e(_TAG, "startRecordScreen: error ", e);
        }


    }

    fun stopRecordScreen() {
        try {
            mMediaRecorder?.stop()
            mMediaRecorder?.reset()
            println("stopRecordScreen success")
        } catch (e: Exception) {
            Log.e( _TAG,"stopRecordScreen: error", e);
        } finally {
            stopScreenSharing()
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        val windowManager = registrar.context().applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics: DisplayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
//        val screenSize = Point()
//        windowManager.defaultDisplay.getRealSize(screenSize);
//        setVideoDimensions(screenSize)
        mScreenDensity = metrics.densityDpi
        println("density " + mScreenDensity.toString())
        println("msurface " + mMediaRecorder?.getSurface())
        println("aaa" + mDisplayWidth.toString() + " " + mDisplayHeight);

        return mMediaProjection?.createVirtualDisplay(
                "FlutterScreenRecording",
                mDisplayWidth,
                mDisplayHeight,
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder?.getSurface(),
                null /* callbacks */,
                null /* Handler */)
    }

    private fun stopScreenSharing() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay?.release()
            if (mMediaProjection != null) {
                mMediaProjection?.unregisterCallback(mMediaProjectionCallback)
                mMediaProjection?.stop()
                mMediaProjection = null
            }
            Log.d(_TAG, "stopScreenSharing: done");
        }
    }

    inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            Log.d("MediaProjectionCallback", "onStop");
            mMediaRecorder?.stop()
            mMediaRecorder?.reset()

            mVirtualDisplay?.release();

            mMediaProjection = null
            stopScreenSharing()
        }
    }

}