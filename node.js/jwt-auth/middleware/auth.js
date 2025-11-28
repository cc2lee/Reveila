// middleware/auth.js
const jwt = require('jsonwebtoken');

// The access token can live in a cookie named access_token.
// Mark the cookie as httpOnly and secure in production.
// Set sameSite: 'strict' to reduce CSRF risk.
// For APIs used by browsers, cookies simplify sending tokens. For SPAs that call many domains, an Authorization header may be simpler.
function auth(req, res, next) {
  const header = req.headers.authorization || '';
  const [scheme, tokenFromHeader] = header.split(' ');
  const tokenFromCookie = req.cookies?.access_token;

  const token = scheme === 'Bearer' && tokenFromHeader ? tokenFromHeader : tokenFromCookie;

  if (!token) return res.status(401).json({ message: 'No token provided' });

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = { id: decoded.id, email: decoded.email };
    next();
  } catch (err) {
    const msg = err.name === 'TokenExpiredError' ? 'Access token expired' : 'Invalid token';
    return res.status(401).json({ message: msg });
  }
}

module.exports = auth;