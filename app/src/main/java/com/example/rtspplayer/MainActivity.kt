package com.example.rtspplayer

import android.Manifest
import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.res.Configuration
import androidx.media3.common.MediaItem
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer
import com.example.rtspplayer.databinding.ActivityMainBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var libVLC: LibVLC
    private lateinit var vlcMediaPlayer: MediaPlayer
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestStoragePermissions()


        //ExoPlayer for streaming
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            binding.SurfaceView.player = this
        }

        // for recording
        libVLC = LibVLC(this, arrayListOf("--rtsp-tcp"))
        vlcMediaPlayer = MediaPlayer(libVLC)


        binding.PlayBtn.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                playRtspStream(url)
            } else {
                showToast("Enter a valid RTSP URL")
            }
        }

        binding.RecordBtn.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                if (isRecording) {
                    stopRecording()
                } else {
                    recordRtspStream(url)
                }
            } else {
                showToast("Enter a valid RTSP URL")
            }
        }

        binding.pipBtn.setOnClickListener {
            enterPipMode()
        }
    }

    private fun playRtspStream(rtspUrl: String) {
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            val mediaItem = MediaItem.fromUri(Uri.parse(rtspUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            Log.d("ExoPlayer", "Playing RTSP stream: $rtspUrl")
            showToast("Playing stream")

        } catch (e: Exception) {
            Log.e("ExoPlayer", "Playback error: ${e.message}", e)
            showToast("Playback error: ${e.message}")
        }
    }

    private fun recordRtspStream(rtspUrl: String) {
        vlcMediaPlayer.stop()
        vlcMediaPlayer.detachViews()


        val fileName = "recorded_stream_${System.currentTimeMillis()}.mp4"
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (outputDir != null && !outputDir.exists()) {
            val created = outputDir.mkdirs()
            Log.d("Recording", "Directory created: $created")
        }
        val outputPath = File(outputDir, fileName).absolutePath
        Log.d("Recording", "Saving to: $outputPath")

        try {
            // Configure media for recording
            val media = Media(libVLC, Uri.parse(rtspUrl)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":sout=#file{dst=\"$outputPath\",mux=mp4}")
                addOption(":sout-keep")
                addOption(":network-caching=150")
            }
            vlcMediaPlayer.media = media
            vlcMediaPlayer.play()

            isRecording = true
            binding.RecordBtn.text = "Stop Recording"
            showToast("Recording started: $fileName")

            // Monitor recording events
            vlcMediaPlayer.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        Log.d("Recording", "Recording started")
                    }

                    MediaPlayer.Event.EndReached -> {
                        Log.d("Recording", "Stream ended")
                        stopRecording()
                    }

                    MediaPlayer.Event.EncounteredError -> {
                        Log.e("Recording", "Recording error")
                        showToast("Recording failed")
                        stopRecording()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Recording", "Exception: ${e.message}", e)
            showToast("Recording failed: ${e.message}")
            isRecording = false
            binding.RecordBtn.text = "Record Stream"
        }
    }

    private fun stopRecording() {
        vlcMediaPlayer.stop()
        vlcMediaPlayer.detachViews()
        isRecording = false
        binding.RecordBtn.text = "Record Stream"
        showToast("Recording stopped")
        Log.d("Recording", "Recording stopped")
    }

    private fun enterPipMode() {
        try {
            val width = binding.surfaceContainer.width
            val height = binding.surfaceContainer.height
            val aspectRatio =
                if (width > 0 && height > 0) Rational(width, height) else Rational(16, 9)

            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(pipParams)
            Log.d("PiP", "Entered PiP mode")

        } catch (e: Exception) {
            Log.e("PiP", "PiP error: ${e.message}", e)
            showToast("PiP error: ${e.message}")
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        binding.PlayBtn.visibility = visibility
        binding.RecordBtn.visibility = visibility
        binding.pipBtn.visibility = visibility
        binding.tilUrl.visibility = visibility
        Log.d("PiP", "PiP mode changed: $isInPictureInPictureMode")
    }

    override fun onStop() {
        super.onStop()
        exoPlayer.pause()
        Log.d("Lifecycle", "onStop: Paused ExoPlayer")
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        if (isRecording) {
            vlcMediaPlayer.stop()
        }
        vlcMediaPlayer.release()
        libVLC.release()
        Log.d("Lifecycle", "onDestroy: Released resources")
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.values.all { it }) {
            showToast("Permissions not granted")
            Log.w("Permissions", "Some permissions denied")
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            else -> {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}