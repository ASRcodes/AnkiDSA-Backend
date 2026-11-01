# Reminder operations

## What failed and what changed

The October 29–November 1 incident was a scheduling failure. GitHub's scheduled jobs began after the backend's 06:30–12:00 Asia/Kolkata delivery window. The backend returned `outside_window`; the workflow treated that as an error and emailed failures without sending reminders. Previous foreground, background and closed-app phone tests verified direct FCM delivery, not this daily scheduling path.

The backend now dispatches automatically after application readiness and every 60 seconds while it remains awake. Automatic and manual dispatch share `SchedulerService`, its original morning window, database lease, bounded retries and per-device daily delivery ledger. Readiness is not delayed by notification sending. Disabled notifications do not create the automatic coordinator.

Supabase `pg_cron` and `pg_net` wake the existing Render service with a public `GET /ready`. These requests contain no credentials, FCM tokens, account data or study data. They do not invoke the protected sender endpoint. This keeps provider credentials in Render and removes GitHub schedule timing from the delivery path.

## Provisioning and schedule

1. Deploy the reviewed backend revision with `NOTIFICATIONS_ENABLED=true`, working Firebase credentials, and the automatic coordinator. Keep the existing database and migrations.
2. Verify the intended Supabase project, use its owner connection, and apply [ops/supabase-reminder-wake.sql](ops/supabase-reminder-wake.sql). The script checks the owner, UTC cron timezone, expected application tables, and ownership of any existing names. It creates only the dedicated operations schema/functions and four named jobs; it does not alter study/account tables.
3. Check the returned job names, schedules and active state. Existing jobs with these names but different commands are rejected instead of overwritten.

Ten public wake requests are scheduled each day:

| Job | UTC | Asia/Kolkata |
| --- | --- | --- |
| `ankidsa-reminder-wake-primary` | 01:00, 01:05, 01:10, 01:15 | 06:30, 06:35, 06:40, 06:45 |
| `ankidsa-reminder-wake-retry` | 04:00, 04:05, 04:10 | 09:30, 09:35, 09:40 |
| `ankidsa-reminder-wake-last` | 06:00, 06:05, 06:10 | 11:30, 11:35, 11:40 |

A fourth job, `ankidsa-reminder-wake-retention`, runs at 00:20 UTC and removes only these four jobs' history older than seven days. The HTTP timeout is five minutes. A timed-out wake request does not prove that the container failed to start; inspect readiness and backend batch evidence before retrying infrastructure changes. This schedule uses existing Free services and does not request a paid worker, plan change or continuous all-day keep-alive.

## Delivery audit and manual verification

The `Daily reminder delivery` GitHub workflow now schedules one **read-only audit** at 07:17 UTC / 12:47 Asia/Kolkata. A delayed run can inspect results without sending afternoon notifications. Repository variable `REMINDERS_ENABLED=true` enables it; the main-only `reminders` environment holds `API_PUBLIC_URL` and the independent `REMINDER_TRIGGER_SECRET`. The `deliver` mode is available only through manual workflow dispatch and still respects the morning window. Manual dispatch defaults to `audit`.

The runner first waits for `/ready`, then authenticates `GET /internal/reminders` using `X-Reminder-Secret`. Ordinary account JWTs cannot access the endpoint. It returns only aggregate fields:

| Field | Meaning |
| --- | --- |
| `day`, `windowClosed` | Study date and whether its morning window has ended. |
| `ranToday` | A valid successful batch marker exists from that morning. |
| `sent` | Today's ledger entries accepted by FCM. This is not proof of phone receipt. |
| `failed` | Actionable terminal failures, excluding ineligible/expired devices. |
| `skipped` | Reviews completed, opt-out/account changes, or unregistered tokens that prevented a send. |
| `pending` | Currently eligible staged work still awaiting delivery. |
| `lastBatchRemaining` | Total unfinished work when the last morning batch completed, including devices not staged yet. This is historical evidence; an afternoon registration does not increase it. |

The marker uses the existing `job_leases` row named `reminders-last-success`, a completion timestamp and an `owner` value formatted `remaining:N`. It is written only after staging, delivery and aggregate counting succeed. Releasing the dispatch lock after an exception does not create success evidence. A later completed batch updates the count to zero when the backlog drains. No database migration is needed for this marker.

An audit after the window fails for a missing successful cycle, actionable failures, eligible pending work, or a nonzero historical remainder. The historical count can remain nonzero if users subsequently complete reviews; inspect the ledger and eligibility before interpreting it as a current missed delivery. Ineligible and unregistered-token outcomes remain the schema's terminal `FAILED` rows with their distinct codes, but are reported as `skipped`, avoiding false workflow alerts.

## Evidence and troubleshooting

Check each stage independently:

1. `cron.job` confirms the intended active schedules. `cron.job_run_details` records the enqueue job, not the HTTP or FCM outcome.
2. Inspect `pg_net` response status promptly; its response history is short-lived. HTTP 200 from `/ready` establishes database readiness only.
3. Render logs show aggregate reminder batch counts. Firebase warnings contain fixed reason labels and SDK error enums, never raw exception text, device tokens or account details.
4. The protected audit and `reminder_deliveries` ledger establish successful dispatch, provider acceptance, skip reasons and retry/backlog state. A real phone must confirm receipt and opening its review queue.

Only enabled, recently registered devices with due questions are eligible. A zero-send completed cycle can be healthy when nobody has work due. Do not change real review dates or bypass the morning window merely to make a test pass. Repeated triggers preserve daily deduplication, but an unavoidable crash after FCM acceptance and before recording `SENT` can cause a duplicate retry.

## Rollback

Apply [ops/supabase-reminder-wake-rollback.sql](ops/supabase-reminder-wake-rollback.sql) to unschedule only the matching AnkiDSA jobs. It preserves extensions, functions, unrelated jobs and all application data. Disabling wake jobs alone does not disable delivery while the backend is awake; set `NOTIFICATIONS_ENABLED=false` and redeploy if an immediate delivery pause is needed. Disable the GitHub audit variable during intentional maintenance so its expected missing-cycle result does not generate failure mail.

Roll back the application only to a reviewed compatible artifact. Retain additive tables, delivery records and study data. Do not down-migrate, delete registrations or restore over the live database. Keep current private credentials out of source and artifacts.
