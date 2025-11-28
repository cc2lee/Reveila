// node.js/jwt-auth/server.js
// Main Server File for JWT Authentication API

// 1. Load Environment Variables First (Must be at the top)
require('dotenv').config();

// 2. Import Libraries (Express, etc.)
const express = require('express');
const https = require('https'); // Import https module
const fs = require('fs');       // Import file system module
const enforce = require('express-sslify');
const cors = require('cors');
const app = express();

// Redirect all HTTP requests to HTTPS
// The 'trustProtoHeader: true' option is essential for Heroku, AWS, Nginx, etc.
app.use(enforce.HTTPS({ trustProtoHeader: true }));

// Run npm install cookie-parser to install this package
const cookieParser = require('cookie-parser');
app.use(cookieParser());

// 3. Import and Connect to the Database (MongoDB)
const connectDB = require('./config/db');
connectDB(); // This runs the function that connects to MongoDB Atlas

// 4. Add Middleware (Body parsers, CORS, etc.)
// Run npm install cors to install CORS package
// For production, define a list of allowed origins

/*

const allowedOrigins = [
    'http://localhost:3000',      // Your local development frontend URL
    'https://www.yourwebsite.com' // Your production frontend URL
];

// Configure CORS options
const corsOptions = {
    origin: function (origin, callback) {
        // Allow requests with no origin (like mobile apps or curl requests)
        if (!origin) return callback(null, true);
        
        if (allowedOrigins.indexOf(origin) === -1) {
            const msg = 'The CORS policy for this site does not allow access from the specified Origin.';
            return callback(new Error(msg), false);
        }
        callback(null, true);
    },
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'], // Specify allowed methods
    credentials: true // Crucial if you use cookies or authorization headers
};

// Apply the configured cors middleware to all routes
app.use(cors(corsOptions));

*/
// For development, we'll allow all origins.
app.use(cors());

// Accept Content-Type: application/json
app.use(express.json());

// Accept Content-Type: application/x-www-form-urlencoded
app.use(express.urlencoded({ extended: true }));

// 5. Define Routes (Import route files using app.use())
// Example: app.use('/api/users', require('./routes/userRoutes'));
const authRoutes = require('./routes/auth');
app.use('/api/auth', authRoutes);

app.get('/', (req, res) => {
  res.send('JWT Auth API running');
});

const profileRoutes = require('./routes/profile');
app.use('/api/profile', profileRoutes); // The app.use defines the base path for profile routes

// 6. Configure and start the HTTPS server
const options = {
  key: fs.readFileSync('./config/ssl/key.pem'),
  cert: fs.readFileSync('./config/ssl/cert.pem')
};

const PORT = process.env.PORT || 5000;

// Start the server using HTTPS instead of HTTP
https.createServer(options, app).listen(PORT, () => {
  console.log(`HTTPS Server running on port ${PORT}`);
});