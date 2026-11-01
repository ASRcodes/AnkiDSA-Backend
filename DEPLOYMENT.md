# Backend deployment entry point

For the Supabase `ENOIDENTIFIER` startup failure, start with [RENDER-FIX.md](RENDER-FIX.md).
Older revisions used only `DB_USER`; the deployed code prefers
`DB_USERNAME` and also supports `DB_USER`. Both need the complete pooler username
(`postgres.<project-ref>`), not just `postgres`.

The API can start before the web frontend is deployed: leave `APP_PUBLIC_URL` empty
with `RECOVERY_ENABLED=false`, set `API_PUBLIC_URL` to its Render HTTPS URL, and use
only the known Chrome extension origin in `CORS_ORIGINS`. Once Netlify allocates the
web URL, add that exact HTTPS origin to CORS and set `APP_PUBLIC_URL`. Password
recovery requires both that frontend URL and a working mail provider.

Current release: API https://ankidsa-backend.onrender.com and web
https://ankidsa.netlify.app are live. Render has the exact Netlify and extension
CORS origins configured. Brevo password recovery and Android FCM are configured;
password-reset inbox tests and direct phone delivery have passed. Daily scheduling
is verified separately after infrastructure changes; see [REMINDER-OPERATIONS.md](REMINDER-OPERATIONS.md).

The complete owner-facing guide is maintained in the companion [AnkiDSA frontend DEPLOYMENT.md](https://github.com/ASRcodes/AnkiDSA-frontend/blob/main/DEPLOYMENT.md). In this workspace it is `C:\Users\rajsi\ankidsa\DEPLOYMENT.md`. Publish both reviewed repository revisions together; the link will contain the new guide only after you push these changes.

Use one **Render Free Web Service**, Docker/Java 21, `plan: free`, no disk/worker/cron/database service. Keep existing **Supabase Free PostgreSQL data**. `render.yaml` is optional configuration, not an instruction to deploy automatically. Health `/health`, database readiness `/ready`. Normal production startup has `FLYWAY_ENABLED=false` and Hibernate `validate`.

Before deployment:

1. Verify existing project reference, schema, migrations, data counts, Data API grants and TLS session-pooler connection. Run `scripts/verify-database.sql` read-only. Create an external encrypted backup and restore it into a disposable local database to verify it. Do not point test scripts at Supabase.
2. Build with `mvn -B verify` / `scripts/maven.ps1 -B verify`. Set private process `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, plus `EXPECTED_DATABASE` and `EXPECTED_DATABASE_ROLE`. Run `java -jar target/ankidsa-backend-1.0.0.jar --verify-database`.
3. Review pending SQL. V1–V4 are unchanged; legacy V4 clears `users.fcm_token`, so review it explicitly if pending. V5 is additive. After verified backup/review set maintenance-only `BACKUP_VERIFIED=true`, `MIGRATION_APPROVED=true`, and `LEGACY_V4_REVIEWED=true` only if appropriate; run `--migrate-database`. No automatic baseline, clean, reset, DROP or TRUNCATE is permitted. Missing or conflicting Flyway history requires manual reconciliation.
4. In Render connect this GitHub repo/root Dockerfile. Choose Free, disable auto deploy, health check `/ready`. Enter every value in `.env.example` privately, replacing placeholders. Use exact Pages/Render HTTPS origins; secrets never go into client bundles.
5. Configure Brevo Free verified sender/API key, Firebase Spark FCM HTTP v1/least-privilege service account. Upload Firebase JSON as Render secret file `firebase-service-account.json`; set `GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-service-account.json`. Set notifications/recovery true only with functioning credentials. Brevo sender approval without an owned domain is an external launch gate.
6. Backend Actions `checks.yml` has no provider secrets. After deploying the automatic reminder coordinator, apply the reviewed [Supabase wake setup](ops/supabase-reminder-wake.sql) using the intended project's owner connection. Its ten morning public `/ready` requests contain no secrets; the backend sends within 06:30–12:00 Asia/Kolkata using the existing lease/claim ledger. Create a main-only `reminders` environment with `API_PUBLIC_URL` and an independent `REMINDER_TRIGGER_SECRET`, then set repository variable `REMINDERS_ENABLED=true`. GitHub now performs a read-only audit at 12:47 Asia/Kolkata; manual workflow mode `deliver` tests the protected sender during the morning window. Follow [REMINDER-OPERATIONS.md](REMINDER-OPERATIONS.md) for verification, proof limits and rollback. Delivery remains best effort, including the crash-after-FCM-acceptance duplicate boundary.
7. Optional deployment workflow uses main-only `production` environment `RENDER_DEPLOY_HOOK`; run only after required validation passed on the intended latest main revision. No paid overages or billing method.


For the current public-only Render Free web service, set `CLIENT_IP_MODE=render-cloudflare`
with `TRUSTED_PROXY_CIDRS` empty. [Render documents](https://render.com/articles/host-pocketbase-on-render#making-pocketbase-see-the-real-client-ip)
that its Cloudflare ingress overwrites `CF-Connecting-IP`; caller-supplied entries can
remain in `X-Forwarded-For`. This mode validates a single literal IP and ignores those
other forwarding headers. Missing, malformed or duplicate client-IP headers share
one conservative rate-limit bucket. Startup requires Render's own `RENDER=true`,
`RENDER_SERVICE_TYPE=web`, and `RENDER_EXTERNAL_URL` to match `API_PUBLIC_URL` exactly
as an HTTPS `onrender.com` origin. Do not set these runtime identity variables manually.
This avoids putting every tester into the proxy's shared login limit. The default
`cidr` mode retains the configured trusted-proxy chain for other deployments.
[Free Render services cannot receive private network traffic](https://render.com/docs/free#other-limitations);
review this public-ingress assumption before changing plans, adding private ingress,
introducing another proxy, or changing the public hostname. No request IPs are logged
or exposed by this configuration.

Tests: H2 and disposable loopback PostgreSQL, plus notification/email adapter mocks/local HTTP. Docker build/runtime checks are in CI and must pass before launch; Docker is not installed in the current local workspace. No live provider test is implied by mocks.

Rollback the application to a reviewed compatible artifact while retaining additive tables and all study data. Do not down-migrate or restore over the live database. Restore only into a separate recovery database pending explicit owner review. Pause reminder jobs while investigating failures. Rotate exposed secrets; never reintroduce compromised credentials from old artifacts.
