# Fix Render's Supabase ENOIDENTIFIER startup error

`FATAL: (ENOIDENTIFIER) no tenant identifier provided` means the connection reached
Supabase's shared pooler without the project identifier it needs for routing.
Flyway is the first database client to expose the error; disabling Flyway does not
repair the connection.

## Correct the existing Render service

1. In the **existing Supabase project**, open **Connect > Session pooler**.
   Copy its exact host and username. Do not guess the region or host prefix.
2. In **Render > your backend service > Environment**, use:

   ```dotenv
   DB_URL=jdbc:postgresql://EXACT_SESSION_POOLER_HOST:5432/postgres?sslmode=require&connectTimeout=10&socketTimeout=30&tcpKeepAlive=true
   DB_USER=postgres.EXACT_PROJECT_REF
   DB_USERNAME=postgres.EXACT_PROJECT_REF
   DB_PASSWORD=YOUR_EXISTING_DATABASE_PASSWORD
   ```

   Replace every placeholder. The GitHub `main` revision inspected on 2026-10-15
   (`b3d425a`) reads **DB_USER**. The current local code accepts **DB_USERNAME**
   with **DB_USER** as its fallback. Set both names to the same full username
   while updating the existing deployment. If Connect gives a custom database
   role, use that exact username instead of `postgres`.

3. Keep the password in Render's private environment field. Use the raw database
   password there, without quotes or URL encoding. A Supabase anon/service-role
   API key is not the database password. Keep credentials out of `DB_URL`.
4. Check for overriding settings: `SPRING_DATASOURCE_URL`,
   `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
   `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER`, and `SPRING_FLYWAY_PASSWORD`.
   Remove obsolete overrides or align them with the same intended database.
   A `user=postgres` URL parameter also overrides the separate username in pgJDBC.
5. Save and redeploy. Keep the existing database and migration history. With the
   current local production profile, apply any reviewed pending migrations using
   the maintenance procedure in [DEPLOYMENT.md](DEPLOYMENT.md), then deploy with
   `SPRING_PROFILES_ACTIVE=production` and `FLYWAY_ENABLED=false`.

## Verify the deployment

- Build the current code with `scripts/maven.ps1 -B verify` on Windows, or
  `mvn -B verify` with Java 21.
- The new startup guard checks the effective datasource and Flyway settings
  before connections open. It explains missing tenant usernames, transaction
  pooler ports, missing pooler TLS, and malformed JDBC URLs without printing
  credentials. It cannot infer the correct project reference or validate the
  database password without access to the existing service.
- In Render, set the health-check path to `/ready` for this local revision.
  Confirm both `GET https://YOUR-SERVICE.onrender.com/health` and `/ready` return
  HTTP 200. `/ready` executes a database query; a successful build alone does not
  prove that the deployment works.
- Check that logs show successful startup without tenant, authentication, or
  schema-validation errors. If a schema error appears next, inspect the actual
  migration history before running migrations; never reset or baseline blindly.

To complete the live deployment from this workspace, provide the Render service
URL plus access through a connected signed-in browser or a privately configured
Render API credential. The Supabase Session pooler host and username are also
needed to verify the fix. Keep passwords and tokens out of chat and Git.

References: [Supabase connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres),
[Supavisor tenant routing](https://supabase.github.io/supavisor/connecting/overview/),
[pgJDBC connection parameter precedence](https://jdbc.postgresql.org/documentation/use/).

## Verification recorded on 2026-10-15

- Java 21 Maven `verify`: **45 tests passed, zero failures/errors/skips**, including
  nine connection-configuration regressions and the production-profile TLS check.
  This used a fresh, disposable PostgreSQL 18 database bound to loopback; migrations
  V1 through V5 all completed there. No live Supabase data was changed.
- The packaged JAR ran with the production profile, Flyway disabled, TLS PostgreSQL,
  and a 256 MiB Java heap. Real HTTP requests returned `/health` 200, `/ready` 200,
  and unauthenticated `/api/auth/verify` 401.
- Three Python reminder-script tests passed; the source credential scan reported
  no high-confidence findings. Docker is not installed here, so no local container
  build/runtime result is claimed.
- These are local results. The live Render health/readiness requests timed out;
  live configuration changes and deployment verification still require Render access.
