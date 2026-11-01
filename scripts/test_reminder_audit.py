import contextlib, importlib.util, io, json, pathlib, unittest

spec=importlib.util.spec_from_file_location('trigger',pathlib.Path(__file__).with_name('trigger-reminders.py'))
trigger=importlib.util.module_from_spec(spec)
spec.loader.exec_module(trigger)

class AuditTest(unittest.TestCase):
    def audit(self, **overrides):
        result={'status':'audit','day':'2026-11-01','windowClosed':True,'ranToday':True,'sent':0,'failed':0,'skipped':0,'pending':0,'lastBatchRemaining':0,**overrides}
        class Response(io.BytesIO):
            def __init__(self): super().__init__(json.dumps(result).encode())
        requests=[]
        class Opener:
            def open(self, request, timeout):
                requests.append(request)
                return Response()
        self.requests=requests
        output=io.StringIO()
        with contextlib.redirect_stdout(output):
            trigger.check_delivery(Opener(),'https://api.example.test','fixture-'+'x'*48,trigger.time.monotonic()+30)
        return output.getvalue()

    def test_late_audit_reads_completed_delivery_without_posting(self):
        output=self.audit(sent=1,private='never-log-this')
        self.assertEqual(len(self.requests),1)
        self.assertEqual(self.requests[0].method,'GET')
        self.assertNotIn('never-log-this',output)
        self.assertNotIn('fixture-',output)

    def test_successful_empty_batch_is_healthy_but_no_batch_is_not(self):
        self.audit(sent=0)
        with self.assertRaisesRegex(trigger.TriggerError,'scheduler_missed'):
            self.audit(ranToday=False)

    def test_failed_or_pending_deliveries_alert(self):
        with self.assertRaisesRegex(trigger.TriggerError,'delivery_failed'): self.audit(failed=1)
        with self.assertRaisesRegex(trigger.TriggerError,'delivery_pending'): self.audit(pending=1)

    def test_unstaged_backlog_in_last_batch_is_not_false_healthy(self):
        with self.assertRaisesRegex(trigger.TriggerError,'morning_backlog'):
            self.audit(pending=0,lastBatchRemaining=5)

    def test_skipped_devices_and_in_progress_window_are_not_errors(self):
        self.audit(skipped=3)
        self.audit(windowClosed=False,ranToday=False,pending=1)

    def test_malformed_summary_does_not_pass(self):
        with self.assertRaisesRegex(trigger.TriggerError,'response_invalid'): self.audit(ranToday='yes')
        with self.assertRaisesRegex(trigger.TriggerError,'response_invalid'): self.audit(pending=-1)
        with self.assertRaisesRegex(trigger.TriggerError,'response_invalid'): self.audit(day='not-a-date')

if __name__=='__main__': unittest.main()
