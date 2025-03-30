#!/bin/bash
set -e

# Define variables
GITHUB_REPO="https://github.com/serkac1000/YouTubeTranslator2.git"
GITHUB_USERNAME="serkac1000"
BRANCH="main"

# Check if GITHUB_TOKEN exists
if [ -z "$GITHUB_TOKEN" ]; then
  echo "Error: GITHUB_TOKEN environment variable not set"
  exit 1
fi

# Initialize Git repository (if not already initialized)
if [ ! -d ".git" ]; then
  git init
  echo "Git repository initialized."
else
  echo "Git repository already exists."
fi

# Configure Git for this operation
git config --global user.name "GitHub Actions"
git config --global user.email "actions@github.com"

# Set the remote URL with the token for authentication
REMOTE_URL="https://${GITHUB_USERNAME}:${GITHUB_TOKEN}@github.com/${GITHUB_USERNAME}/YouTubeTranslator2.git"
git remote remove origin 2>/dev/null || true
git remote add origin "$REMOTE_URL"
echo "Git remote set to $GITHUB_REPO"

# Create a .gitignore file to exclude unnecessary files
if [ ! -f ".gitignore" ]; then
  cat > .gitignore << EOF
# Built application files
*.apk
*.aar
*.ap_
*.aab

# Files for the ART/Dalvik VM
*.dex

# Java class files
*.class

# Generated files
bin/
gen/
out/

# Gradle files
.gradle/
build/

# Local configuration file (sdk path, etc)
local.properties

# Proguard folder
proguard/

# Log Files
*.log

# Android Studio Navigation editor temp files
.navigation/

# Android Studio captures folder
captures/

# IntelliJ
*.iml
.idea/

# Keystore files
*.jks
*.keystore

# External native build folder generated in Android Studio 2.2 and later
.externalNativeBuild
.cxx/

# Google Services (e.g. APIs or Firebase)
google-services.json

# Version control
.git/

# Mac system files
.DS_Store
EOF
  echo "Created .gitignore file."
fi

# Add all files
git add .
echo "Added files to git staging."

# Commit changes
COMMIT_MSG="Updated YouTube Translator app with settings menu for API key configuration"
git commit -m "$COMMIT_MSG"
echo "Committed changes with message: $COMMIT_MSG"

# Push to GitHub
git push -u origin $BRANCH --force
echo "Changes pushed to GitHub repository: $GITHUB_REPO"

echo "Upload to GitHub completed successfully!"