# Deploying the AnkiDSA backend

Use Java 21 and Maven 3.9+. Run `./scripts/maven.ps1 -B verify`, or build the included multi-stage Dockerfile.

Required environment:

```text
DB_URL=jdbc:postgresql://YOUR_HOST:5432/postgres?sslmode=require
DB_USER=your_application_role
DB_PASSWORD=your_rotated_password
JWT_SECRET=your_random_secret_with_at_least_32_bytes
CORS_ORIGINS=https://YOUR_FLUTTER_HOST,chrome-extension://bafnmcdkpidnpollodebmifggmmpkbje
APP_TIMEZONE=Asia/Kolkata
```

Rotate the database password exposed in the original source before deployment. Keep secrets, local database files, service-account keys, and source backups private.

The server listens on port 8081, configurable with PORT. Put it behind HTTPS and verify GET /health. API documentation is disabled unless API_DOCS_ENABLED=true.

Flyway applies V1–V4 to fresh databases; Hibernate validates the schema. Before upgrading an existing database, verify a backup and rehearse on a copy. Confirm the legacy schema matches V1, baseline once at version 1, and let V2–V4 run. Remove the baseline override afterwards. Never run the old root schema.sql alongside Flyway.

V2 adds password hashes, versions, and review request IDs. V3 adds password recovery and session versions. V4 adds device registrations and clears legacy push tokens so clients can request consent again. Users must sign in again after the session-version upgrade.

## Password recovery

Set APP_PUBLIC_URL to the HTTPS Flutter app origin, RECOVERY_ENABLED=true, and configure MAIL_HOST, MAIL_PORT (587 by default), MAIL_FROM, MAIL_USER, and MAIL_PASSWORD. SMTP authentication and required STARTTLS default to true. See .env.example for the complete configuration.

Verify the sender domain and real inbox delivery with your mail provider before public signup. Automated mail tests use a local SMTP server or mocks. Reset tokens are random, hashed in storage, valid for 30 minutes, and single-use. Responses do not disclose account existence; requests have an account cooldown and an IP rate limit. A successful reset revokes all earlier sessions and device registrations and sends a change notice.

Legacy accounts without passwords can recover access by proving mailbox ownership through this flow. Google OAuth is not implemented.

## Reminders

Set NOTIFICATIONS_ENABLED=true and supply Firebase Application Default Credentials. Prefer a managed service identity. If using a service-account file in Docker, mount it privately and set GOOGLE_APPLICATION_CREDENTIALS to its container path.

Clients opt in through their notification settings. PUT /api/users/me/device registers an FCM token; DELETE at the same path removes only the authenticated user's matching token. Both accept {"token":"..."} and return 204. Multiple devices are supported and one token has one owner. Resetting a password removes all of that account's registrations.

The scheduler runs at 06:30 in APP_TIMEZONE and sends generic reminders only when problems are due. Tokens inactive for 30 days and tokens rejected as unregistered are removed. Run only one scheduler instance. Configure the Flutter app with public Firebase client values and verify real-device delivery before offering push to users; simulated tests do not establish provider delivery.

## Operations and local demo

Configure trusted proxy handling and shared rate limits at the gateway when scaling beyond one API instance. Do not trust arbitrary forwarded client addresses. Back up the database, monitor errors, and rehearse restores.

Run ./scripts/run-demo.ps1 for a local backend, or the Flutter project's scripts/start-demo.ps1 for both API and web app. The demo uses a private local H2 file, a sample account, and a local .local/outbox mailbox. It never uses SMTP for recovery. Keep the demo profile out of production.

The Flutter project's DEMO.md, TESTING.md, and PRODUCTION.md cover the complete system, client configuration, native signing, recording, and remaining deployment checks.
