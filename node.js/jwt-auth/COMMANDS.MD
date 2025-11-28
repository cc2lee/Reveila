Run Express Server:

```bash
node server.js
```

or, if you’re using nodemon (hot reload upon saving code changes). `nodemon` automatically restarts the Node.js server.

```bash
npx nodemon server.js
```

Server URL:

```bash
http://localhost:5000
```

Install mongoose (MongoDB):

```bash
npm install mongoose
```

Generate 32 bytes (256 bits) key using Node.js:

```
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```
