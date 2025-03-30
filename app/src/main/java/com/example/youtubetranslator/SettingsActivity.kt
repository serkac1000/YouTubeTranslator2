package com.example.youtubetranslator

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var saveButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    
    companion object {
        const val PREFS_NAME = "YouTubeTranslatorPrefs"
        const val KEY_YOUTUBE_API_KEY = "youtube_api_key"
        
        // Get saved API key or use the one from BuildConfig as fallback
        fun getYouTubeApiKey(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedKey = prefs.getString(KEY_YOUTUBE_API_KEY, null)
            
            // If saved key exists and is not empty, use it; otherwise, use the one from BuildConfig
            return if (!savedKey.isNullOrEmpty()) {
                savedKey
            } else {
                BuildConfig.YOUTUBE_API_KEY
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Enable up button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
        
        // Initialize views
        apiKeyLayout = findViewById(R.id.apiKeyLayout)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        saveButton = findViewById(R.id.saveButton)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load the current API key
        val currentApiKey = getYouTubeApiKey(this)
        apiKeyInput.setText(currentApiKey)
        
        // Set up input validation
        apiKeyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                apiKeyLayout.error = null
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Set up save button
        saveButton.setOnClickListener {
            saveApiKey()
        }
    }
    
    private fun saveApiKey() {
        val apiKey = apiKeyInput.text.toString().trim()
        
        // Validate API key (simple validation - not empty and minimum length)
        if (apiKey.isEmpty()) {
            apiKeyLayout.error = getString(R.string.error_empty_api_key)
            return
        }
        
        if (apiKey.length < 20) {
            apiKeyLayout.error = getString(R.string.error_invalid_api_key)
            return
        }
        
        // Save the API key
        sharedPreferences.edit().putString(KEY_YOUTUBE_API_KEY, apiKey).apply()
        
        // Show success message
        Toast.makeText(
            this,
            getString(R.string.api_key_saved),
            Toast.LENGTH_SHORT
        ).show()
        
        // Hide keyboard
        hideKeyboard()
        
        // Finish activity
        finish()
    }
    
    private fun hideKeyboard() {
        val view = currentFocus ?: View(this)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Respond to the action bar's Up button
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}