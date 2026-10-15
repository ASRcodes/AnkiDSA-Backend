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
        class Opener:
            def open(self, request, timeout):
                if isinstance(request, str): return Response({'status': 'ready'})
                calls.append(request)
                return Response(results.pop(0) if len(results) > 1 else results[0])
        output = io.StringIO()
        with patch.dict(os.environ, {'API_PUBLIC_URL':'https://api.example.test', 'REMINDER_TRIGGER_SECRET':'fixture-'+'x'*48}, clear=True), patch.object(trigger, 'build_opener', return_value=Opener()), patch.object(trigger.time, 'sleep'), contextlib.redirect_stdout(output):
            trigger.run()
        return calls, output.getvalue()
    def test_duplicate_busy_trigger_waits_and_never_logs_secret_or_extra_payload(self):
        calls, output = self.run_with([{'status':'busy','remaining':0}, {'status':'complete','remaining':0,'sent':1,'private':'must-not-print'}])
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[0].method, 'POST')
        self.assertTrue(calls[0].get_header('X-reminder-secret').startswith('fixture-'))
        self.assertNotIn('fixture-', output)
        self.assertNotIn('must-not-print', output)
    def test_backlog_is_bounded_and_reported_as_failure(self):
        with self.assertRaises(RuntimeError): self.run_with([{'status':'complete','remaining':2}])
    def test_disabled_jobs_are_not_reported_successful(self):
        with self.assertRaises(RuntimeError): self.run_with([{'status':'disabled','remaining':0}])

if __name__ == '__main__': unittest.main()
