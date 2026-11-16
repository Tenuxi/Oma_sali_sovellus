"""Validate every certificate digest, regardless of apksigner signer-label format."""
from pathlib import Path
import re
import sys


def verify(text: str, expected: str) -> None:
    expected = expected.strip().lower()
    if not re.fullmatch(r'[0-9a-f]{64}', expected):
        raise ValueError('Invalid pinned certificate digest')
    digests = {d.lower() for d in re.findall(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})(?=\s|$)', text)}
    if digests != {expected}:
        raise ValueError(f'APK certificate mismatch: expected {expected}; found {sorted(digests)}. Public certificate report:\n{text}')


if __name__ == '__main__':
    verify(Path(sys.argv[1]).read_text(), Path('docs/release-certificate.sha256').read_text())
