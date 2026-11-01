-- Reviewed infrastructure setup for the AnkiDSA Supabase project only.
-- Apply only AFTER deploying ReminderDispatchCoordinator with notifications enabled.
-- No credentials, study data, account data, or FCM tokens enter pg_net requests.
-- pg_cron success means enqueued, not delivered. Inspect net._http_response promptly
-- (default retention ~6h) and reminder_deliveries for actual provider outcomes.
BEGIN;

DO $guard$
BEGIN
    IF current_user <> 'postgres' OR current_database() <> 'postgres' THEN
        RAISE EXCEPTION 'Use the reviewed project owner connection';
    END IF;
    IF coalesce(current_setting('cron.timezone', true), '') NOT IN ('GMT', 'UTC') THEN
        RAISE EXCEPTION 'Wake schedules require UTC cron.timezone';
    END IF;
    IF to_regclass('public.reminder_deliveries') IS NULL
       OR to_regclass('public.reminder_devices') IS NULL THEN
        RAISE EXCEPTION 'Expected AnkiDSA reminder schema is missing';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_namespace
        WHERE nspname = 'ankidsa_ops'
          AND (nspowner <> 'postgres'::regrole
               OR obj_description(oid, 'pg_namespace') IS DISTINCT FROM
                  'AnkiDSA reminder wake scheduling; managed by supabase-reminder-wake.sql')
    ) THEN
        RAISE EXCEPTION 'Refusing to alter an unrelated ankidsa_ops schema';
    END IF;
END
$guard$;

CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;
CREATE SCHEMA IF NOT EXISTS ankidsa_ops AUTHORIZATION postgres;
COMMENT ON SCHEMA ankidsa_ops IS
    'AnkiDSA reminder wake scheduling; managed by supabase-reminder-wake.sql';
REVOKE ALL ON SCHEMA ankidsa_ops FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION ankidsa_ops.wake_reminders()
RETURNS bigint
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $function$
    SELECT net.http_get(
        url := 'https://ankidsa-backend.onrender.com/ready',
        params := '{}'::jsonb,
        headers := '{}'::jsonb,
        timeout_milliseconds := 300000
    );
$function$;
REVOKE ALL ON FUNCTION ankidsa_ops.wake_reminders()
    FROM PUBLIC, anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION ankidsa_ops.prune_reminder_wake_history()
RETURNS void
LANGUAGE sql
SECURITY INVOKER
SET search_path = ''
AS $function$
    DELETE FROM cron.job_run_details d
    USING cron.job j
    WHERE d.jobid = j.jobid AND j.username = current_user
      AND j.jobname IN (
          'ankidsa-reminder-wake-primary', 'ankidsa-reminder-wake-retry',
          'ankidsa-reminder-wake-last', 'ankidsa-reminder-wake-retention'
      )
      AND d.start_time < now() - interval '7 days';
$function$;
REVOKE ALL ON FUNCTION ankidsa_ops.prune_reminder_wake_history()
    FROM PUBLIC, anon, authenticated, service_role;

DO $guard$
BEGIN
    IF EXISTS (
        SELECT 1 FROM cron.job
        WHERE username = current_user
          AND ((jobname IN (
                  'ankidsa-reminder-wake-primary', 'ankidsa-reminder-wake-retry',
                  'ankidsa-reminder-wake-last'
                ) AND command <> 'SELECT ankidsa_ops.wake_reminders();')
               OR (jobname = 'ankidsa-reminder-wake-retention'
                   AND command <> 'SELECT ankidsa_ops.prune_reminder_wake_history();'))
    ) THEN
        RAISE EXCEPTION 'Refusing to overwrite an unrelated named cron job';
    END IF;
END
$guard$;

-- All cron expressions are UTC. Four first-window pulses cover cold starts and retries.
-- Asia/Kolkata: 06:30, 06:35, 06:40, 06:45.
SELECT cron.schedule('ankidsa-reminder-wake-primary', '0,5,10,15 1 * * *',
    'SELECT ankidsa_ops.wake_reminders();');
-- Asia/Kolkata: 09:30, 09:35, 09:40.
SELECT cron.schedule('ankidsa-reminder-wake-retry', '0,5,10 4 * * *',
    'SELECT ankidsa_ops.wake_reminders();');
-- Asia/Kolkata: 11:30, 11:35, 11:40; delivery window still ends at 12:00.
SELECT cron.schedule('ankidsa-reminder-wake-last', '0,5,10 6 * * *',
    'SELECT ankidsa_ops.wake_reminders();');
-- Prune only these four jobs' history; never application or other job records.
SELECT cron.schedule('ankidsa-reminder-wake-retention', '20 0 * * *',
    'SELECT ankidsa_ops.prune_reminder_wake_history();');

COMMIT;

-- Safe metadata verification: contains no request headers or user data.
SELECT jobname, schedule, active
FROM cron.job
WHERE username = current_user
  AND jobname IN ('ankidsa-reminder-wake-primary', 'ankidsa-reminder-wake-retry',
                  'ankidsa-reminder-wake-last', 'ankidsa-reminder-wake-retention')
ORDER BY jobname;
