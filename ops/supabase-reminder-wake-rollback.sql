-- Disable only the AnkiDSA jobs created by supabase-reminder-wake.sql.
-- Shared extensions, schemas, application data and unrelated jobs are preserved.
BEGIN;
DO $rollback$
DECLARE
    owned_job record;
BEGIN
    IF current_user <> 'postgres' OR current_database() <> 'postgres' THEN
        RAISE EXCEPTION 'Use the reviewed project owner connection';
    END IF;
    IF to_regnamespace('ankidsa_ops') IS NULL OR to_regclass('cron.job') IS NULL THEN
        RETURN;
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
    FOR owned_job IN
        SELECT jobid FROM cron.job
        WHERE username = current_user
          AND ((jobname IN ('ankidsa-reminder-wake-primary',
                           'ankidsa-reminder-wake-retry', 'ankidsa-reminder-wake-last')
                AND command = 'SELECT ankidsa_ops.wake_reminders();')
               OR (jobname = 'ankidsa-reminder-wake-retention'
                   AND command = 'SELECT ankidsa_ops.prune_reminder_wake_history();'))
    LOOP
        PERFORM cron.unschedule(owned_job.jobid);
    END LOOP;
END
$rollback$;
COMMIT;
