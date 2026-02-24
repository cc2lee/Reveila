# How PostgreSQL populates data in ${REVEILA_HOME}/data/postgres?

When you launch the reveila-db-prod container, the following sequence occurs:

The Handshake: Docker looks at your docker-compose.prod.yml and sees that ../data/postgres on your Windows machine should be "mounted" to /var/lib/postgresql/data inside the Linux container.

The Initialization: The Postgres engine checks that internal folder.

If it's empty: Postgres runs its internal initdb command. It creates the system tables, the reveila_db database, and the user charles. You will see files like PG_VERSION and base/ suddenly appear in your Windows ../data/postgres folder.

If it's NOT empty: Postgres assumes the database already exists. It just "opens" the files and starts the service.