#!/usr/bin/env python3
"""Fetch the exact Celosia revision used by the experimental Android port."""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

CELOSIA_URL = "https://github.com/HenryKun55/celosia.git"
CELOSIA_COMMIT = "ed1025701cfa943d7c1649d88e5175a51ff33d5e"


class FetchError(RuntimeError):
    pass


def run(args: list[str], *, cwd: Path | None = None) -> str:
    result = subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise FetchError("$ " + " ".join(args) + "\n" + result.stdout[-12000:])
    return result.stdout.strip()


def ensure_checkout(destination: Path, revision: str = CELOSIA_COMMIT) -> Path:
    destination = destination.expanduser().resolve()
    destination.parent.mkdir(parents=True, exist_ok=True)

    if destination.exists():
        if not (destination / ".git").exists():
            raise FetchError(
                f"Destino já existe, mas não é um checkout Git: {destination}. "
                "Não vou apagar nem sobrescrever esses arquivos."
            )
        inside = run(["git", "rev-parse", "--is-inside-work-tree"], cwd=destination)
        if inside != "true":
            raise FetchError(f"O destino não é um repositório Git válido: {destination}")
        dirty = subprocess.run(
            ["git", "diff", "--quiet", "--ignore-submodules", "--"],
            cwd=destination,
        ).returncode
        if dirty != 0:
            raise FetchError(
                f"Há alterações locais versionadas em {destination}. "
                "Preserve-as e use um checkout separado antes de atualizar Celosia."
            )
        run(["git", "fetch", "--no-tags", "origin", revision], cwd=destination)
    else:
        run([
            "git", "clone", "--filter=blob:none", "--no-checkout",
            "--single-branch", "--branch", "main", CELOSIA_URL, str(destination),
        ])
        run(["git", "fetch", "--no-tags", "origin", revision], cwd=destination)

    run(["git", "checkout", "--detach", revision], cwd=destination)
    actual = run(["git", "rev-parse", "HEAD"], cwd=destination)
    if actual.lower() != revision.lower():
        raise FetchError(f"Revisão Celosia inesperada: {actual}; esperada: {revision}")
    required = (
        "celosia",
        "PS2Recomp/CMakeLists.txt",
        "PS2Recomp/ps2xRuntime/src/lib/ps2_pad.cpp",
        "PS2Recomp/ps2xRuntime/src/lib/native/native_renderer.cpp",
        "game/scus.toml",
        "game/overrides/sotc_overrides.cpp",
    )
    missing = [name for name in required if not (destination / name).is_file()]
    if missing:
        raise FetchError("Checkout Celosia incompleto; faltam: " + ", ".join(missing))
    return destination


def self_test() -> None:
    assert len(CELOSIA_COMMIT) == 40
    assert all(c in "0123456789abcdef" for c in CELOSIA_COMMIT)
    assert CELOSIA_URL.endswith("/celosia.git")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=Path(__file__).resolve().parents[1] / "upstream" / "celosia")
    parser.add_argument("--revision", default=CELOSIA_COMMIT)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("Celosia pin self-test passed.")
        return 0
    try:
        path = ensure_checkout(args.destination, args.revision)
        print(f"Celosia ready: {path} @ {args.revision}")
    except (FetchError, OSError, FileNotFoundError) as error:
        print(f"ERRO: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
