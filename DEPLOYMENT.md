# Backend deployment entry point

For the Supabase `ENOIDENTIFIER` startup failure, start with [RENDER-FIX.md](RENDER-FIX.md).
The existing GitHub revision uses `DB_USER`; the local deployment code prefers
`DB_USERNAME` and also supports `DB_USER`. Both need the complete pooler username
(`postgres.<project-ref>`), not just `postgres`.

The API can start before the web frontend is deployed: leave `APP_PUBLIC_URL` empty
with `RECOVERY_ENABLED=false`, set `API_PUBLIC_URL` to its Render HTTPS URL, and use
only the known Chrome extension origin in `CORS_ORIGINS`. Once Netlify allocates the
web URL, add that exact HTTPS origin to CORS and set `APP_PUBLIC_URL`. Password
recovery requires both that frontend URL and a working mail provider.

The complete owner-facing guide is maintained in the companion [AnkiDSA frontend DEPLOYMENT.md](https://github.com/ASRcodes/AnkiDSA-frontend/blob/main/DEPLOYMENT.md). In this workspace it is `C:\Users\rajsi\ankidsa\DEPLOYMENT.md`. Publish both reviewed repository revisions together; the link will contain the new guide only after you push these changes.

Use one **Render Free Web Service**, Docker/Java 21, `plan: free`, no disk/worker/cron/database service. Keep existing **Supabase Free PostgreSQL data**. `render.yaml` is optional configuration, not an instruction to deploy automatically. Health `/health`, database readiness `/ready`. Normal production startup has `FLYWAY_ENABLED=false` and Hibernate `validate`.

Before deployment:

1. Verify existing project reference, schema, migrations, data counts, Data API grants and TLS session-pooler connection. Run `scripts/verify-database.sql` read-only. Create an external encrypted backup and restore it into a disposable local database to verify it. Do not point test scripts at Supabase.
2. Build with `mvn -B verify` / `scripts/maven.ps1 -B verify`. Set private process `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, plus `EXPECTED_DATABASE` and `EXPECTED_DATABASE_ROLE`. Run `java -jar target/ankidsa-backend-1.0.0.jar --verify-database`.
3. Review pending SQL. V1–V4 are unchanged; legacy V4 clears `users.fcm_token`, so review it explicitly if pending. V5 is additive. After verified backup/review set maintenance-only `BACKUP_VERIFIED=true`, `MIGRATION_APPROVED=true`, and `LEGACY_V4_REVIEWED=true` only if appropriate; run `--migrate-database`. No automatic baseline, clean, reset, DROP or TRUNCATE is permitted. Missing or conflicting Flyway history requires manual reconciliation.
4. In Render connect this GitHub repo/root Dockerfile. Choose Free, disable auto deploy, health check `/ready`. Enter every value in `.env.example` privately, replacing placeholders. Use exact Pages/Render HTTPS origins; secrets never go into client bundles.
5. Configure Brevo Free verified sender/API key, Firebase Spark FCM HTTP v1/least-privilege service account. Upload Firebase JSON as Render secret file `firebase-service-account.json`; set `GOOGLE_APPLICATION_CREDENTIALS=/etc/secrets/firebase-service-account.json`. Set notifications/recovery true only with functioning credentials. Brevo sender approval without an owned domain is an external launch gate.
6. Backend Actions `checks.yml` has no provider secrets. Create main-only `reminders` environment with `API_PUBLIC_URL` variable and independent `REMINDER_TRIGGER_SECRET` secret. Set repository variable `REMINDERS_ENABLED=true` only after notification delivery is configured, then manually test `reminders.yml` inside 06:30–12:00 Asia/Kolkata. GHA triggers four morning batches, handles cold starts, and uses a database lease/claim ledger. Delivery remains best effort, with an unavoidable crash-after-FCM-acceptance duplicate boundary.
7. Optional deployment workflow uses main-only `production` environment `RENDER_DEPLOY_HOOK`; run only after required validation passed on the intended latest main revision. No paid overages or billing method.

Tests: H2 and disposable loopback PostgreSQL, plus notification/email adapter mocks/local HTTP. Docker build/runtime checks are in CI and must pass before launch; Docker is not installed in the current local workspace. No live provider test is implied by mocks.

Rollback the application to a reviewed compatible artifact while retaining additive tables and all study data. Do not down-migrate or restore over the live database. Restore only into a separate recovery database pending explicit owner review. Pause reminder jobs while investigating failures. Rotate exposed secrets; never reintroduce compromised credentials from old artifacts.
