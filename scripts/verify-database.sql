-- Read-only. Run on the verified existing project; save results outside Git.
BEGIN READ ONLY;
SELECT current_database(), current_user, current_schema(), version();
SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;
SELECT installed_rank, version, description, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;
SELECT 'users' AS table_name, count(*) FROM public.users
UNION ALL SELECT 'problems', count(*) FROM public.problems
UNION ALL SELECT 'review_logs', count(*) FROM public.review_logs;
SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public';
SELECT conrelid::regclass, conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE connamespace = 'public'::regnamespace;
SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname = 'public';
SELECT grantee, table_name, privilege_type FROM information_schema.role_table_grants
WHERE table_schema = 'public' AND grantee IN ('anon', 'authenticated');
COMMIT;
