// In this code,
// signAccessToken creates a short token with the user ID and email.
// signRefreshToken creates a long-lived token with a jti value. The jti lets us rotate and track tokens.
// persistRefreshToken hashes the refresh token and stores metadata like expiry and device info.
// setRefreshCookie writes the HTTP-only cookie so the browser sends it to the refresh endpoint automatically.
// rotateRefreshToken revokes the old token, issues a new pair, and saves the new record. Rotation blocks replay if an old refresh token is stolen.

const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const RefreshToken = require('../models/refreshToken');

const ACCESS_TTL = '15m'; // Time-To-Live: 15 minutes
const REFRESH_TTL_SEC = 60 * 60 * 24 * 7; // Time-To-Live: 7 days

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

function createJti() {
  return crypto.randomBytes(16).toString('hex');
}

function signAccessToken(user) {
  const payload = { id: user._id, email: user.email };
  return jwt.sign(payload, process.env.JWT_SECRET, { expiresIn: ACCESS_TTL });
}

function signRefreshToken(user, jti) {
  const payload = { id: user._id, jti };
  const token = jwt.sign(payload, process.env.REFRESH_TOKEN_SECRET, { expiresIn: REFRESH_TTL_SEC });
  return token;
}

async function persistRefreshToken({ user, refreshToken, jti, ip, userAgent }) {
  const tokenHash = hashToken(refreshToken);
  const expiresAt = new Date(Date.now() + REFRESH_TTL_SEC * 1000);
  await RefreshToken.create({ user: user._id, tokenHash, jti, expiresAt, ip, userAgent });
}

function setRefreshCookie(res, refreshToken) {
  const isProd = process.env.NODE_ENV === 'production';
  res.cookie('refresh_token', refreshToken, {
    httpOnly: true,
    secure: isProd,
    sameSite: 'strict',
    path: '/api/auth/refresh',
    maxAge: REFRESH_TTL_SEC * 1000
  });
}

async function rotateRefreshToken(oldDoc, user, req, res) {
  // revoke old
  oldDoc.revokedAt = new Date();
  const newJti = createJti();
  oldDoc.replacedBy = newJti;
  await oldDoc.save();

  // issue new
  const newAccess = signAccessToken(user);
  const newRefresh = signRefreshToken(user, newJti);
  await persistRefreshToken({
    user,
    refreshToken: newRefresh,
    jti: newJti,
    ip: req.ip,
    userAgent: req.headers['user-agent'] || ''
  });
  setRefreshCookie(res, newRefresh);
  return { accessToken: newAccess };
}

module.exports = {
  hashToken,
  createJti,
  signAccessToken,
  signRefreshToken,
  persistRefreshToken,
  setRefreshCookie,
  rotateRefreshToken
};