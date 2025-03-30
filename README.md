# YouTube Translator Android App

This project is an Android application that plays YouTube videos and provides real-time English-to-Russian subtitle translation with advanced buffering resilience and continuous subtitle display.

## Project Overview

The YouTube Translator app allows users to:
- Enter a YouTube video URL (saved between app sessions)
- Play the video within the app with advanced buffering handling
- View automatically generated English-to-Russian translations as subtitles
- Experience continuous subtitle display even during buffering
- Benefit from automatic quality adjustments and buffer recovery

## Technical Implementation

The app is built using:
- Kotlin programming language
- YouTube iframe API in WebView for video playback
- ML Kit for on-device translation
- Coroutines for asynchronous operations
- JavaScript evaluations for advanced buffering detection

## Project Structure

- `/app` - Contains the Android application source code
- `index.html` - Documentation and demo page
- `app_preview.png` - App preview image
- `youtube_translator.apk` - Downloadable APK file (placeholder)

## Setup Instructions

### Prerequisites
- Android Studio 4.1 or higher
- Android SDK with API level 21+
- Gradle 7.3.3+

### Building the App
1. Clone this repository
2. Open the project in Android Studio
3. Sync project with Gradle files
4. Build and run on an emulator or physical device

## Web Documentation

This repository also includes a web-based documentation page that:
- Explains the app's features and functionality
- Shows a preview of the app interface
- Provides a download link for the APK

To view the documentation:
1. Run `node server.js`
2. Open a browser and navigate to `http://localhost:5000`

## Key Features

- **Enhanced Buffering Handling**: Specially designed buffering detection and recovery system
- **Continuous Subtitles**: Subtitles remain active even during video buffering
- **Smart Quality Adjustments**: Automatic quality settings to optimize playback
- **Visual Buffering Indicators**: Special "⟳" symbol shows when buffering is active
- **URL Persistence**: Previously entered URLs are saved between app sessions
- **YouTube-like Subtitle Display**: Shows the latest 3 lines for better readability

## Limitations

- Direct YouTube video playback may be restricted due to YouTube's terms of service
- The current implementation uses sample English phrases rather than actual speech recognition
- A production app would require a more robust solution for YouTube video extraction

## Future Improvements

- Implement speech recognition for real-time subtitle generation
- Support additional language pairs beyond English-to-Russian
- Add user preferences for translation settings
- Improve YouTube video extraction methods
- Add a history of recently viewed videos