"""Bounded cold-start and reminder retries. Logs only aggregate, nonsecret diagnostics."""
import argparse, json, os, sys, time
from datetime import date
from urllib.request import Request, HTTPRedirectHandler, build_opener
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit

WAKE_SECONDS = 300
RUN_SECONDS = 420
MAX_BATCHES = 16

class TriggerError(RuntimeError):
    """Only locally authored messages may be exposed in workflow logs."""

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def check_delivery(opener, base, secret, deadline):
    request = Request(base + '/internal/reminders', method='GET', headers={'X-Reminder-Secret': secret})
    try:
        with opener.open(request, timeout=max(0.1, min(30, deadline - time.monotonic()))) as response:
            result = json.load(response)
    except HTTPError as error:
        code = error.code
        error.close()
        raise TriggerError('audit_rejected: verify the deployed audit endpoint and scheduler secret (HTTP ' + str(code) + ')') from None
    except (URLError, TimeoutError):
        raise TriggerError('audit_unavailable: delivery health could not be checked') from None
    if not isinstance(result, dict) or result.get('status') not in ('audit','disabled'):
        raise TriggerError('response_invalid: delivery audit returned an unexpected response')
    if result['status'] == 'disabled':
        raise TriggerError('notifications_disabled: delivery audit is disabled')
    summary = {'status':'audit'}
    try: summary['day'] = date.fromisoformat(result['day']).isoformat()
    except (KeyError, ValueError, TypeError):
        raise TriggerError('response_invalid: delivery audit date is invalid') from None
    for key in ('windowClosed','ranToday'):
        if type(result.get(key)) is not bool:
            raise TriggerError('response_invalid: delivery audit state is invalid')
        summary[key] = result[key]
    for key in ('sent','failed','skipped','pending','lastBatchRemaining'):
        value = result.get(key)
        if type(value) is not int or value < 0:
            raise TriggerError('response_invalid: delivery audit counts are invalid')
        summary[key] = value
    print(json.dumps(summary), flush=True)
    if summary['failed']:
        raise TriggerError('delivery_failed: inspect aggregate delivery failure codes and Firebase diagnostics')
    if summary['windowClosed'] and not summary['ranToday']:
        raise TriggerError('scheduler_missed: no successful reminder batch ran in today\'s delivery window; check Supabase wake jobs and backend timer')
    if summary['windowClosed'] and summary['lastBatchRemaining']:
        raise TriggerError('morning_backlog: the last completed morning batch left unfinished work; inspect delivery records')
    if summary['windowClosed'] and summary['pending']:
        raise TriggerError('delivery_pending: eligible reminders remain after the delivery window')

def run(audit=False):
    base = os.environ.get('API_PUBLIC_URL', '').rstrip('/')
    secret = os.environ.get('REMINDER_TRIGGER_SECRET', '')
    url = urlsplit(base)
    if url.scheme != 'https' or not url.hostname or url.path or url.query or url.fragment or url.username or len(secret) < 43:
        raise TriggerError('configuration_invalid: check API_PUBLIC_URL and REMINDER_TRIGGER_SECRET')
    opener = build_opener(NoRedirect())
    deadline = time.monotonic() + RUN_SECONDS
    # Free-host cold starts have exceeded three minutes. Never send credentials on redirects.
    ready = False
    wake_deadline = min(deadline, time.monotonic() + WAKE_SECONDS)
    while time.monotonic() < wake_deadline:
        try:
            with opener.open(base + '/ready', timeout=max(0.1, min(20, wake_deadline - time.monotonic()))) as response:
                payload = json.load(response)
                ready = response.status == 200 and isinstance(payload, dict) and payload.get('status') == 'ready'
                if ready: break
        except HTTPError as error:
            code = error.code
            error.close()
            if code < 500 and code not in (408, 429):
                raise TriggerError('readiness_rejected: check the configured API URL') from None
        except (URLError, TimeoutError, json.JSONDecodeError, UnicodeDecodeError): pass
        time.sleep(max(0, min(5, wake_deadline - time.monotonic())))
    if not ready:
        raise TriggerError('readiness_timeout: API readiness timed out after 300 seconds')
    if audit:
        return check_delivery(opener, base, secret, deadline)
    permanent_failures = 0
    for _ in range(MAX_BATCHES):
        if time.monotonic() >= deadline: break
        try:
            request = Request(base + '/internal/reminders', data=b'{}', method='POST',
                headers={'X-Reminder-Secret': secret, 'Content-Type': 'application/json'})
            with opener.open(request, timeout=max(0.1, min(55, deadline - time.monotonic()))) as response:
                result = json.load(response)
            if not isinstance(result, dict) or result.get('status') not in ('complete','busy','disabled','outside_window'):
                raise TriggerError('response_invalid: the reminder endpoint returned an unexpected response')
            counts = {}
            for key in ('sent','retryable','failed','skipped','remaining'):
                value = result.get(key, 0)
                if type(value) is not int or value < 0:
                    raise TriggerError('response_invalid: the reminder endpoint returned invalid delivery counts')
                counts[key] = value
            if 'remaining' not in result:
                raise TriggerError('response_invalid: the reminder endpoint did not report remaining work')
            print(json.dumps({'status':result['status'], **counts}), flush=True)
            if result['status'] == 'disabled':
                raise TriggerError('notifications_disabled: check NOTIFICATIONS_ENABLED and Firebase configuration')
            if result['status'] == 'outside_window':
                raise TriggerError('outside_delivery_window: scheduler ran outside the configured local delivery hours; inspect scheduler timing')
            permanent_failures += counts['failed']
            # Finish other devices before reporting actionable failures. Opt-outs/expired tokens are skipped.
            if counts['remaining'] == 0 and result['status'] != 'busy':
                if permanent_failures:
                    raise TriggerError('delivery_failed: one or more devices failed; inspect aggregate delivery codes and Firebase diagnostics')
                return
        except HTTPError as error:
            code = error.code
            error.close()
            if code < 500 and code not in (408, 429):
                raise TriggerError('trigger_rejected: check the scheduler secret and endpoint (HTTP ' + str(code) + ')') from None
        except (URLError, TimeoutError): pass
        except (json.JSONDecodeError, UnicodeDecodeError):
            raise TriggerError('response_invalid: reminder endpoint did not return valid JSON') from None
        time.sleep(max(0, min(15, deadline - time.monotonic())))
    raise TriggerError('backlog_remaining: the bounded run ended; later scheduled attempts can resume safely')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit', action='store_true', help='Read delivery health without sending reminders')
    args = parser.parse_args()
    try: run(audit=args.audit)
    except TriggerError as error:
        print('Reminder delivery: ' + str(error), file=sys.stderr)
        sys.exit(1)
    except Exception:
        print('Reminder delivery: unexpected_error; inspect the service using private operational tools', file=sys.stderr)
        sys.exit(1)
