const express = require('express');
const app = express();
const port = 5000;

// Serve static files from the current directory
app.use(express.static('./'));

// Start the server
app.listen(port, '0.0.0.0', () => {
  console.log(`Server running at http://0.0.0.0:${port}/`);
  console.log('Access the YouTube Translator app documentation page in your browser');
});