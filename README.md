# AnkiDSA-Backend
AnkiDSA's backend.

Live API: https://ankidsa-backend.onrender.com. Web app: https://ankidsa.netlify.app.
The 2026-10-15 repair and live verification are recorded in [RENDER-FIX.md](RENDER-FIX.md).

Render failing with `ENOIDENTIFIER` / `no tenant identifier provided`? Follow
[RENDER-FIX.md](RENDER-FIX.md) for the exact Supabase pooler settings, including the
`DB_USER` name required by older deployed revisions.

Java 21 / Spring Boot API for the existing Flutter and Chrome clients. Production uses Render Free Docker and the existing Supabase PostgreSQL project. See [DEPLOYMENT.md](DEPLOYMENT.md), [.env.example](.env.example), and the frontend repository's complete deployment guide.

Run `mvn -B verify` (Windows: `scripts/maven.ps1 -B verify`). Tests reject hosted database targets. `production` and `staging` validate HTTPS/TLS configuration and disable automatic Flyway migrations. Never reset/baseline/clean an existing Supabase database. Run the explicit maintenance command only after verifying identity and an external backup.
