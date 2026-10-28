"""Bounded cold-start and reminder retries. Uses only Python's standard library."""
import json, os, sys, time
from urllib.request import Request, urlopen, HTTPRedirectHandler, build_opener
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def run():
    base = os.environ.get('API_PUBLIC_URL', '').rstrip('/')
    secret = os.environ.get('REMINDER_TRIGGER_SECRET', '')
    url = urlsplit(base)
    if url.scheme != 'https' or not url.hostname or url.path or url.query or url.fragment or url.username or len(secret) < 43:
        raise ValueError('Missing or invalid scheduler configuration')
    opener = build_opener(NoRedirect())
    deadline = time.monotonic() + 360
    # Wake the sleeping API. No secret is sent to an unverified redirect target.
    ready = False
    wake_deadline = time.monotonic() + 180
    while time.monotonic() < wake_deadline:
        try:
            with opener.open(base + '/ready', timeout=max(0.1, min(20, wake_deadline - time.monotonic()))) as response:
                ready = response.status == 200
                if ready: break
        except HTTPError as error: error.close()
        except (URLError, TimeoutError): pass
        time.sleep(max(0, min(5, wake_deadline - time.monotonic())))
    if not ready: raise RuntimeError('API readiness timed out')
    for _ in range(8):
        if time.monotonic() >= deadline: break
        try:
            request = Request(base + '/internal/reminders', data=b'{}', method='POST',
                headers={'X-Reminder-Secret': secret, 'Content-Type': 'application/json'})
            with opener.open(request, timeout=55) as response:
                result = json.load(response)
            # Print an allowlist, never raw responses, credentials, or user data.
            print(json.dumps({key: result.get(key) for key in ('status', 'sent', 'retryable', 'failed', 'remaining')}))
            if result.get('status') in ('disabled', 'outside_window'):
                raise RuntimeError('Reminder configuration/window does not permit this run')
            if result.get('failed', 0): raise RuntimeError('Some deliveries failed permanently; inspect delivery status')
            if result.get('remaining') == 0 and result.get('status') != 'busy': return
        except HTTPError as error:
            if error.code < 500 and error.code != 429: raise RuntimeError('Scheduler authentication/request rejected') from None
        except (URLError, TimeoutError): pass
        time.sleep(15)
    raise RuntimeError('Reminder backlog remains; later scheduled runs can resume safely')

if __name__ == '__main__':
    try: run()
    except Exception as error:
        print('Reminder trigger did not complete. Check Actions/Render configuration and aggregate delivery status. Type: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
