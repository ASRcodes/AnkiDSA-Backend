-- AnkiDSA uses the Spring API over JDBC, never Supabase anon/authenticated REST.
-- Run as the application table owner after reviewing the exact table list and
-- backing up grants. This does not change other project tables or schema defaults.
BEGIN;
REVOKE ALL PRIVILEGES ON TABLE
    public.users, public.problems, public.review_logs,
    public.password_reset_tokens, public.reminder_devices,
    public.revoked_sessions, public.job_leases, public.reminder_deliveries,
    public.mail_budget, public.mail_reservations, public.flyway_schema_history
FROM anon, authenticated, PUBLIC;
REVOKE ALL PRIVILEGES ON SEQUENCE
    public.users_id_seq, public.problems_id_seq, public.review_logs_id_seq
FROM anon, authenticated, PUBLIC;
COMMIT;
