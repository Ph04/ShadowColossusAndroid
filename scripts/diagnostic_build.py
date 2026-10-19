#!/usr/bin/env python3
"""Run SOTC ISO generation/build while capturing a portable diagnostic log."""
from __future__ import annotations

import argparse
import datetime as dt
import os
import platform
import shutil
import subprocess
import sys
import traceback
from pathlib import Path

EXPECTED_ISO_BYTES = 4_635_918_336


def emit(log, text: str) -> None:
    rendered = text.rstrip("\n")
    print(rendered, flush=True)
    log.write(rendered + "\n")
    log.flush()


def run_tee(command: list[str], cwd: Path, log) -> int:
    emit(log, "")
    emit(log, "COMMAND: " + subprocess.list2cmdline(command))
    emit(log, "CWD: " + str(cwd))
    try:
        with subprocess.Popen(
            command,
            cwd=str(cwd),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        ) as proc:
            assert proc.stdout is not None
            for line in proc.stdout:
                sys.stdout.write(line)
                sys.stdout.flush()
                log.write(line)
                log.flush()
            return_code = proc.wait()
        emit(log, f"COMMAND_EXIT_CODE: {return_code}")
        return return_code
    except Exception:
        emit(log, "COMMAND_LAUNCH_EXCEPTION:")
        emit(log, traceback.format_exc())
        return 127


def probe(log, root: Path, label: str, command: list[str]) -> None:
    emit(log, f"\n--- ENVIRONMENT PROBE: {label} ---")
    code = run_tee(command, root, log)
    emit(log, f"PROBE_EXIT_CODE: {code}")


def count_generated(upstream: Path) -> tuple[int, int, bool]:
    generated = upstream / "Port" / "generated"
    if not generated.is_dir():
        return 0, 0, False
    cpp_count = sum(1 for p in generated.rglob("*.cpp") if p.is_file() and p.stat().st_size > 0)
    header_count = sum(1 for p in generated.rglob("*.h") if p.is_file() and p.stat().st_size > 0)
    return cpp_count, header_count, True


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Gera código do jogo e compila APK mantendo um relatório completo de diagnóstico local."
    )
    parser.add_argument("--iso", required=True, type=Path, help="sua ISO PAL SCES-53326 v1.00")
    parser.add_argument("--jobs", type=int, default=1)
    parser.add_argument("--build-type", choices=("debug", "release", "diagnostic"), default="diagnostic")
    parser.add_argument("--codegen-only", action="store_true", help="gera/verifica C++ sem compilar APK")
    parser.add_argument("--skip-vu1", action="store_true", help="pula apenas a geração estática opcional da VU1")
    parser.add_argument("--log-file", type=Path, help="caminho explícito do relatório (opcional)")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    prepare_script = root / "scripts" / "prepare_sotc_android.py"
    upstream = root / "upstream" / "sotc-vibe-pc"
    try:
        iso = args.iso.expanduser().resolve(strict=True)
    except Exception as error:
        print(f"ERRO: não consegui resolver o caminho da ISO: {error}", file=sys.stderr)
        return 2
    if not iso.is_file():
        print(f"ERRO: não é arquivo ISO: {iso}", file=sys.stderr)
        return 2
    if args.jobs < 1:
        print("ERRO: --jobs precisa ser maior que zero.", file=sys.stderr)
        return 2

    timestamp = dt.datetime.now().astimezone().strftime("%Y%m%d-%H%M%S")
    log_path = args.log_file.expanduser().resolve() if args.log_file else root / "build" / "diagnostics" / f"sotc-diagnostic-{timestamp}.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)

    with log_path.open("w", encoding="utf-8", errors="replace") as log:
        emit(log, "SHADOW OF THE COLOSSUS ANDROID — HOST DIAGNOSTIC LOG v1")
        emit(log, f"started_at={dt.datetime.now().astimezone().isoformat()}")
        emit(log, f"project_root={root}")
        emit(log, f"platform={platform.platform()}")
        emit(log, f"python={sys.version.replace(os.linesep, ' ')}")
        emit(log, f"python_executable={sys.executable}")
        emit(log, f"architecture={platform.machine()}")
        emit(log, f"cwd={Path.cwd()}")
        emit(log, f"jobs={args.jobs}")
        emit(log, f"build_type={args.build_type}")
        emit(log, f"codegen_only={args.codegen_only}")
        emit(log, f"skip_vu1={args.skip_vu1}")
        emit(log, f"iso_path={iso}")
        emit(log, f"iso_exists={iso.exists()}")
        emit(log, f"iso_size_bytes={iso.stat().st_size}")
        emit(log, f"iso_expected_size_bytes={EXPECTED_ISO_BYTES}")
        emit(log, f"iso_size_match={iso.stat().st_size == EXPECTED_ISO_BYTES}")
        emit(log, "iso_whole_file_hash=not_computed_by_diagnostic_wrapper; linker validates boot ELF and module hashes")
        emit(log, f"iso_modified_at={dt.datetime.fromtimestamp(iso.stat().st_mtime).astimezone().isoformat()}")
        emit(log, f"ANDROID_HOME_configured={bool(os.environ.get('ANDROID_HOME'))}")
        emit(log, f"ANDROID_SDK_ROOT_configured={bool(os.environ.get('ANDROID_SDK_ROOT'))}")
        emit(log, f"JAVA_HOME_configured={bool(os.environ.get('JAVA_HOME'))}")
        try:
            usage = shutil.disk_usage(root)
            emit(log, f"project_disk_total_bytes={usage.total}")
            emit(log, f"project_disk_free_bytes={usage.free}")
        except OSError as error:
            emit(log, f"project_disk_usage_error={error}")
        try:
            usage = shutil.disk_usage(iso.parent)
            emit(log, f"iso_disk_free_bytes={usage.free}")
        except OSError as error:
            emit(log, f"iso_disk_usage_error={error}")

        for command, label in (
            (["git", "--version"], "git"),
            (["cmake", "--version"], "cmake"),
            (["ninja", "--version"], "ninja"),
            (["make", "--version"], "make"),
            (["cl"], "msvc_cl"),
            (["clang-cl", "--version"], "clang_cl"),
            (["clang", "--version"], "clang"),
            (["clang++", "--version"], "clang++"),
            (["cc", "--version"], "cc"),
            (["g++", "--version"], "g++"),
            (["java", "-version"], "java"),
            (["gradle", "--version"], "gradle"),
        ):
            executable = shutil.which(command[0])
            emit(log, f"tool_{label}_path={executable or 'NOT_FOUND'}")
            if executable:
                probe(log, root, label, command)

        if shutil.which("git"):
            probe(log, root, "git_head", ["git", "rev-parse", "HEAD"])
            probe(log, root, "git_submodules", ["git", "submodule", "status", "--recursive"])
            probe(log, root, "git_status", ["git", "status", "--short"])
        cpp_before, headers_before, generated_before = count_generated(upstream)
        emit(log, f"generated_directory_before={generated_before}")
        emit(log, f"generated_cpp_count_before={cpp_before}")
        emit(log, f"generated_header_count_before={headers_before}")
        host_cache = upstream / "build" / "android-host-tools" / "CMakeCache.txt"
        emit(log, f"host_cmake_cache_path={host_cache}")
        emit(log, f"host_cmake_cache_exists={host_cache.is_file()}")
        if host_cache.is_file():
            wanted_cache_keys = (
                "CMAKE_GENERATOR:INTERNAL=",
                "CMAKE_C_COMPILER:FILEPATH=",
                "CMAKE_CXX_COMPILER:FILEPATH=",
                "CMAKE_BUILD_TYPE:STRING=",
            )
            for cache_line in host_cache.read_text(encoding="utf-8", errors="replace").splitlines():
                if cache_line.startswith(wanted_cache_keys):
                    emit(log, "host_cmake_cache:" + cache_line)

        command = [
            sys.executable,
            str(prepare_script),
            "--iso", str(iso),
            "--jobs", str(args.jobs),
        ]
        if args.codegen_only:
            command.append("--codegen-only")
        else:
            command.extend(["--build-type", args.build_type])
        if args.skip_vu1:
            command.append("--skip-vu1")

        run_code = run_tee(command, root, log)
        cpp_after, headers_after, generated_after = count_generated(upstream)
        emit(log, f"generated_directory_after={generated_after}")
        emit(log, f"generated_cpp_count_after={cpp_after}")
        emit(log, f"generated_header_count_after={headers_after}")
        if args.codegen_only:
            emit(log, "apk_build=SKIPPED_BY_CODEGEN_ONLY")
        else:
            apk_name = {
                "debug": "app-debug.apk",
                "release": "app-release.apk",
                "diagnostic": "app-diagnostic.apk",
            }[args.build_type]
            candidate = root / "app" / "build" / "outputs" / "apk" / args.build_type / apk_name
            emit(log, f"apk_path={candidate}")
            emit(log, f"apk_exists={candidate.is_file()}")
            emit(log, f"apk_size_bytes={candidate.stat().st_size if candidate.is_file() else 0}")
        emit(log, f"prepare_exit_code={run_code}")
        emit(log, f"finished_at={dt.datetime.now().astimezone().isoformat()}")
        if run_code == 0:
            emit(log, "RESULT=SUCCESS — review generated file counts and boot-test separately")
        else:
            emit(log, "RESULT=FAILED — the full first failing command output is above")
        emit(log, f"REPORT_FILE={log_path}")
    print(f"\nRelatório de diagnóstico salvo em:\n{log_path}")
    print("O arquivo registra comandos, saídas, versões de ferramentas, submódulos, espaço livre e resultado da geração.")
    return run_code


if __name__ == "__main__":
    raise SystemExit(main())
