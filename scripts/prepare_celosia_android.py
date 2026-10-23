#!/usr/bin/env python3
"""Generate Celosia's USA game code from the user's own disc, then build the Android APK.

The ISO, extracted disc, module-capture RAM dumps and generated C++ stay local.
The USA boot ELF is SCUS_974.72; this helper never accepts the old PAL profile.
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

REQUIRED_FILES = (
    "celosia",
    "PS2Recomp/CMakeLists.txt",
    "PS2Recomp/ps2xRuntime/CMakeLists.txt",
    "PS2Recomp/ps2xRuntime/src/lib/ps2_pad.cpp",
    "PS2Recomp/ps2xRuntime/src/main.cpp",
    "PS2Recomp/ps2xRuntime/src/lib/ps2_runtime.cpp",
    "game/scus.toml",
    "game/overrides/sotc_overrides.cpp",
)
DISC_BOOT = "SCUS_974.72"
MODULES = ("KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF")


class BuildError(RuntimeError):
    pass


def run(command: list[str], *, cwd: Path | None = None, env: dict[str, str] | None = None) -> None:
    print("+ " + " ".join(f'"{item}"' if " " in item else item for item in command), flush=True)
    subprocess.run(command, cwd=str(cwd) if cwd else None, env=env, check=True)


def validate_tree(celosia: Path) -> None:
    missing = [item for item in REQUIRED_FILES if not (celosia / item).is_file()]
    if missing:
        raise BuildError("Checkout Celosia incompleto. Faltam: " + ", ".join(missing))
    config = (celosia / "game" / "scus.toml").read_text(encoding="utf-8")
    if "SCUS_974.72" not in config:
        raise BuildError("O perfil encontrado não é o perfil americano SCUS_974.72.")
    pad = (celosia / "PS2Recomp" / "ps2xRuntime" / "src" / "lib" / "ps2_pad.cpp").read_text(encoding="utf-8")
    if 'include "ps2_host_backend.h"' not in pad or "data[2] = static_cast<uint8_t>(btns & 0xFF);" not in pad:
        raise BuildError("O leitor de controle Celosia mudou; atualize o hook Android antes de gerar o APK.")


def check_generated(build_dir: Path) -> int:
    runner = build_dir / "runner"
    sources = [item for item in runner.glob("*.cpp") if item.is_file() and item.stat().st_size > 0]
    if not sources:
        raise BuildError(
            f"Celosia não produziu código recompilado em {runner}. "
            "Não vou compilar ou descrever o resultado como engine do jogo."
        )

    disc_dir = build_dir.parent / "celosia-disc"
    for name in MODULES:
        module = disc_dir / name
        if not module.is_file() or module.stat().st_size == 0:
            raise BuildError(f"Disco extraído incompleto: falta {name}")
    boot = disc_dir / DISC_BOOT
    if not boot.is_file() or boot.stat().st_size == 0:
        raise BuildError(f"Disco extraído não contém {DISC_BOOT}")

    # A complete setup must reach all three runtime module captures; otherwise
    # runner/*.cpp may only contain the boot stage and must not enable Play.
    captures_dir = build_dir / "captures"
    for module_name in ("kernel", "manager", "gamecore"):
        capture = captures_dir / (module_name + ".bin")
        if not capture.is_file() or capture.stat().st_size < 33_554_432:
            raise BuildError(
                f"Codegen USA incompleto: captura {capture.name} ausente ou menor que 32 MiB. "
                "Execute o pipeline completo ./celosia setup com sua ISO."
            )

    vu1_dir = build_dir / "vu1rec"
    vu1_sources = [item for item in vu1_dir.glob("*.cpp") if item.is_file() and item.stat().st_size > 0]
    if not vu1_sources:
        raise BuildError(
            f"Celosia não gerou fontes VU1 recompiladas em {vu1_dir}; "
            "o APK não será marcado como pronto para iniciar o jogo."
        )

    # Remove any previous marker first; only write a new one after every
    # prerequisite above has passed, so partial/stale code cannot enable Play.
    marker = build_dir / ".usa-codegen-complete"
    marker.unlink(missing_ok=True)
    marker.write_text(
        "edition=USA-SCUS_974.72\n"
        + "generated_cpp_count=" + str(len(sources)) + "\n"
        + "vu1_cpp_count=" + str(len(vu1_sources)) + "\n"
        + "required_modules=KERNEL.XFF,MANAGER.XFF,GAMECORE.XFF\n",
        encoding="ascii",
    )
    return len(sources)


def build_android(project_root: Path, build_type: str) -> Path:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk or not Path(sdk).is_dir():
        raise BuildError("Defina ANDROID_HOME ou ANDROID_SDK_ROOT para o Android SDK.")
    gradle = shutil.which("gradle") or shutil.which("gradle.bat")
    if not gradle:
        raise BuildError("Gradle 8.9 não encontrado no PATH.")
    task = {"debug": ":app:assembleDebug", "diagnostic": ":app:assembleDiagnostic", "release": ":app:assembleRelease"}[build_type]
    run([gradle, "--no-daemon", "--max-workers=1", "--stacktrace", task], cwd=project_root)
    apk_name = {"debug": "app-debug.apk", "diagnostic": "app-diagnostic.apk", "release": "app-release.apk"}[build_type]
    apk = project_root / "app" / "build" / "outputs" / "apk" / build_type / apk_name
    if not apk.is_file():
        candidates = list((project_root / "app" / "build" / "outputs" / "apk" / build_type).glob("*.apk"))
        if not candidates:
            raise BuildError("Gradle terminou, mas nenhum APK foi encontrado.")
        apk = candidates[0]
    return apk


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    source_group = parser.add_mutually_exclusive_group()
    source_group.add_argument("--iso", type=Path, help="ISO/ZIP USA da sua própria cópia de Shadow of the Colossus")
    source_group.add_argument("--disc-dir", type=Path, help="pasta já extraída ou 'disc/' de um pacote Celosia exportado pelo Android")
    parser.add_argument("--jobs", type=int, default=1, help="threads para a captura/build host; padrão conservador: 1")
    parser.add_argument("--build-type", choices=("debug", "diagnostic", "release"), default="release")
    parser.add_argument("--codegen-only", action="store_true", help="gera o código local e não executa Gradle/NDK")
    parser.add_argument("--check-source-tree", action="store_true", help="verifica os arquivos Celosia e o pin sem ISO")
    parser.add_argument("--skip-setup", action="store_true", help="usa código já gerado em build/celosia")
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs precisa ser pelo menos 1")

    root = Path(__file__).resolve().parents[1]
    celosia = root / "upstream" / "celosia"
    build_dir = root / "build" / "celosia"
    disc_dir = root / "build" / "celosia-disc"
    try:
        run([sys.executable, str(root / "scripts" / "ensure_celosia.py"), "--destination", str(celosia)])
        validate_tree(celosia)
        run([sys.executable, str(root / "scripts" / "patch_celosia_android_input.py"), "--self-test"])
        run([sys.executable, str(root / "scripts" / "patch_celosia_android_paths.py"), "--self-test"])
        run([sys.executable, str(root / "scripts" / "patch_celosia_android_startup.py"), "--self-test"])
        if args.check_source_tree:
            print(f"Celosia source tree is valid: {celosia}")
            return 0
        # Invalidate a prior stamp on every invocation. It is restored only
        # if check_generated() proves all USA captures and VU1 output exist.
        (build_dir / ".usa-codegen-complete").unlink(missing_ok=True)
        if not args.skip_setup:
            if not args.iso and not args.disc_dir:
                raise BuildError("Informe --iso ou --disc-dir com os arquivos da edição USA.")
            if not shutil.which("bash"):
                raise BuildError("A preparação Celosia requer Bash e ferramentas POSIX. No Windows, execute em WSL2.")
            for tool in ("cmake", "ninja", "python3", "bsdtar", "pkg-config"):
                if not shutil.which(tool):
                    raise BuildError(f"Ferramenta obrigatória ausente: {tool}")
            env = os.environ.copy()
            env["CELOSIA_DISC"] = str(disc_dir)
            env["CELOSIA_BUILD"] = str(build_dir)
            env["CELOSIA_JOBS"] = str(args.jobs)

            if args.iso:
                iso = args.iso.expanduser().resolve()
                if not iso.is_file():
                    raise BuildError(f"ISO/ZIP não encontrado: {iso}")
                setup_command = ["bash", str(celosia / "celosia"), "setup", "--iso", str(iso)]
            else:
                exported_disc = args.disc_dir.expanduser().resolve()
                if not exported_disc.is_dir():
                    raise BuildError(f"Pasta exportada não encontrada: {exported_disc}")
                required_inputs = (DISC_BOOT, *MODULES, "SYSTEM.CNF")
                missing_inputs = [
                    name for name in required_inputs
                    if not (exported_disc / name).is_file() or (exported_disc / name).stat().st_size == 0
                ]
                if missing_inputs:
                    raise BuildError("Pacote exportado incompleto; faltam: " + ", ".join(missing_inputs))
                disc_dir.mkdir(parents=True, exist_ok=True)
                for name in required_inputs:
                    shutil.copy2(exported_disc / name, disc_dir / name)
                print(f"Usando arquivos USA extraídos; nenhuma ISO completa será necessária: {disc_dir}")
                setup_command = ["bash", str(celosia / "celosia"), "setup"]

            run(setup_command, cwd=celosia, env=env)
        generated_count = check_generated(build_dir)
        if args.codegen_only:
            print(f"Codegen USA concluído: {generated_count} fontes C++ em {build_dir / 'runner'}. Dados permanecem locais.")
            return 0
        apk = build_android(root, args.build_type)
        print(f"APK gerado: {apk}")
        print("A compilação não comprova ainda boot/renderização em aparelho Android; faça o teste real antes de classificá-lo como jogável.")
        return 0
    except (BuildError, subprocess.CalledProcessError, OSError) as error:
        print(f"ERRO: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
