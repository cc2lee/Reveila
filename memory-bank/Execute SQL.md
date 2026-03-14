To run SQL scripts against the Reveila database (PostgreSQL) running in Docker, you can use the `docker exec` command. Based on your project structure, here are the specific commands for your environment.

### 1. Identify Database Credentials
From your [`docker-compose.prod.yml`](system-home/standard/infrastructure/docker-compose.prod.yml:71), the credentials are:
- **Container Name:** `reveila-db-prod`
- **User:** `admin`
- **Database:** `reveila_db`

---

### 2. Run a Script to Create Tables (Schema)
To run the [`audit.sql`](system-home/standard/resources/db/schemas/audit.sql) script:

**On Windows (Command Prompt):**
```cmd
type ./get-audit-history.sql | docker exec -i reveila-db-prod psql -U admin -d reveila_db
```

**On Windows (PowerShell):**
```powershell
Get-Content ./get-audit-history.sql | docker exec -i reveila-db-prod psql -U admin -d reveila_db
```

---

### 3. Run a Query Script
To run the [`policy-enforcement-history.sql`](system-home/standard/resources/db/queries/policy-enforcement-history.sql) query:

**On Windows (Command Prompt):**
```cmd
type system-home\standard\resources\db\queries\policy-enforcement-history.sql | docker exec -i reveila-db-prod psql -U admin -d reveila_db
```

---

### 4. Interactive SQL Shell (Manual Queries)
If you want to log into the database and run queries manually:
```powershell
docker exec -it reveila-db-prod psql -U admin -d reveila_db
```
Once inside, you can type any SQL command (ending with `;`) and press Enter. Type `\q` to exit.

### 💡 Troubleshooting
If you receive a "vector" extension error when running `audit.sql`, ensure your PostgreSQL image supports `pgvector`. The system is configured to use `ankane/pgvector:latest`, which provides native support for AI vector embeddings.







List the tables:
PowerShell
docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "\dt"

The AI "Pulse" Check:
