import contextlib, importlib.util, io, json, os, pathlib, unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('trigger', pathlib.Path(__file__).with_name('trigger-reminders.py'))
trigger = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trigger)

class Response(io.BytesIO):
    status = 200
    def __init__(self, value): super().__init__(json.dumps(value).encode())

class TriggerTest(unittest.TestCase):
    def run_with(self, results):
        calls = []
        self.requests = calls
        class Opener:
            def open(self, request, timeout):
                if isinstance(request, str): return Response({'status': 'ready'})
                calls.append(request)
                return Response(results.pop(0) if len(results) > 1 else results[0])
        output = io.StringIO()
        self.output = output
        with patch.dict(os.environ, {'API_PUBLIC_URL':'https://api.example.test', 'REMINDER_TRIGGER_SECRET':'fixture-'+'x'*48}, clear=True), patch.object(trigger, 'build_opener', return_value=Opener()), patch.object(trigger.time, 'sleep'), contextlib.redirect_stdout(output):
            trigger.run()
        return calls, output.getvalue()
    def test_readiness_retries_quick_503s_until_server_wakes(self):
        clock = [0]
        probes, posts = [], []
        class Opener:
            def open(self, request, timeout):
                if isinstance(request, str):
                    probes.append(request)
                    if clock[0] < 240:
                        raise trigger.HTTPError(request, 503, 'Waking', {}, None)
                    return Response({'status': 'ready'})
                posts.append(request)
                return Response({'status': 'complete', 'remaining': 0})
        def sleep(seconds): clock[0] += seconds
        with patch.dict(os.environ, {'API_PUBLIC_URL':'https://api.example.test', 'REMINDER_TRIGGER_SECRET':'fixture-'+'x'*48}, clear=True), patch.object(trigger, 'build_opener', return_value=Opener()), patch.object(trigger.time, 'monotonic', side_effect=lambda: clock[0]), patch.object(trigger.time, 'sleep', side_effect=sleep), contextlib.redirect_stdout(io.StringIO()):
            trigger.run()
        self.assertGreater(len(probes), 6)
        self.assertEqual(len(posts), 1)
        self.assertEqual(clock[0], 240)

    def test_http_200_loading_page_is_not_mistaken_for_readiness(self):
        clock = [0]
        calls = []
        class Opener:
            def open(self, request, timeout):
                if isinstance(request, str):
                    return Response({'status':'starting' if clock[0] < 20 else 'ready'})
                calls.append(request)
                return Response({'status':'complete','remaining':0})
        def sleep(seconds): clock[0] += seconds
        with patch.dict(os.environ, {'API_PUBLIC_URL':'https://api.example.test', 'REMINDER_TRIGGER_SECRET':'fixture-'+'x'*48}, clear=True), patch.object(trigger, 'build_opener', return_value=Opener()), patch.object(trigger.time, 'monotonic', side_effect=lambda: clock[0]), patch.object(trigger.time, 'sleep', side_effect=sleep), contextlib.redirect_stdout(io.StringIO()):
            trigger.run()
        self.assertEqual(clock[0],20)
        self.assertEqual(len(calls),1)

    def test_unavailable_readiness_stops_at_deadline_without_triggering(self):
        clock = [0]
        class Opener:
            def open(self, request, timeout):
                if not isinstance(request, str): raise AssertionError('Must not send a trigger')
                raise trigger.HTTPError(request, 503, 'Unavailable', {}, None)
        def sleep(seconds): clock[0] += seconds
        with patch.dict(os.environ, {'API_PUBLIC_URL':'https://api.example.test', 'REMINDER_TRIGGER_SECRET':'fixture-'+'x'*48}, clear=True), patch.object(trigger, 'build_opener', return_value=Opener()), patch.object(trigger.time, 'monotonic', side_effect=lambda: clock[0]), patch.object(trigger.time, 'sleep', side_effect=sleep):
            with self.assertRaisesRegex(RuntimeError, 'readiness timed out'): trigger.run()
        self.assertEqual(clock[0], 300)

    def test_duplicate_busy_trigger_waits_and_never_logs_secret_or_extra_payload(self):
        calls, output = self.run_with([{'status':'busy','remaining':0}, {'status':'complete','remaining':0,'sent':1,'private':'must-not-print'}])
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[0].method, 'POST')
        self.assertTrue(calls[0].get_header('X-reminder-secret').startswith('fixture-'))
        self.assertNotIn('fixture-', output)
        self.assertNotIn('must-not-print', output)
    def test_backlog_is_bounded_and_reported_as_failure(self):
        with self.assertRaises(RuntimeError): self.run_with([{'status':'complete','remaining':2}])
    def test_expired_or_ineligible_devices_are_successful_skips(self):
        calls, output = self.run_with([{'status':'complete','remaining':0,'failed':0,'skipped':2}])
        self.assertEqual(len(calls), 1)
        self.assertIn('"skipped": 2', output)

    def test_permanent_failure_does_not_starve_remaining_devices(self):
        results = [{'status':'complete','remaining':1,'failed':1}, {'status':'complete','remaining':0,'sent':1}]
        with self.assertRaisesRegex(trigger.TriggerError, 'delivery_failed'):
            self.run_with(results)
        self.assertEqual(len(self.requests), 2, 'The successful second batch must be attempted')

    def test_outside_window_is_actionable_not_silent_success(self):
        with self.assertRaisesRegex(trigger.TriggerError, 'outside_delivery_window'):
            self.run_with([{'status':'outside_window','remaining':0}])

    def test_untrusted_response_status_is_not_logged(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            with self.assertRaisesRegex(trigger.TriggerError, 'response_invalid'):
                self.run_with([{'status':'fixture-secret-must-not-print','remaining':0}])
        self.assertNotIn('fixture-secret-must-not-print', self.output.getvalue())

    def test_missing_remaining_count_is_not_false_success(self):
        with self.assertRaisesRegex(trigger.TriggerError, 'response_invalid'):
            self.run_with([{'status':'complete','sent':1}])

    def test_disabled_jobs_are_not_reported_successful(self):
        with self.assertRaises(RuntimeError): self.run_with([{'status':'disabled','remaining':0}])

if __name__ == '__main__': unittest.main()
