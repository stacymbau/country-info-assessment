# Country Info Service

Takes a country name over REST, looks it up through a public SOAP service, saves the result in MySQL, and offers CRUD endpoints.

**Stack:** Java 21, Spring Boot 4, MySQL, Docker, Kubernetes

## How it works

1. `POST /api/v1/countries` with `{"name":"kenya"}`
2. The name becomes `Kenya`
3. SOAP call 1 returns the ISO code (`KE`)
4. SOAP call 2 returns the full country info (capital, phone code, currency, flag, languages)
5. The country and its languages are saved in MySQL

Posting a country that is already saved returns it without a duplicate.

## Run it

You need JDK 21, Maven and Docker Desktop.

```powershell
mvn clean package -DskipTests
docker compose up --build
```

Check it is running:

```powershell
curl.exe http://localhost:8080/actuator/health/readiness
```

You should see `{"status":"UP"}`. Database tables are created automatically.

## Test it

**Mac, Linux or Git Bash:**

```bash
# create (201). Run it again and you get 200, no duplicate
curl -i -X POST http://localhost:8080/api/v1/countries \
  -H "Content-Type: application/json" \
  -d '{"name":"kenya"}'

# list, get one
curl http://localhost:8080/api/v1/countries
curl http://localhost:8080/api/v1/countries/1

# update
curl -X PUT http://localhost:8080/api/v1/countries/1 \
  -H "Content-Type: application/json" \
  -d '{"capitalCity":"Nairobi City"}'

# delete (204)
curl -i -X DELETE http://localhost:8080/api/v1/countries/1
```

Error cases:

```bash
curl -i -X POST http://localhost:8080/api/v1/countries -H "Content-Type: application/json" -d '{"name":""}'         # 400
curl -i -X POST http://localhost:8080/api/v1/countries -H "Content-Type: application/json" -d '{"name":"Atlantis"}' # 404
curl -i http://localhost:8080/api/v1/countries/9999                                                                 # 404
```

(In PowerShell, escape the quotes the same way as above.)

Or run everything at once: `.\scripts\smoke-test.ps1` (PowerShell) or `./scripts/smoke-test.sh` (bash).

Unit tests: `mvn verify`

## API

| Method | Path | Result |
|---|---|---|
| POST | `/api/v1/countries` | 201 created, 200 already saved, 400 bad input, 404 unknown country, 503 SOAP service down |
| GET | `/api/v1/countries` | List (paged) |
| GET | `/api/v1/countries/{id}` | One country, or 404 |
| PUT | `/api/v1/countries/{id}` | Update the fields you send |
| DELETE | `/api/v1/countries/{id}` | 204 |

## Deploy to Kubernetes

```powershell
$env:DB_PASSWORD = 'choose-a-password'
$env:MYSQL_ROOT_PASSWORD = 'choose-another'
.\scripts\deploy.ps1
```

## Key design choices

- **Stateless service:** all data lives in MySQL, so more copies can run at once.
- **Timeouts, retries and a circuit breaker** protect the app when the SOAP service is slow or down.
- **Caching:** SOAP lookups are cached for 24 hours.
- **Fallback:** if the SOAP service is down, countries already saved are still returned.
- **Clear errors:** every error returns the same JSON shape with a correlation ID.
- **Logs and metrics:** each request has a correlation ID in the logs. Metrics are at `/actuator/prometheus`.
- **Health checks:** `/actuator/health/liveness` and `/readiness` are used by Kubernetes.

## Settings

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local MySQL | Database |
| `SOAP_URL` | public SOAP service | SOAP address |
| `SOAP_READ_TIMEOUT` | `5s` | SOAP timeout |
