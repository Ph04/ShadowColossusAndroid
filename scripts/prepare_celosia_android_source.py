#!/usr/bin/env python3
"""Create a disposable source mirror of the pinned Celosia tree for Android patches."""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path, PurePosixPath

PIN = "ed1025701cfa943d7c1649d88e5175a51ff33d5e"


class MirrorError(RuntimeError):
    pass


def run(args: list[str], cwd: Path | None = None) -> bytes:
    result = subprocess.run(args, cwd=str(cwd) if cwd else None, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise MirrorError(result.stderr.decode("utf-8", errors="replace")[-8000:])
    return result.stdout


def create_mirror(source: Path, destination: Path, revision: str = PIN) -> Path:
    source = source.expanduser().resolve()
    destination = destination.expanduser().resolve()
    if not (source / ".git").exists():
        raise MirrorError("Celosia source checkout missing Git metadata: " + str(source))
    actual = run(["git", "rev-parse", "HEAD"], cwd=source).decode().strip()
    if actual.lower() != revision.lower():
        raise MirrorError("Celosia revision mismatch: " + actual + "; expected " + revision)
    archive_bytes = run(["git", "archive", "--format=tar", revision], cwd=source)
    temporary = destination.with_name(destination.name + ".partial")
    if temporary.exists():
        shutil.rmtree(temporary)
    temporary.mkdir(parents=True)
    try:
        with tempfile.TemporaryFile() as handle:
            handle.write(archive_bytes)
            handle.seek(0)
            with tarfile.open(fileobj=handle, mode="r:") as archive:
                for member in archive.getmembers():
                    relative = PurePosixPath(member.name)
                    if relative.is_absolute() or any(part in ("", ".", "..") for part in relative.parts):
                        raise MirrorError("Unsafe source path in Git archive: " + member.name)
                    if not (member.isfile() or member.isdir() or member.issym()):
                        raise MirrorError("Unsupported object in source archive: " + member.name)
                    if member.issym():
                        link = PurePosixPath(member.linkname)
                        if link.is_absolute() or ".." in link.parts:
                            raise MirrorError("Unsafe symbolic link in source archive: " + member.name)
                archive.extractall(temporary)
        required = (
            "PS2Recomp/CMakeLists.txt",
            "PS2Recomp/ps2xRuntime/src/lib/ps2_pad.cpp",
            "PS2Recomp/ps2xRuntime/src/lib/native/native_renderer.cpp",
            "game/scus.toml",
            "game/overrides/sotc_overrides.cpp",
        )
        missing = [name for name in required if not (temporary / name).is_file()]
        if missing:
            raise MirrorError("Extracted Celosia mirror is incomplete: " + ", ".join(missing))
        ownership_marker = ".sotc-celosia-source-mirror"
        if destination.exists():
            marker = destination / ownership_marker
            if not marker.is_file():
                raise MirrorError(
                    "Mirror destination exists without its ownership marker; refusing to delete user data: "
                    + str(destination)
                )
            shutil.rmtree(destination)
        temporary.rename(destination)
        (destination / ownership_marker).write_text(
            "source_revision=" + revision + "\\n",
            encoding="ascii",
        )
        return destination
    except Exception:
        if temporary.exists():
            shutil.rmtree(temporary, ignore_errors=True)
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--destination", type=Path, required=True)
    parser.add_argument("--revision", default=PIN)
    args = parser.parse_args()
    try:
        result = create_mirror(args.source, args.destination, args.revision)
        print("Celosia Android source mirror ready: " + str(result) + " @ " + args.revision)
        return 0
    except (MirrorError, OSError, tarfile.TarError) as error:
        print("ERRO: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
