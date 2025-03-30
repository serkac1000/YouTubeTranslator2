package com.example.youtubetranslator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.common.model.DownloadConditions
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
import java.util.Locale
import kotlinx.coroutines.withContext

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
    private var fallbackSubtitleJob: Job? = null
    private var videoPlaybackMonitorJob: Job? = null
    private var youtubeVideoPlaying = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecognitionActive = false
    private val recognitionResults = mutableListOf<String>()
    private var lastSubtitleText = ""
    private var subtitleDisplayActive = false
    private val recentSubtitles = mutableListOf<String>()
    private val MAX_SUBTITLE_LINES = 3
    private val PERMISSION_REQUEST_RECORD_AUDIO = 101
    
    // Keep sample phrases as fallback when speech recognition is not available
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
        
        // Check for audio recording permission
        checkAudioRecordingPermission()
        
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
        
        // Stop any existing subtitle generation
        stopSubtitleGeneration()
        
        // Display a loading message in Russian
        translator.translate("Loading video...")
            .addOnSuccessListener { translatedText ->
                subtitlesView.text = translatedText
            }
            .addOnFailureListener { 
                subtitlesView.text = "Загрузка видео..." // Russian for "Loading video..."
            }
        
        // Remove any existing WebView
        youtubePlayerContainer.removeAllViews()
        
        // Start subtitle generation immediately - don't wait for page to finish loading
        startSubtitleGeneration()
        
        // Create a new WebView for YouTube playback with enhanced stability
        youtubeWebView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Configure WebView settings for enhanced stability
            settings.apply {
                javaScriptEnabled = true
                mediaPlaybackRequiresUserGesture = false
                domStorageEnabled = true
                loadsImagesAutomatically = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                
                // Additional settings for better stability
                cacheMode = WebSettings.LOAD_DEFAULT  // Use cache when possible for smoother playback
                
                // setRenderPriority is deprecated in newer Android versions
                @Suppress("DEPRECATION")
                try {
                    setRenderPriority(WebSettings.RenderPriority.HIGH)  // High rendering priority
                } catch (e: Exception) {
                    Log.w("YouTubeTranslator", "setRenderPriority not available", e)
                }
                
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW  // Allow mixed content
                allowContentAccess = true  // Allow content access
                
                // Performance optimizations
                // Another deprecated setting but useful on older devices
                @Suppress("DEPRECATION")
                try {
                    setEnableSmoothTransition(true)
                } catch (e: Exception) {
                    Log.w("YouTubeTranslator", "setEnableSmoothTransition not available", e)
                }
                
                allowFileAccess = true
                domStorageEnabled = true
                databaseEnabled = true
                
                // Network optimizations
                // AppCache is deprecated but still used in some devices
                @Suppress("DEPRECATION")
                try {
                    setAppCacheEnabled(true)
                    setAppCachePath(cacheDir.absolutePath)
                } catch (e: Exception) {
                    Log.w("YouTubeTranslator", "App cache setting not available", e)
                }
                
                // DNS prefetch optimization
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setNetworkAvailable(true)
                }
                
                // Buffering enhancements
                mediaPlaybackRequiresUserGesture = false
            }
            
            // Set WebView clients for better handling
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("YouTubeTranslator", "WebView page loading started: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("YouTubeTranslator", "WebView page loading finished: $url")
                    youtubeVideoPlaying = true
                    
                    // Execute JavaScript to ensure video plays continuously
                    view?.let {
                        it.loadUrl("javascript:(function() { " +
                                "var video = document.querySelector('video'); " +
                                "if(video) { " +
                                "  video.play(); " +
                                "} " +
                                "})()")
                    }
                    
                    // Start playback monitoring job
                    startVideoPlaybackMonitor(view)
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // Keep all navigation inside WebView
                    url?.let { view?.loadUrl(it) }
                    return true
                }
                
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Log.e("YouTubeTranslator", "WebView error: $errorCode - $description")
                    Toast.makeText(this@MainActivity, "Ошибка загрузки видео: $description", Toast.LENGTH_SHORT).show()
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    Log.d("YouTubeTranslator", "WebView loading progress: $newProgress%")
                }
            }
            
            // Ensure hardware acceleration is enabled
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // Add the WebView to the container
        youtubePlayerContainer.addView(youtubeWebView)
        
        // Get the API key from settings or fallback to BuildConfig
        val youtubeApiKey = SettingsActivity.getYouTubeApiKey(this)
        
        // Enhanced embed URL with additional parameters for stability and buffering
        val embedUrl = "https://www.youtube.com/embed/$videoId?" +
                "enablejsapi=1" +
                "&key=$youtubeApiKey" +
                "&autoplay=1" +
                "&rel=0" +
                "&showinfo=0" +
                "&controls=1" +  // Show video controls
                "&fs=1" +        // Enable fullscreen option
                "&modestbranding=1" +  // Hide YouTube logo
                "&iv_load_policy=3" +  // Hide video annotations
                "&hl=ru" +       // Russian language interface
                "&playsinline=1" +  // Play inline on devices
                "&html5=1" +     // Force HTML5 player
                "&vq=medium" +   // Set initial quality to medium for better buffering
                "&origin=${Uri.encode(this.packageName)}" // Identify app as origin
        
        // Load the YouTube embed URL with enhanced parameters
        youtubeWebView?.loadUrl(embedUrl)
        
        Log.d("YouTubeTranslator", "Loading YouTube video in WebView with URL: $embedUrl")
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
    
    // Original subtitle generation method replaced with speech recognition implementation
    
    private fun translateAndDisplaySubtitle(englishText: String) {
        // Don't translate the same text repeatedly to avoid flicker
        if (englishText == lastSubtitleText && subtitleDisplayActive) {
            return
        }
        
        // Update the last processed text
        lastSubtitleText = englishText
        subtitleDisplayActive = true
        
        Log.d("YouTubeTranslator", "Translating: $englishText")
        
        translator.translate(englishText)
            .addOnSuccessListener { translatedText ->
                // Add this new subtitle to our recent list
                recentSubtitles.add(translatedText)
                
                // Keep only the most recent subtitles (maximum 3 lines)
                while (recentSubtitles.size > MAX_SUBTITLE_LINES) {
                    recentSubtitles.removeAt(0)
                }
                
                // Join the recent subtitles with newlines to create a YouTube-like effect
                val displayText = recentSubtitles.joinToString("\n")
                
                // Show only Russian subtitles
                subtitlesView.text = displayText
                
                // Schedule this subtitle set to remain visible for at least 3 seconds
                CoroutineScope(Dispatchers.Main).launch {
                    delay(3000)  // Keep subtitle visible for 3 seconds minimum
                }
            }
            .addOnFailureListener { exception ->
                Log.e("YouTubeTranslator", "Translation failed", exception)
                // If translation fails, still try to show something in Russian
                subtitlesView.text = "Ошибка перевода" // "Translation error" in Russian
            }
    }
    
    override fun onPause() {
        super.onPause()
        // Pause the YouTube video when the app goes to the background
        youtubeWebView?.onPause()
        
        // Pause speech recognition when app is in background
        if (isRecognitionActive) {
            stopSpeechRecognition()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Resume the YouTube video when the app comes to the foreground
        youtubeWebView?.onResume()
        
        // Inject JavaScript to ensure video playback continues
        youtubeWebView?.let { webView ->
            if (youtubeVideoPlaying) {
                try {
                    // Use JavaScript to force video play and prevent pausing
                    webView.loadUrl("javascript:(function() { " +
                            "var video = document.querySelector('video'); " +
                            "if(video) { " +
                            "  video.play(); " +
                            "  video.onpause = function() { video.play(); }; " +
                            "} " +
                            "})()")
                } catch (e: Exception) {
                    Log.e("YouTubeTranslator", "Error injecting JavaScript", e)
                }
            }
        }
        
        // Resume speech recognition if video is playing
        if (youtubeVideoPlaying) {
            startSubtitleGeneration()
        }
    }
    
    // Using android:screenOrientation="portrait" in the manifest
    // so we don't need the onConfigurationChanged method anymore
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                // Navigate to Settings screen
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources
        stopSubtitleGeneration()
        player.release()
        translator.close()
        
        // Clean up speech recognizer
        stopSpeechRecognition()
        
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
    
    // Speech recognition methods
    private fun checkAudioRecordingPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
        } else {
            // Permission already granted, initialize speech recognition
            initSpeechRecognizer()
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize speech recognition
                initSpeechRecognizer()
            } else {
                // Permission denied
                Toast.makeText(
                    this,
                    "Аудио разрешение отклонено. Будут использованы образцы субтитров.", // "Audio permission denied. Sample subtitles will be used." in Russian
                    Toast.LENGTH_LONG
                ).show()
                Log.w("YouTubeTranslator", "Audio recording permission denied. Using sample subtitles as fallback.")
            }
        }
    }
    
    private fun initSpeechRecognizer() {
        // Check if device supports speech recognition
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("YouTubeTranslator", "Speech recognition is not available on this device")
            Toast.makeText(
                this,
                "Распознавание речи недоступно на этом устройстве. Используются образцы субтитров.", // "Speech recognition is not available on this device. Using sample subtitles." in Russian
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        // Create Android's built-in speech recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        // Set up recognition listener
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("YouTubeTranslator", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("YouTubeTranslator", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Microphone sound level changed (not essential to log)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d("YouTubeTranslator", "Buffer received")
            }

            override fun onEndOfSpeech() {
                Log.d("YouTubeTranslator", "End of speech")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                Log.e("YouTubeTranslator", "Error in speech recognition: $errorMessage")
                
                // If there's an error, use a fallback phrase
                val fallbackText = sampleEnglishPhrases[(0..9).random()]
                translateAndDisplaySubtitle(fallbackText)
                
                // Restart recognition after a brief delay if still active
                if (isRecognitionActive) {
                    startListening()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    Log.d("YouTubeTranslator", "Speech recognized: $recognizedText")
                    
                    // Add the recognized text to our results list
                    recognitionResults.add(recognizedText)
                    
                    // Keep only the last 10 results to avoid memory issues
                    if (recognitionResults.size > 10) {
                        recognitionResults.removeAt(0)
                    }
                    
                    // Translate and display the recognized text
                    translateAndDisplaySubtitle(recognizedText)
                } else {
                    // No speech detected, use a fallback phrase
                    val fallbackText = sampleEnglishPhrases[(0..9).random()]
                    translateAndDisplaySubtitle(fallbackText)
                }
                
                // Restart listening after a brief delay if still active
                if (isRecognitionActive) {
                    startListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    Log.d("YouTubeTranslator", "Partial speech recognized: $recognizedText")
                    
                    // For continuous update, we can translate and display partial results too
                    translateAndDisplaySubtitle(recognizedText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d("YouTubeTranslator", "Speech recognition event: $eventType")
            }
        })
        
        Log.d("YouTubeTranslator", "Speech recognizer initialized")
    }
    
    private fun startListening() {
        try {
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            }
            
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e("YouTubeTranslator", "Error starting speech recognition", e)
            // If starting speech recognition fails, use a fallback phrase
            val fallbackText = sampleEnglishPhrases[(0..9).random()]
            translateAndDisplaySubtitle(fallbackText)
        }
    }
    
    private fun startSpeechRecognition() {
        // Make sure we have permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("YouTubeTranslator", "Cannot start speech recognition: Missing RECORD_AUDIO permission")
            return
        }
        
        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }
        
        isRecognitionActive = true
        
        // Start listening
        startListening()
        
        // Also start a fallback coroutine that will supply sample phrases 
        // if speech recognition doesn't work properly
        subtitleJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && youtubeVideoPlaying && isRecognitionActive) {
                delay(5000) // Wait 5 seconds
                
                // Only use fallback if no recent recognition results
                if (recognitionResults.isEmpty()) {
                    val fallbackText = sampleEnglishPhrases[(0..9).random()]
                    Log.d("YouTubeTranslator", "Using fallback subtitle: $fallbackText")
                    translateAndDisplaySubtitle(fallbackText)
                }
            }
        }
    }
    
    private fun stopSpeechRecognition() {
        isRecognitionActive = false
        
        // Stop the Android speech recognizer
        speechRecognizer?.cancel()
        
        // Clear recognition results
        recognitionResults.clear()
    }
    
    // Override the original sample-based subtitle generation to use speech recognition instead
    private fun startSubtitleGeneration() {
        // Cancel any existing subtitle job
        subtitleJob?.cancel()
        fallbackSubtitleJob?.cancel()
        
        // Reset subtitle display state
        subtitleDisplayActive = false
        
        // Start with a default subtitle to ensure something is shown immediately
        translateAndDisplaySubtitle(sampleEnglishPhrases[0])
        
        // Start speech recognition if we have permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognition()
            
            // Create a fallback job that will periodically ensure subtitles are showing
            fallbackSubtitleJob = CoroutineScope(Dispatchers.Main).launch {
                while (isActive && youtubeVideoPlaying) {
                    delay(4000) // Check every 4 seconds
                    
                    // If no subtitles are showing for more than 4 seconds, show fallback
                    if (subtitlesView.text.isNullOrEmpty() || !subtitleDisplayActive) {
                        val fallbackText = sampleEnglishPhrases[(0..9).random()]
                        Log.d("YouTubeTranslator", "No subtitles showing, using fallback: $fallbackText")
                        translateAndDisplaySubtitle(fallbackText)
                    }
                }
            }
        } else {
            // Fall back to sample-based subtitle generation
            Log.d("YouTubeTranslator", "Starting sample-based subtitle generation (fallback mode)")
            
            // Start a new subtitle generation job with sample phrases
            subtitleJob = CoroutineScope(Dispatchers.Main).launch {
                var index = 1 // Start from second phrase since we've already shown the first one
                
                // Keep generating subtitles whether the player is playing or not
                while (isActive) {  // isActive is a property from coroutine context
                    val englishText = sampleEnglishPhrases[index % sampleEnglishPhrases.size]
                    translateAndDisplaySubtitle(englishText)
                    index++
                    delay(3000) // Show new subtitles every 3 seconds for better experience
                }
            }
        }
    }
    
    private fun stopSubtitleGeneration() {
        // Stop any active speech recognition
        stopSpeechRecognition()
        
        // Cancel all subtitle jobs
        subtitleJob?.cancel()
        subtitleJob = null
        
        fallbackSubtitleJob?.cancel()
        fallbackSubtitleJob = null
        
        // Stop video monitoring
        videoPlaybackMonitorJob?.cancel()
        videoPlaybackMonitorJob = null
        
        // Clear subtitle display
        subtitlesView.text = ""
        subtitleDisplayActive = false
        lastSubtitleText = ""
        recentSubtitles.clear()
    }
    
    /**
     * This function monitors video playback and ensures it continues without stopping.
     * It injects JavaScript periodically to check video playback state and restart if needed.
     */
    private fun startVideoPlaybackMonitor(webView: WebView?) {
        // Cancel any existing video monitor
        videoPlaybackMonitorJob?.cancel()
        
        if (webView == null) {
            Log.e("YouTubeTranslator", "Cannot start video playback monitor: WebView is null")
            return
        }
        
        // Start a coroutine to periodically check and enforce video playback
        videoPlaybackMonitorJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && youtubeVideoPlaying) {
                try {
                    // Check video state and restart if needed every 1 second
                    webView.loadUrl("javascript:(function() { " +
                        "var video = document.querySelector('video'); " +
                        "if(video) { " +
                        "  if(video.paused) { " +
                        "    console.log('Video was paused - restarting playback'); " +
                        "    video.play(); " +
                        "  } " +
                        "  video.onpause = function() { " +
                        "    console.log('Pause event detected - forcing play'); " +
                        "    video.play(); " +
                        "  }; " +
                        "  video.playbackRate = 1.0; " +  // Ensure normal playback speed
                        "  video.autoplay = true; " +     // Ensure autoplay is enabled
                        "  if (video.readyState < 4) { " + // If video is buffering (not enough data)
                        "    console.log('Video buffering detected - adjusting'); " +
                        "    video.currentTime -= 0.5; " + // Back up slightly to help buffering
                        "  } " +
                        "} else { " +
                        "  console.log('Video element not found'); " +
                        "} " +
                        "})()")
                    
                    // Shorter delay for more responsive checking (2 times per second)
                    delay(500)
                } catch (e: Exception) {
                    Log.e("YouTubeTranslator", "Error in video playback monitor", e)
                    delay(1000) // Wait a bit longer on error
                }
            }
            
            Log.d("YouTubeTranslator", "Video playback monitor stopped")
        }
        
        Log.d("YouTubeTranslator", "Video playback monitor started")
    }
}
