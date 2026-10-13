#!/usr/bin/env python3
"""Stage a package with Beijing wall time for upload-artifact v4's UTC ZIP dates."""

import os
from pathlib import Path
import shutil
import sys


def stage_artifact(source: Path) -> Path:
    source = source.resolve(strict=True)
    if not source.is_file():
        raise ValueError(f"Not a package file: {source}")
    destination = source.parent / "artifact-upload" / source.name
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    metadata = source.stat()
    # upload-artifact v4 serializes ZIP DOS dates in UTC even with TZ set.
    # Shift only this upload copy so its timezone-less ZIP date is Beijing time.
    # The original package keeps its real mtime; signed contents are unchanged.
    os.utime(destination, ns=(metadata.st_atime_ns, metadata.st_mtime_ns + 8 * 3600 * 10**9))
    return destination


if __name__ == "__main__":
    print(f"Actions upload copy: {stage_artifact(Path(sys.argv[1]))}")
