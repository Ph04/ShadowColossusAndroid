#!/usr/bin/env python3
"""Add Android logcat and private log-file forwarding to the game-specific main()."""
from __future__ import annotations

import argparse
import re
from pathlib import Path

INCLUDE_ANCHOR = '#include <vector>'
INCLUDE_BLOCK = '''#include <vector>

#if defined(__ANDROID__)
#include <android/log.h>
#include <unistd.h>
#include <thread>
#include <cstdio>
#include <cstring>
#include <fstream>
#endif'''

NAMESPACE_ANCHOR = '''namespace
{
    std::filesystem::path executableDirectory()'''
NAMESPACE_BLOCK = '''namespace
{
#if defined(__ANDROID__)
    // Port/src/main.cpp replaces the generic runner main(). Keep boot errors
    // visible in logcat and in files/logs/sotc-boot.log so they can be shared
    // from the launcher without requiring ADB.
    int g_sotcLogcatPipe[2]{-1, -1};
    std::thread g_sotcLogcatThread;

    void stopStdioRedirect()
    {
        std::cout.flush();
        std::cerr.flush();
        std::fflush(stdout);
        std::fflush(stderr);
        if (g_sotcLogcatPipe[1] >= 0)
        {
            close(STDOUT_FILENO);
            close(STDERR_FILENO);
            close(g_sotcLogcatPipe[1]);
            g_sotcLogcatPipe[1] = -1;
        }
        if (g_sotcLogcatThread.joinable())
            g_sotcLogcatThread.join();
    }

    void redirectStdioToLogcat()
    {
        if (pipe(g_sotcLogcatPipe) != 0)
            return;

        std::error_code pathError;
        const auto logDirectory = std::filesystem::current_path() / "logs";
        std::filesystem::create_directories(logDirectory, pathError);
        const std::string logPath = (logDirectory / "sotc-boot.log").string();

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

        g_sotcLogcatThread = std::thread([logPath]()
        {
            std::ofstream logFile(logPath, std::ios::trunc);
            FILE *reader = fdopen(g_sotcLogcatPipe[0], "r");
            if (!reader)
            {
                close(g_sotcLogcatPipe[0]);
                g_sotcLogcatPipe[0] = -1;
                return;
            }
            char line[1024];
            while (fgets(line, sizeof(line), reader))
            {
                size_t len = std::strlen(line);
                if (len > 0 && line[len - 1] == '\\n')
                    line[--len] = '\\0';
                __android_log_write(ANDROID_LOG_INFO, "sotc", line);
                if (logFile)
                {
                    logFile.write(line, static_cast<std::streamsize>(len));
                    logFile.put('\\n');
                    logFile.flush();
                }
            }
            fclose(reader);
            g_sotcLogcatPipe[0] = -1;
        });
    }

    struct ScopedSotcLogcatCleanup
    {
        ~ScopedSotcLogcatCleanup() { stopStdioRedirect(); }
    };
#endif

    std::filesystem::path executableDirectory()'''
MAIN_ANCHOR = '''int main(int argc, char *argv[])
{
#if defined(_WIN32)'''
MAIN_BLOCK = '''int main(int argc, char *argv[])
{
#if defined(__ANDROID__)
    redirectStdioToLogcat();
    [[maybe_unused]] const ScopedSotcLogcatCleanup logcatCleanup;
#endif
#if defined(_WIN32)'''

PATCHED_NAMESPACE = re.compile(
    r'namespace\n\{\n#if defined\(__ANDROID__\)\n    // Port/src/main\.cpp replaces.*?'
    r'#endif\n\n    std::filesystem::path executableDirectory\(\)',
    re.DOTALL,
)


def patch(source: Path) -> None:
    text = source.read_text(encoding="utf-8")
    if "sotc-boot.log" in text and "ScopedSotcLogcatCleanup" in text and "redirectStdioToLogcat();" in text:
        print(f"Android logcat/file diagnostics already applied: {source}")
        return

    # Upgrade a checkout previously patched by an older version of this helper.
    if "redirectStdioToLogcat();" in text and 'ANDROID_LOG_INFO, "sotc"' in text:
        if "#include <fstream>" not in text:
            text = text.replace("#include <cstring>", "#include <cstring>\n#include <fstream>", 1)
        text, replacements = PATCHED_NAMESPACE.subn(NAMESPACE_BLOCK, text, count=1)
        if replacements != 1:
            raise SystemExit(f"Não reconheci o hook Android antigo em {source}; nenhum patch foi gravado.")
        source.write_text(text, encoding="utf-8")
        print(f"Hook Android atualizado com log local: {source}")
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
    print(f"Android logcat/file diagnostics applied: {source}")


def self_test() -> None:
    sample = (
        "#include <vector>\n"
        "namespace\n{\n"
        "    std::filesystem::path executableDirectory()\n"
        "int main(int argc, char *argv[])\n{\n#if defined(_WIN32)"
    )
    assert INCLUDE_ANCHOR in sample
    assert NAMESPACE_ANCHOR in sample
    assert MAIN_ANCHOR in sample
    changed = sample.replace(INCLUDE_ANCHOR, INCLUDE_BLOCK, 1)
    changed = changed.replace(NAMESPACE_ANCHOR, NAMESPACE_BLOCK, 1)
    changed = changed.replace(MAIN_ANCHOR, MAIN_BLOCK, 1)
    assert 'ANDROID_LOG_INFO, "sotc"' in changed
    assert "sotc-boot.log" in changed
    assert "ScopedSotcLogcatCleanup" in changed
    assert changed.count("redirectStdioToLogcat();") == 1

    old_patch = (
        "namespace\n{\n#if defined(__ANDROID__)\n"
        "    // Port/src/main.cpp replaces the generic runner main(). Keep its\n"
        "    // Android logcat forwarding so boot failures remain diagnosable on-device.\n"
        "    int g_sotcLogcatPipe[2]{-1, -1};\n"
        "    std::thread g_sotcLogcatThread;\n"
        "    void redirectStdioToLogcat() { }\n"
        "#endif\n\n"
        "    std::filesystem::path executableDirectory()"
    )
    upgraded, count = PATCHED_NAMESPACE.subn(NAMESPACE_BLOCK, old_patch, count=1)
    assert count == 1 and "sotc-boot.log" in upgraded


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, help="path to upstream Port/src/main.cpp")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("Android logcat/file patch self-test passed.")
        return 0
    if not args.source:
        parser.error("--source is required unless --self-test is used")
    if not args.source.is_file():
        raise SystemExit(f"Arquivo upstream não encontrado: {args.source}")
    patch(args.source)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
