# Auth Service

Minimal Java 17 / Spring Boot authentication API with Spring Security, Spring Data
JPA, BCrypt and an externally hosted Render PostgreSQL database.

## Authentication architecture and flow

Authentication responses include a numeric `status` matching the HTTP status and
a readable `message`. Successful login also includes `email`:

```json
{"status":200,"message":"Login successful","email":"test@example.com"}
```

Validation errors include field messages without echoing submitted values:

```json
{"status":400,"message":"Validation failed","errors":{"password":"must not be blank"}}
```

Malformed JSON returns 400 with an explanation. Invalid credentials return 401,
duplicate registration returns 409, and invalid CSRF returns 403 with instructions.
Logout returns 200 with `{"status":200,"message":"Logout successful"}` so it
can include a JSON body.

![Authentication architecture: CSRF setup, registration, login, session access and logout](docs/images/authentication-flow.png)

1. **Prepare:** fetch `GET /auth/csrf` and keep the session cookie and CSRF token.
2. **Register:** send `POST /auth/register`. Spring Security checks CSRF, the
   controller validates the DTO, and `AuthService` hashes the password with BCrypt.
   JPA stores the email and hash in Render PostgreSQL. Registration returns 201
   without logging the user in.
3. **Log in:** send `POST /auth/login`. The authentication provider loads the user
   through JPA and verifies the BCrypt hash. The controller rotates the session
   ID and saves the authenticated security context in the HTTP session. Fetch a
   new CSRF token after login.
4. **Access / logout:** send the session cookie to `GET /auth/me`. Spring Security
   restores authentication from the session. To end it, send `POST /auth/logout`
   with the cookie and fresh CSRF token; the logout filter invalidates the session.

PostgreSQL stores user accounts; authenticated sessions live in application
memory. Database credentials come only from environment variables. See the
sections below for configuration and runnable request examples.

The diagram is stored in `docs/images/authentication-flow.png`. To regenerate it
on Windows, run `powershell -File docs/generate-auth-flow.ps1`.

## 1. Maven dependencies

`pom.xml` includes these dependencies (versions managed by Spring Boot):

| Dependency | Purpose |
| --- | --- |
| spring-boot-starter-web | JSON HTTP endpoints |
| spring-boot-starter-security | Authentication, sessions, CSRF and BCrypt |
| spring-boot-starter-data-jpa | Entity persistence and repositories |
| spring-boot-starter-validation | Request DTO validation |
| postgresql (runtime) | PostgreSQL JDBC driver |
| spring-boot-starter-security-test (test) | Spring testing support |
| h2 (test) | Isolated tests without Render credentials |

## 2. Implementation, step by step

All Java files are under `src/main/java/javasecurity/authservice/`:

```text
SecurityApplication.java
 auth/
   AuthController.java
   AuthService.java
   User.java
   UserRepository.java
   RegisterRequest.java
   LoginRequest.java
 config/
   SecurityConfig.java
```

1. **User** maps to `app_users`. It stores an ID, a unique normalized email and
   a BCrypt hash. Plain-text passwords are never persisted or returned.
2. **UserRepository** extends `JpaRepository` and finds users by email. The database
   unique constraint also prevents duplicate emails during concurrent requests.
3. **RegisterRequest / LoginRequest** validate JSON with `@Valid`, `@NotBlank`,
   `@Email` and `@Size`. Registration requires at least eight characters. Both
   endpoints reject passwords exceeding BCrypt's 72 UTF-8 byte limit.
4. **AuthService** normalizes emails to lowercase, checks duplicates, hashes the
   password and saves the user. Duplicate registration returns 409.
5. **SecurityConfig** loads users from JPA, checks hashes through
   `DaoAuthenticationProvider`, and requires authentication except for registration,
   login, CSRF retrieval and the health endpoint. There are no roles. HTTP Basic
   and form login are not configured; clients log in with JSON.
6. **AuthController** authenticates login credentials, rotates the session ID,
   and explicitly saves the security context to the HTTP session. Invalid credentials
   return 401. `/auth/me` demonstrates that the session works on later requests.
7. **CSRF and logout** use Spring Security's CSRF protection. Fetch a token before
   sending POST requests. Login clears the old token; fetch another after login.
   POST `/auth/logout` invalidates the session and returns 200 with a logout message.

See Spring Security's [session persistence documentation](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html).

## 3. Configure Render PostgreSQL

Create a PostgreSQL database in Render. In its connection details, find the host,
port, database name, username and password. For connections from your computer,
use the **external** host and permit your client IP in Render's access controls.
Build a JDBC URL without embedding credentials:

```text
DB_URL=jdbc:postgresql://<external-host>:5432/<database-name>?sslmode=require
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
```

Do not paste Render's `postgresql://user:password@host/database` URL directly into
`DB_URL`: JDBC requires the `jdbc:postgresql://` format. Supply credentials through
the separate environment variables. For services in the same Render region, the
internal database host can be used instead.

`src/main/resources/application.properties` reads these variables without fallback
credentials. `ddl-auto=update` creates/updates the learning project's table on
startup. Use versioned schema migrations before production use.

Render reference: [Create and connect to Postgres](https://render.com/docs/postgresql-creating-connecting).

## 4. Run locally

Install JDK 17+ and set `JAVA_HOME`. In PowerShell, set the three DB environment
variables in your current shell, then run:

```powershell
$env:DB_URL = 'jdbc:postgresql://<external-host>:5432/<database-name>?sslmode=require'
$env:DB_USERNAME = '<database-user>'
$env:DB_PASSWORD = '<database-password>'
.\mvnw.cmd spring-boot:run
```

Use your own values locally; never commit credentials. Maven downloads through
the wrapper. Run tests with `.\mvnw.cmd clean verify` (Linux/macOS:
`sh ./mvnw clean verify`). Tests use an isolated H2 database, not Render.

For Docker, put those variables in an untracked `.env` file beside `compose.yaml`:

```sh
docker compose up --build -d
docker compose logs -f auth-service
docker compose down
```

Compose reads `.env`; running Maven directly does not. Optional
`AUTH_SERVICE_PORT=8081` changes the Docker host port. The Docker build runs tests
and packages the application into a Java 17 image running as a non-root user.

## 5. Try registration and session login

PowerShell example (service running at port 8080):

```powershell
$base = 'http://localhost:8080'
$csrf = Invoke-RestMethod "$base/auth/csrf" -SessionVariable session
$headers = @{ $csrf.headerName = $csrf.token }

Invoke-RestMethod "$base/auth/register" -Method Post -WebSession $session `
  -Headers $headers -ContentType 'application/json' `
  -Body '{"email":"learner@example.com","password":"learning-password"}'

Invoke-RestMethod "$base/auth/login" -Method Post -WebSession $session `
  -Headers $headers -ContentType 'application/json' `
  -Body '{"email":"learner@example.com","password":"learning-password"}'

Invoke-RestMethod "$base/auth/me" -WebSession $session

$csrf = Invoke-RestMethod "$base/auth/csrf" -WebSession $session
$headers = @{ $csrf.headerName = $csrf.token }
Invoke-RestMethod "$base/auth/logout" -Method Post -WebSession $session -Headers $headers
```

Registration returns 201, login and `/auth/me` return an email, and logout returns
200. Invalid DTOs return 400, duplicate emails 409, bad credentials 401, and missing
or invalid CSRF tokens 403. Keep the session cookie between requests. Registration
does not log the user in. Fetch a fresh token after logout before logging in again.

## 6. Deploy the application on Render

1. Push the project to your Git provider, then create a Render **Web Service** from
   the repository and choose the **Docker** runtime. Set the root directory to this
   project if it lives in a larger repository. The included `Dockerfile` builds and
   starts the JAR; no separate build/start commands are needed.
2. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in Render's environment settings.
   Set `SESSION_COOKIE_SECURE=true` so session cookies use HTTPS. Never add secret
   values to `render.yaml` or source files.
3. Set the health check path to `/health`. The app binds to `${PORT:8080}`; Render
   supplies `PORT`. Deploy and check the logs for successful database connectivity.
4. Alternatively, create a Blueprint from the included `render.yaml`, which prompts
   for the three database variables and sets the secure cookie flag. It uses your
   existing database rather than creating another one.

See the [Render Blueprint reference](https://render.com/docs/blueprint-spec)

Sessions live in application memory, expire after 30 minutes of inactivity, and
are lost on restart or redeployment. Run one instance for this minimal setup;
multiple instances require shared session storage. Browser examples assume the
frontend shares the API's origin; cross-origin CORS/cookie configuration is not
included. The H2 tests do not replace a connection check against your Render DB.

