#!/usr/bin/env python3
"""Make Celosia's Android memory-card mount honor PS2X_MEMCARD_DIR."""
from __future__ import annotations

import argparse
from pathlib import Path

ANCHOR = '''        paths.hostRoot = paths.elfDirectory;
        paths.cdRoot = paths.elfDirectory;
        paths.mcRoot = paths.elfDirectory / "mc0";
    }

    setIoPaths(paths);'''

PATCHED = '''        paths.hostRoot = paths.elfDirectory;
        paths.cdRoot = paths.elfDirectory;
        paths.mcRoot = paths.elfDirectory / "mc0";
    }

#if defined(__ANDROID__)
    // The imported disc directory can be atomically replaced. Keep saves under
    // the app-private persistent root instead of inside the extracted ISO tree.
    if (const char *memoryCardRoot = std::getenv("PS2X_MEMCARD_DIR");
        memoryCardRoot && *memoryCardRoot)
    {
        paths.mcRoot = std::filesystem::path(memoryCardRoot);
    }
#endif

    setIoPaths(paths);'''


def patch(source: Path) -> None:
    text = source.read_text(encoding="utf-8")
    if "memoryCardRoot = std::getenv(\\"PS2X_MEMCARD_DIR\\")" in text:
        if text.count("memoryCardRoot = std::getenv(\\"PS2X_MEMCARD_DIR\\")") != 1:
            raise SystemExit("Patch de memory card duplicado em " + str(source))
        print("Celosia Android memory-card path hook already applied: " + str(source))
        return
    if text.count(ANCHOR) != 1:
        raise SystemExit("Não reconheci exatamente o ponto de configuração do memory card: " + str(source))
    source.write_text(text.replace(ANCHOR, PATCHED, 1), encoding="utf-8")
    print("Celosia Android memory-card path hook applied: " + str(source))


def self_test() -> None:
    sample = "void configureIoPathsFromElf() {\\n" + ANCHOR + "\\n}"
    result = sample.replace(ANCHOR, PATCHED, 1)
    assert result.count('std::getenv("PS2X_MEMCARD_DIR")') == 1
    assert result.index("paths.mcRoot = std::filesystem::path(memoryCardRoot)") < result.index("setIoPaths(paths)")
    assert "#if defined(__ANDROID__)" in result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("Celosia Android path patch self-test passed.")
        return 0
    if args.source is None:
        parser.error("--source is required unless --self-test is used")
    if not args.source.is_file():
        raise SystemExit("Arquivo de runtime Celosia não encontrado: " + str(args.source))
    patch(args.source)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
