package com.example.rtspplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.rtspplayer.databinding.ActivityMainBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestStoragePermissions()

        val options = arrayListOf("--rtsp-tcp")
        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC)

        binding.PlayBtn.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                playRtspStream(url)
            }
        }

        binding.RecordBtn.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                recordRtspStream(url)
            }
        }

        binding.pipBtn.setOnClickListener {

            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build())
        }
    }

    private fun playRtspStream(rtspURL: String) {
        val media = Media(libVLC, Uri.parse(rtspURL)).apply {
            setHWDecoderEnabled(true, false)
            addOption("network-caching=150")
        }

        mediaPlayer.media = media

        mediaPlayer.attachViews(binding.SurfaceView, null, false, false)

        mediaPlayer.play()
    }


    private fun recordRtspStream(rtspURL: String) {
        val fileName = "recorded_stream_${System.currentTimeMillis()}.ts"
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val outputPath = File(outputDir, fileName).absolutePath

        val media = Media(libVLC, Uri.parse(rtspURL)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":sout=#file{dst=$outputPath}")
            addOption(":sout-keep")
            addOption(":network-caching=150")
        }

        mediaPlayer.media = media
        mediaPlayer.attachViews(binding.SurfaceView, null, false, false)
        mediaPlayer.play()

        Toast.makeText(this, "Recording to app's Movies folder", Toast.LENGTH_SHORT).show()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Hide buttons while in PiP
        if (isInPictureInPictureMode) {
            binding.PlayBtn.visibility = View.GONE
            binding.RecordBtn.visibility = View.GONE
            binding.pipBtn.visibility = View.GONE
            binding.tilUrl.visibility = View.GONE
        } else {
            binding.PlayBtn.visibility = View.VISIBLE
            binding.RecordBtn.visibility = View.VISIBLE
            binding.pipBtn.visibility = View.VISIBLE
            binding.tilUrl.visibility = View.VISIBLE
        }
    }


    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
        }
    }


    @SuppressLint("ObsoleteSdkInt")
    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }

            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        mediaPlayer.release()
        libVLC.release()
    }
}