"""Redacted high-confidence scan of tracked files and optional Git history.
Not a substitute for rotating previously exposed credentials or provider audits.
"""
import argparse, pathlib, re, subprocess, sys
parser = argparse.ArgumentParser()
parser.add_argument('--history', action='store_true')
parser.add_argument('--repo', default='.')
args = parser.parse_args()
root = pathlib.Path(args.repo).resolve()
def git(*arguments):
    return subprocess.check_output(['git', '-C', str(root), *arguments])
patterns = {
    'private-key': re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'brevo-key': re.compile(rb'xkeysib-[a-fA-F0-9]{40,}'),
    'github-token': re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})'),
    'embedded-db-password': re.compile(rb'postgres(?:ql)?://[^\s:/]+:[^\s@$}{]+@'),
    'service-account': re.compile(rb'"private_key"\s*:\s*"[^"\s]{20,}'),
}
findings = set()
def scan(label, data):
    for kind, pattern in patterns.items():
        for match in pattern.finditer(data):
            findings.add((label, data[:match.start()].count(b'\n') + 1, kind))
for name in git('ls-files', '--cached', '--others', '--exclude-standard', '-z').decode().split('\0'):
    if name and (root / name).is_file(): scan(name, (root / name).read_bytes())
if args.history:
    for revision in git('rev-list', '--all').decode().split():
        for name in git('ls-tree', '-r', '--name-only', revision).decode().splitlines():
            scan(revision[:10] + ':' + name, git('show', revision + ':' + name))
for label, line, kind in sorted(findings): print(f'{label}:{line}: {kind} [VALUE REDACTED]')
print(f'{len(findings)} high-confidence credential findings. Test passwords/public client IDs require separate review.')
sys.exit(bool(findings))
