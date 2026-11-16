"""Reserve a version in an immutable remote ref; never rely on run_number or clocks.
The workflow-level release concurrency lock covers allocation through publication.
A failed build consumes a number. A rerun receives a new number, even after workflow recreation.
"""
import json
import os
import subprocess

def gh(*args):
    return subprocess.check_output(['gh', *args], text=True)

repo = os.environ['GITHUB_REPOSITORY']
refs = json.loads(gh('api', f'repos/{repo}/git/matching-refs/tags/build-'))
codes = [int(r['ref'].rsplit('build-', 1)[1]) for r in refs if r['ref'].rsplit('build-', 1)[1].isdigit()]
code = max(codes, default=0) + 1
assert code <= 2_100_000_000, 'Android versionCode exhausted'
gh('api', '--method', 'POST', f'repos/{repo}/git/refs', '-f', f'ref=refs/tags/build-{code}', '-f', f'sha={os.environ["GITHUB_SHA"]}')
with open(os.environ['GITHUB_ENV'], 'a') as out:
    out.write(f'VERSION_CODE={code}\nVERSION_NAME=1.0.{code - 1}\n')
