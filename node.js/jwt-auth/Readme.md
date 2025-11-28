### Client Flow

Here is how the browser app should behave:

* Keep the access token in memory. Do not put it in localStorage.
* Call protected APIs with the `Authorization` header or let cookies handle it if you chose the cookie approach for access.
* If a call fails with `Access token expired`, call `/api/auth/refresh`. The browser sends the refresh cookie automatically.
* Replace the in-memory access token with the new one.
* Retry the original request.
* On logout, call `/api/auth/logout` and clear any local state.
