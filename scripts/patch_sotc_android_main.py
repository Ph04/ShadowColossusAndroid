#!/usr/bin/env python3
"""Add Android logcat forwarding to the game-specific PS2Recomp main()."""
from __future__ import annotations

import argparse
from pathlib import Path

INCLUDE_ANCHOR = '#include <vector>'
INCLUDE_BLOCK = '''#include <vector>

#if defined(__ANDROID__)
#include <android/log.h>
#include <unistd.h>
#include <thread>
#include <cstdio>
#include <cstring>
#endif'''

NAMESPACE_ANCHOR = '''namespace
{
    std::filesystem::path executableDirectory()'''
NAMESPACE_BLOCK = '''namespace
{
#if defined(__ANDROID__)
    // Port/src/main.cpp replaces PS2Recomp's generic runner main(). Keep its
    // Android logcat forwarding so boot failures remain diagnosable on-device.
    int g_sotcLogcatPipe[2]{-1, -1};
    std::thread g_sotcLogcatThread;

    void redirectStdioToLogcat()
    {
        if (pipe(g_sotcLogcatPipe) != 0)
            return;

        setvbuf(stdout, nullptr, _IOLBF, 0);
        setvbuf(stderr, nullptr, _IONBF, 0);
        if (dup2(g_sotcLogcatPipe[1], STDOUT_FILENO) < 0 ||
            dup2(g_sotcLogcatPipe[1], STDERR_FILENO) < 0)
        {
            close(g_sotcLogcatPipe[0]);
            close(g_sotcLogcatPipe[1]);
            g_sotcLogcatPipe[0] = g_sotcLogcatPipe[1] = -1;
            return;
        }

        g_sotcLogcatThread = std::thread([]()
        {
            FILE *reader = fdopen(g_sotcLogcatPipe[0], "r");
            if (!reader)
                return;
            char line[1024];
            while (fgets(line, sizeof(line), reader))
            {
                size_t len = std::strlen(line);
                if (len > 0 && line[len - 1] == '\\n')
                    line[len - 1] = '\\0';
                __android_log_write(ANDROID_LOG_INFO, "sotc", line);
            }
            fclose(reader);
            g_sotcLogcatPipe[0] = -1;
        });
    }
#endif

    std::filesystem::path executableDirectory()'''
MAIN_ANCHOR = '''int main(int argc, char *argv[])
{
#if defined(_WIN32)'''
MAIN_BLOCK = '''int main(int argc, char *argv[])
{
#if defined(__ANDROID__)
    redirectStdioToLogcat();
#endif
#if defined(_WIN32)'''


def patch(source: Path) -> None:
    text = source.read_text(encoding="utf-8")
    if "redirectStdioToLogcat();" in text and 'ANDROID_LOG_INFO, "sotc"' in text:
        print(f"Android logcat forwarding already applied: {source}")
        return
    for anchor, block, label in (
        (INCLUDE_ANCHOR, INCLUDE_BLOCK, "includes"),
        (NAMESPACE_ANCHOR, NAMESPACE_BLOCK, "anonymous namespace"),
        (MAIN_ANCHOR, MAIN_BLOCK, "main()"),
    ):
        if anchor not in text:
            raise SystemExit(f"Não reconheci a seção {label} em {source}; nenhum arquivo foi gravado.")
        text = text.replace(anchor, block, 1)
    source.write_text(text, encoding="utf-8")
    print(f"Android logcat forwarding aplicado a: {source}")


def self_test() -> None:
    sample = "#include <vector>\nnamespace\n{\n    std::filesystem::path executableDirectory()\nint main(int argc, char *argv[])\n{\n#if defined(_WIN32)"
    assert INCLUDE_ANCHOR in sample
    assert NAMESPACE_ANCHOR in sample
    assert MAIN_ANCHOR in sample
    changed = sample.replace(INCLUDE_ANCHOR, INCLUDE_BLOCK, 1)
    changed = changed.replace(NAMESPACE_ANCHOR, NAMESPACE_BLOCK, 1)
    changed = changed.replace(MAIN_ANCHOR, MAIN_BLOCK, 1)
    assert 'ANDROID_LOG_INFO, "sotc"' in changed
    assert changed.count("redirectStdioToLogcat();") == 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, help="path to upstream Port/src/main.cpp")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("Logcat patch self-test passed.")
        return 0
    if not args.source:
        parser.error("--source is required unless --self-test is used")
    if not args.source.is_file():
        raise SystemExit(f"Arquivo upstream não encontrado: {args.source}")
    patch(args.source)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
