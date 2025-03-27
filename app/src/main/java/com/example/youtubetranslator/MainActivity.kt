package com.example.youtubetranslator

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var translator: Translator
    private lateinit var playerView: PlayerView
    private lateinit var youtubeLinkInput: EditText
    private lateinit var subtitlesView: TextView
    private lateinit var playButton: Button
    
    private var subtitleJob: Job? = null
    private val sampleEnglishPhrases = arrayOf(
        "Welcome to this video",
        "Today we will discuss important topics",
        "Thank you for watching this content",
        "Don't forget to subscribe",
        "Let me show you how this works",
        "This is a demonstration of our app",
        "The main features include video playback",
        "We also provide real-time translation",
        "This technology can be very useful",
        "We hope you enjoy using our application"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views
        playerView = findViewById(R.id.videoPlayer)
        youtubeLinkInput = findViewById(R.id.youtubeLinkInput)
        subtitlesView = findViewById(R.id.subtitlesView)
        playButton = findViewById(R.id.playButton)
        
        // Set up the YouTube link input field
        youtubeLinkInput.apply {
            isEnabled = true
            isFocusableInTouchMode = true
            isFocusable = true
            
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: Editable?) {
                    // You can add validation logic here if needed
                }
            })
        }
        
        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        
        // Set up player listeners
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@MainActivity,
                    "Error playing video: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("YouTubeTranslator", "Player error", error)
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stopSubtitleGeneration()
                } else if (playbackState == Player.STATE_READY) {
                    startSubtitleGeneration()
                }
            }
        })
        
        // Initialize ML Kit Translator (English to Russian)
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
        translator = Translation.getClient(options)
        
        // Download translation model if needed
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                Log.d("YouTubeTranslator", "Translation model downloaded successfully")
            }
            .addOnFailureListener { exception ->
                Log.e("YouTubeTranslator", "Error downloading translation model", exception)
                Toast.makeText(
                    this,
                    "Failed to download translation model. Please check your internet connection.",
                    Toast.LENGTH_LONG
                ).show()
            }
        
        // Set up play button click listener
        playButton.setOnClickListener {
            val link = youtubeLinkInput.text.toString().trim()
            if (link.isNotEmpty()) {
                if (link.contains("youtube.com") || link.contains("youtu.be")) {
                    playVideo(link)
                } else {
                    Toast.makeText(this, "Please enter a valid YouTube link", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a YouTube link", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun playVideo(link: String) {
        // Extract video ID from YouTube link
        val videoId = extractVideoId(link)
        if (videoId.isEmpty()) {
            Toast.makeText(this, "Could not extract video ID from link", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create a URL for the video
        // Note: This is a simplified approach and might not work for all videos
        // In a real implementation, a server-side solution would be better
        val streamUrl = "https://www.youtube.com/watch?v=$videoId"
        
        // Prepare and play the video
        val mediaItem = MediaItem.fromUri(streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        
        // Reset and start subtitle generation
        stopSubtitleGeneration()
        startSubtitleGeneration()
    }
    
    private fun extractVideoId(url: String): String {
        val pattern = Pattern.compile(
            "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*"
        )
        val matcher = pattern.matcher(url)
        return if (matcher.find()) {
            matcher.group()
        } else {
            ""
        }
    }
    
    private fun startSubtitleGeneration() {
        subtitleJob = CoroutineScope(Dispatchers.Main).launch {
            var index = 0
            while (player.isPlaying) {
                val englishText = sampleEnglishPhrases[index % sampleEnglishPhrases.size]
                translateAndDisplaySubtitle(englishText)
                index++
                delay(5000) // Update subtitles every 5 seconds
            }
        }
    }
    
    private fun stopSubtitleGeneration() {
        subtitleJob?.cancel()
        subtitleJob = null
        subtitlesView.text = ""
    }
    
    private fun translateAndDisplaySubtitle(englishText: String) {
        translator.translate(englishText)
            .addOnSuccessListener { translatedText ->
                subtitlesView.text = "$englishText\n$translatedText"
            }
            .addOnFailureListener { exception ->
                Log.e("YouTubeTranslator", "Translation failed", exception)
                subtitlesView.text = englishText
            }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopSubtitleGeneration()
        player.release()
        translator.close()
    }
}
