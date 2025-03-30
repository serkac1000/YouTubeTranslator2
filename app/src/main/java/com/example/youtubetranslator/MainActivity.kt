package com.example.youtubetranslator

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var translator: Translator
    private lateinit var youtubeLinkInput: TextInputEditText
    private lateinit var youtubeLinkLayout: TextInputLayout
    private lateinit var subtitlesView: TextView
    private lateinit var playButton: Button
    private lateinit var youtubePlayerContainer: FrameLayout
    private var youtubeWebView: WebView? = null
    
    private var subtitleJob: Job? = null
    private var youtubeVideoPlaying = false
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
        youtubePlayerContainer = findViewById(R.id.youtubePlayerContainer)
        youtubeLinkLayout = findViewById(R.id.youtubeLinkLayout)
        youtubeLinkInput = findViewById(R.id.youtubeLinkInput)
        subtitlesView = findViewById(R.id.subtitlesView)
        playButton = findViewById(R.id.playButton)
        
        // Setup input field with better user experience
        youtubeLinkInput.setText("")
        
        // Configure the TextInputLayout for better UX
        youtubeLinkLayout.hint = getString(R.string.enter_youtube_link)
        youtubeLinkLayout.isHintEnabled = true
        youtubeLinkLayout.isErrorEnabled = true
        youtubeLinkLayout.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        
        // Configure TextInputLayout end icon to clear text
        youtubeLinkLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        
        // Make sure the EditText is properly configured for input
        youtubeLinkInput.isEnabled = true
        youtubeLinkInput.isClickable = true
        youtubeLinkInput.isFocusable = true
        youtubeLinkInput.isFocusableInTouchMode = true
        
        // Set a click listener to ensure the keyboard shows up
        youtubeLinkInput.setOnClickListener {
            // Request focus and show keyboard
            youtubeLinkInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(youtubeLinkInput, InputMethodManager.SHOW_IMPLICIT)
        }
        
        // Also add a touch listener for additional coverage
        youtubeLinkInput.setOnTouchListener { v, event ->
            // Show keyboard
            v.performClick() // Call the click listener too
            youtubeLinkInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(youtubeLinkInput, InputMethodManager.SHOW_IMPLICIT)
            false // Return false to indicate that we haven't consumed the event
        }
        
        // Set up a text change listener
        youtubeLinkInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Nothing to do here
            }
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Log the text change for debugging
                Log.d("YouTubeTranslator", "Text changed: ${s.toString()}")
            }
            
            override fun afterTextChanged(s: Editable?) {
                // Nothing special needed here
            }
        })
        
        // Log that the app has started and input field is initialized
        Log.d("YouTubeTranslator", "App started, input field initialized")
        
        // Request focus on the input field when the activity starts
        youtubeLinkInput.post {
            youtubeLinkInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(youtubeLinkInput, InputMethodManager.SHOW_IMPLICIT)
        }
        
        // Initialize ExoPlayer (keeping it for future use if needed)
        player = ExoPlayer.Builder(this).build()
        
        // We'll use a WebView instead of ExoPlayer for YouTube playback
        
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
        
        // Set up play button click listener with validation
        playButton.setOnClickListener {
            val link = youtubeLinkInput.text.toString().trim()
            
            // Clear any previous errors
            youtubeLinkLayout.error = null
            
            if (link.isEmpty()) {
                // Show error in the TextInputLayout instead of Toast
                youtubeLinkLayout.error = "Please enter a YouTube link"
                youtubeLinkInput.requestFocus()
            } else if (!link.contains("youtube.com") && !link.contains("youtu.be")) {
                // Show error for invalid YouTube link
                youtubeLinkLayout.error = "Please enter a valid YouTube link (youtube.com or youtu.be)"
                youtubeLinkInput.requestFocus()
            } else {
                // Valid link format, proceed to try to extract video ID and play
                val videoId = extractVideoId(link)
                if (videoId.isEmpty()) {
                    youtubeLinkLayout.error = "Could not extract video ID from the link. Supported formats: youtube.com/watch?v=VIDEO_ID or youtu.be/VIDEO_ID"
                    youtubeLinkInput.requestFocus()
                } else {
                    // Valid video ID extracted, proceed to play
                    playVideo(link)
                }
            }
        }
    }
    
    private fun playVideo(link: String) {
        // Extract video ID from YouTube link
        val videoId = extractVideoId(link)
        
        // We've already validated the video ID in the click listener, but let's double-check
        if (videoId.isEmpty()) {
            Log.e("YouTubeTranslator", "Empty video ID after validation. Link: $link")
            youtubeLinkLayout.error = "Could not extract video ID from link. Please check the format."
            youtubeLinkInput.requestFocus()
            return
        }
        
        Log.d("YouTubeTranslator", "Playing YouTube video with ID: $videoId")
        
        // Display a loading message
        subtitlesView.text = getString(R.string.loading_video)
        
        // Remove any existing WebView
        youtubePlayerContainer.removeAllViews()
        
        // Create a new WebView for YouTube playback
        youtubeWebView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Configure WebView settings
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(false)
            
            // Set WebView clients
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("YouTubeTranslator", "WebView page loading finished")
                    youtubeVideoPlaying = true
                    startSubtitleGeneration()
                }
            }
            
            webChromeClient = WebChromeClient()
        }
        
        // Add the WebView to the container
        youtubePlayerContainer.addView(youtubeWebView)
        
        // Generate YouTube iframe embed URL with API key
        val youtubeApiKey = BuildConfig.YOUTUBE_API_KEY
        val embedUrl = "https://www.youtube.com/embed/$videoId?enablejsapi=1&key=$youtubeApiKey&autoplay=1&rel=0&showinfo=0"
        
        // Load the YouTube embed URL
        youtubeWebView?.loadUrl(embedUrl)
        
        Log.d("YouTubeTranslator", "Loading YouTube video in WebView with URL: $embedUrl")
        
        // Reset subtitles
        stopSubtitleGeneration()
    }
    
    private fun extractVideoId(url: String): String {
        Log.d("YouTubeTranslator", "Extracting video ID from URL: $url")
        
        // Handle youtu.be short links first
        if (url.contains("youtu.be")) {
            // Extract ID from youtu.be/VIDEO_ID format
            val regex = "youtu\\.be/([^?&#]+).*".toRegex()
            val matchResult = regex.find(url)
            if (matchResult != null && matchResult.groupValues.size > 1) {
                val videoId = matchResult.groupValues[1]
                Log.d("YouTubeTranslator", "Successfully extracted video ID from youtu.be URL: $videoId")
                return videoId
            } else {
                Log.e("YouTubeTranslator", "Failed to extract video ID from youtu.be URL using regex")
            }
        }
        
        // Handle standard youtube.com links
        if (url.contains("youtube.com")) {
            // Extract ID from v=VIDEO_ID parameter
            val regex = "[?&]v=([^?&#]+).*".toRegex()
            val matchResult = regex.find(url)
            if (matchResult != null && matchResult.groupValues.size > 1) {
                val videoId = matchResult.groupValues[1]
                Log.d("YouTubeTranslator", "Successfully extracted video ID from youtube.com URL: $videoId")
                return videoId
            } else {
                Log.e("YouTubeTranslator", "Failed to extract video ID from youtube.com URL using regex")
            }
        }
        
        // If the above methods don't work, try the original pattern as fallback
        val pattern = Pattern.compile(
            "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*"
        )
        val matcher = pattern.matcher(url)
        if (matcher.find()) {
            val videoId = matcher.group()
            Log.d("YouTubeTranslator", "Extracted video ID using fallback pattern: $videoId")
            return videoId
        } else {
            Log.e("YouTubeTranslator", "All extraction methods failed for URL: $url")
            return ""
        }
    }
    
    private fun startSubtitleGeneration() {
        // Cancel any existing subtitle job
        subtitleJob?.cancel()
        
        // Start a new subtitle generation job
        subtitleJob = CoroutineScope(Dispatchers.Main).launch {
            var index = 0
            
            // Keep generating subtitles whether the player is playing or not
            // This is for demonstration purposes since we can't directly play YouTube videos
            while (isActive) {  // isActive is a property from coroutine context
                val englishText = sampleEnglishPhrases[index % sampleEnglishPhrases.size]
                translateAndDisplaySubtitle(englishText)
                index++
                delay(5000) // Update subtitles every 5 seconds
            }
        }
        
        Log.d("YouTubeTranslator", "Started subtitle generation")
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
    
    override fun onPause() {
        super.onPause()
        // Pause the YouTube video when the app goes to the background
        youtubeWebView?.onPause()
    }
    
    override fun onResume() {
        super.onResume()
        // Resume the YouTube video when the app comes to the foreground
        youtubeWebView?.onResume()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        stopSubtitleGeneration()
        player.release()
        translator.close()
        
        // Clean up WebView
        youtubeWebView?.let { webView ->
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
            webView.destroy()
            youtubeWebView = null
        }
    }
}
