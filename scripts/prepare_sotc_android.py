#!/usr/bin/env python3
"""Generate SOTC game code locally, then build the Android APK.

The ISO and generated C++ stay on the user's machine. This script does not
upload either item or commit the generated files.
"""
from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

EXPECTED_ISO_BYTES = 4_635_918_336
REQUIRED_SOURCE_FILES = (
    "CMakeLists.txt",
    "External/PS2Recomp/CMakeLists.txt",
    "Port/CMakeLists.txt",
    "Port/profile/SCES-53326_v1.00.json",
    "Port/recomp/sotc.toml",
    "Port/src/main.cpp",
    "Tools/sotc_link.py",
    "Tools/verify_reloc_sites.py",
)


class BuildError(RuntimeError):
    pass


def run(command: list[str | os.PathLike[str]], *, cwd: Path | None = None) -> None:
    cmd = [str(part) for part in command]
    print("+ " + " ".join(f'"{part}"' if " " in part else part for part in cmd), flush=True)
    subprocess.run(cmd, cwd=str(cwd) if cwd else None, check=True)


def check_source_tree(upstream: Path) -> None:
    missing = [name for name in REQUIRED_SOURCE_FILES if not (upstream / name).is_file()]
    if missing:
        rendered = "\n  - ".join(missing)
        raise BuildError(
            "O checkout upstream está incompleto. Inicialize os submódulos recursivamente. "
            "Arquivos ausentes:\n  - " + rendered
        )
    ps2recomp = upstream / "External" / "PS2Recomp"
    if not (ps2recomp / "ps2xRuntime").is_dir():
        raise BuildError(
            f"Submódulo PS2Recomp incompleto: {ps2recomp}. "
            "Use clonagem/checkout com submódulos recursivos."
        )


def locate_tool(build_dir: Path, tool_name: str) -> Path:
    valid_names = {tool_name, tool_name + ".exe"}
    matches = [
        path for path in build_dir.rglob("*")
        if path.is_file()
        and path.name in valid_names
        and "CMakeFiles" not in path.parts
        and "Testing" not in path.parts
    ]
    if os.name != "nt":
        matches = [path for path in matches if os.access(path, os.X_OK)]
    if not matches:
        raise BuildError(
            f"Não encontrei o executável {tool_name} dentro de {build_dir}. "
            "Veja a saída do CMake e confirme se o target foi compilado."
        )

    def score(path: Path) -> tuple[int, int, str]:
        parts = {part.lower() for part in path.parts}
        release_penalty = 0 if "release" in parts else 1
        expected_name = tool_name + (".exe" if os.name == "nt" else "")
        extension_penalty = 0 if path.name == expected_name else 1
        return release_penalty, extension_penalty, str(path)

    return sorted(matches, key=score)[0]


def copy_flat_files(source: Path, destination: Path) -> int:
    destination.mkdir(parents=True, exist_ok=True)
    count = 0
    for item in source.iterdir():
        if item.is_file():
            shutil.copy2(item, destination / item.name)
            count += 1
    return count


def generate_game_sources(upstream: Path, iso: Path, host_build: Path, jobs: int, skip_vu1: bool) -> Path:
    check_source_tree(upstream)
    cmake = shutil.which("cmake")
    if not cmake:
        raise BuildError("CMake não foi encontrado no PATH. Instale CMake e um compilador C/C++.")

    targets = ["ps2_recomp"]
    if not skip_vu1:
        targets.append("vu1_recomp")

    host_build.mkdir(parents=True, exist_ok=True)
    run([
        cmake, "-S", upstream, "-B", host_build,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DPS2X_BUILD_RECOMP=ON",
        "-DPS2X_BUILD_RUNTIME=ON",
        "-DPS2X_BUILD_ANALYZER=OFF",
        # vu1_recomp is defined by the upstream ps2xTest subdirectory.
        # Enable that subdirectory only for the full VU1 build; with --skip-vu1
        # the EE recompiler can still be built without pulling in test targets.
        "-DPS2X_BUILD_TEST=" + ("OFF" if skip_vu1 else "ON"),
        "-DBUILD_TESTING=OFF",
        "-DPS2X_BUILD_STUDIO=OFF",
        "-DPS2X_ENABLE_FFMPEG=OFF",
        "-DPS2X_ENABLE_DEBUG_UI=OFF",
        "-DPS2X_ENABLE_SCCACHE=OFF",
    ])
    run([
        cmake, "--build", host_build, "--config", "Release",
        "--target", *targets, "--parallel", str(jobs),
    ])

    ps2_recomp = locate_tool(host_build, "ps2_recomp")
    vu1_recomp = None if skip_vu1 else locate_tool(host_build, "vu1_recomp")

    build_root = upstream / "build"
    link_out = build_root / "link"
    staging = build_root / "generated_staging"
    vu1_staging = build_root / "generated_vu1_staging"
    generated = upstream / "Port" / "generated"
    profile = upstream / "Port" / "profile" / "SCES-53326_v1.00.json"
    linker = upstream / "Tools" / "sotc_link.py"
    verifier = upstream / "Tools" / "verify_reloc_sites.py"
    recomp_dir = upstream / "Port" / "recomp"
    staged_toml = recomp_dir / "sotc.android.staging.toml"

    for folder in (staging, vu1_staging):
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir(parents=True)

    # This tool validates the disc by checking hashes of its boot ELF and modules.
    run([
        sys.executable, linker,
        "--profile", profile,
        "--iso", iso,
        "--out", link_out,
        "--emit-header", staging / "sotc_layout_generated.h",
    ])
    if not (staging / "sotc_layout_generated.h").is_file():
        raise BuildError("O linker terminou sem gerar sotc_layout_generated.h.")

    # EE/game C++ generation is required even when optional VU1 optimization is skipped.
    original_toml = (recomp_dir / "sotc.toml").read_text(encoding="utf-8")
    output_path = staging.resolve().as_posix()
    updated_toml, replacements = re.subn(
        r'(?m)^output\s*=\s*"[^"]*"\s*$',
        f'output = "{output_path}"',
        original_toml,
        count=1,
    )
    if replacements != 1:
        raise BuildError("Não consegui ajustar o campo output em Port/recomp/sotc.toml.")
    staged_toml.write_text(updated_toml, encoding="utf-8")
    try:
        run([ps2_recomp, staged_toml.name], cwd=recomp_dir)
    finally:
        staged_toml.unlink(missing_ok=True)

    if not skip_vu1:
        capture_dir = link_out / "vu1_capture"
        if not capture_dir.is_dir():
            raise BuildError(f"A captura de microprogramas VU1 não foi criada: {capture_dir}")
        run([vu1_recomp, capture_dir, vu1_staging])
        if not any(item.is_file() and item.suffix == ".cpp" and item.stat().st_size > 0 for item in vu1_staging.iterdir()):
            raise BuildError(
                "O recompilador VU1 não produziu código estático. "
                "Corrija a geração VU1 ou use --skip-vu1 para testar com o interpretador."
            )

    if not (staging / "register_functions.cpp").is_file():
        raise BuildError(
            "O recompilador não criou register_functions.cpp. "
            "O APK não será marcado como pronto; confira o log da ferramenta host."
        )

    generated_cpp = [
        item for item in staging.glob("*.cpp")
        if item.name != "register_functions.cpp" and item.stat().st_size > 0
    ]
    if not generated_cpp:
        raise BuildError(
            "O recompilador criou a tabela de registro, mas nenhum arquivo C++ de jogo. "
            "A geração está incompleta; o APK não será compilado."
        )
    required_headers = ("ps2_recompiled_functions.h", "ps2_recompiled_stubs.h")
    missing_headers = [name for name in required_headers if not (staging / name).is_file()]
    if missing_headers:
        raise BuildError(
            "O recompilador não produziu os cabeçalhos necessários: "
            + ", ".join(missing_headers)
            + ". A geração está incompleta; confira a versão do PS2Recomp."
        )

    if not skip_vu1:
        copy_flat_files(vu1_staging, staging / "vu1")

    # Check relocation coverage before replacing any previously generated output.
    run([
        sys.executable, verifier,
        link_out / "sotc_runtime_relocs.csv",
        staging,
        link_out / "sotc_functions.csv",
    ])

    # Publish the complete output as one directory replacement, removing stale VU1 files.
    backup = generated.with_name("generated.android.previous")
    if backup.exists():
        shutil.rmtree(backup)
    if generated.exists():
        generated.rename(backup)
    try:
        staging.rename(generated)
    except Exception:
        if backup.exists() and not generated.exists():
            backup.rename(generated)
        raise
    if backup.exists():
        shutil.rmtree(backup)

    source_count = len(list(generated.glob("*.cpp")))
    vu1_count = len(list((generated / "vu1").glob("*.cpp"))) if (generated / "vu1").is_dir() else 0
    print(
        f"Fontes locais prontas: {source_count} arquivos C++ do jogo; "
        f"{vu1_count} arquivos VU1. Não faça commit da pasta Port/generated.",
        flush=True,
    )
    return generated


def build_android_apk(project_root: Path, build_type: str) -> Path:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk or not Path(sdk).is_dir():
        raise BuildError(
            "Defina ANDROID_HOME ou ANDROID_SDK_ROOT para uma instalação do Android SDK. "
            "Use --codegen-only se quiser apenas gerar as fontes agora."
        )
    gradle = shutil.which("gradle") or shutil.which("gradle.bat")
    if not gradle:
        raise BuildError("Gradle não foi encontrado no PATH. Instale Gradle 8.9 ou use Android Studio.")
    task = ":app:assembleDebug" if build_type == "debug" else ":app:assembleRelease"
    run([gradle, "--no-daemon", "--max-workers=1", "--stacktrace", task], cwd=project_root)
    apk_name = "app-debug.apk" if build_type == "debug" else "app-release.apk"
    apk = project_root / "app" / "build" / "outputs" / "apk" / build_type / apk_name
    if not apk.is_file():
        candidates = list((project_root / "app" / "build" / "outputs" / "apk" / build_type).glob("*.apk"))
        if not candidates:
            raise BuildError("A tarefa Gradle terminou, mas nenhum APK foi encontrado.")
        apk = candidates[0]
    print(f"APK gerado: {apk}", flush=True)
    if build_type == "release" and "unsigned" in apk.name:
        print("Atenção: o Gradle gerou um APK release sem assinatura; ele precisa ser assinado antes de instalar.", flush=True)
    return apk


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Gera localmente o código de Shadow of the Colossus a partir da ISO do usuário e compila o APK Android."
    )
    parser.add_argument("--iso", type=Path, help="ISO PAL SCES-53326 v1.00 de sua própria cópia")
    parser.add_argument(
        "--upstream", type=Path,
        default=Path(__file__).resolve().parents[1] / "upstream" / "sotc-vibe-pc",
        help="pasta do submódulo upstream (padrão: upstream/sotc-vibe-pc)",
    )
    parser.add_argument(
        "--host-build-dir", type=Path,
        help="pasta de build para as ferramentas host; padrão: upstream/build/android-host-tools",
    )
    parser.add_argument("--jobs", type=int, default=max(1, min(2, os.cpu_count() or 1)))
    parser.add_argument(
        "--skip-vu1", action="store_true",
        help="pula apenas a otimização VU1 estática para um primeiro teste de compilação",
    )
    parser.add_argument(
        "--codegen-only", action="store_true",
        help="gera e valida as fontes locais, mas não executa o build Gradle do APK",
    )
    parser.add_argument("--build-type", choices=("debug", "release"), default="release", help="build Release otimizada por padrão; use debug para diagnóstico")
    parser.add_argument(
        "--check-source-tree", action="store_true",
        help="valida os arquivos/submódulos necessários sem exigir ISO nem executar geração",
    )
    args = parser.parse_args()

    project_root = Path(__file__).resolve().parents[1]
    upstream = args.upstream.resolve()
    try:
        check_source_tree(upstream)
        if args.check_source_tree:
            print(f"Submódulos e arquivos de geração encontrados em: {upstream}")
            return 0
        if args.jobs < 1:
            raise BuildError("--jobs precisa ser pelo menos 1.")
        if not args.iso:
            raise BuildError("Informe --iso com o caminho da sua própria imagem de jogo.")
        iso = args.iso.expanduser().resolve()
        if not iso.is_file():
            raise BuildError(f"ISO não encontrada: {iso}")
        actual_size = iso.stat().st_size
        if actual_size != EXPECTED_ISO_BYTES:
            raise BuildError(
                f"Tamanho de ISO incompatível: {actual_size:,} bytes; "
                f"esperado {EXPECTED_ISO_BYTES:,} bytes para SCES-53326 PAL v1.00."
            )

        host_build = args.host_build_dir.resolve() if args.host_build_dir else upstream / "build" / "android-host-tools"
        generated = generate_game_sources(upstream, iso, host_build, args.jobs, args.skip_vu1)
        if args.codegen_only:
            print(f"Geração concluída em {generated}. Nenhum dado foi enviado ao GitHub.")
            return 0
        build_android_apk(project_root, args.build_type)
        return 0
    except (BuildError, subprocess.CalledProcessError, OSError) as error:
        print(f"ERRO: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
