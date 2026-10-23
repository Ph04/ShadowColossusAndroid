#!/usr/bin/env python3
"""Patch Celosia main() to resolve the USA boot ELF and persist Android boot diagnostics."""
from __future__ import annotations

import argparse
from pathlib import Path

INCLUDE_ANCHOR = "#include <cstdlib>"
INCLUDE_PATCH = '#include <cstdlib>\n#if defined(__ANDROID__)\n#include <fstream>\n#endif'

PATH_ANCHOR = "    std::filesystem::path getExecutablePath(int argc, char *argv[])\n    {\n"
PATH_PATCH = '''    std::filesystem::path getExecutablePath(int argc, char *argv[])
    {
#if defined(__ANDROID__)
        if (const char *androidBootElf = std::getenv("SOTC_ANDROID_BOOT_ELF");
            androidBootElf && *androidBootElf)
        {
            std::cout << "[android] using explicit boot ELF path" << std::endl;
            return std::filesystem::path(androidBootElf);
        }
#endif
'''

MAIN_ANCHOR = '''#if defined(__ANDROID__)
    redirectStdioToLogcat();
#endif
    setupTerminateLogger();

    try
'''
MAIN_PATCH = '''#if defined(__ANDROID__)
    redirectStdioToLogcat();
    androidBootLog("entry", "NativeActivity runner main() entered");
    androidPrepareWorkingDirectory();
#endif
    setupTerminateLogger();

    try
'''


def helper_block() -> str:
    return r"""
#if defined(__ANDROID__)
    void androidBootLog(const char *stage, const std::string &detail = std::string())
    {
        const char *filesDir = std::getenv("SOTC_ANDROID_FILES_DIR");
        std::filesystem::path root;
        if (filesDir && *filesDir)
            root = std::filesystem::path(filesDir);
        else
        {
            std::error_code currentError;
            root = std::filesystem::current_path(currentError);
            if (currentError)
                root = ".";
        }

        const std::filesystem::path logPath = root / "logs" / "sotc-boot.log";
        std::error_code directoryError;
        std::filesystem::create_directories(logPath.parent_path(), directoryError);
        if (!directoryError)
        {
            std::ofstream log(logPath, std::ios::out | std::ios::app);
            if (log)
                log << "[CelosiaAndroid][" << stage << "] " << detail << '\n';
        }
        __android_log_print(ANDROID_LOG_INFO, "SOTC_BOOT", "[%s] %s", stage, detail.c_str());
    }

    void androidPrepareWorkingDirectory()
    {
        const char *filesDir = std::getenv("SOTC_ANDROID_FILES_DIR");
        if (filesDir && *filesDir)
        {
            if (chdir(filesDir) != 0)
            {
                androidBootLog("cwd-failed", std::string("chdir failed; target=") + filesDir);
                return;
            }
        }
        std::error_code error;
        const auto cwd = std::filesystem::current_path(error);
        androidBootLog("working-directory", error ? "current_path failed" : cwd.string());
        const char *boot = std::getenv("SOTC_ANDROID_BOOT_ELF");
        androidBootLog("boot-env", std::string("SOTC_ANDROID_BOOT_ELF=") + (boot ? boot : "(unset)"));
    }
#endif
"""


def patch(source: Path) -> None:
    text = source.read_text(encoding="utf-8")
    log_marker = 'void androidBootLog(const char *stage'
    if log_marker in text:
        if text.count(log_marker) != 1 or "SOTC_ANDROID_BOOT_ELF" not in text:
            raise SystemExit("Patch de inicialização Android duplicado/incompleto em " + str(source))
        print("Celosia Android startup patch already applied: " + str(source))
        return

    for anchor, replacement, label in (
        (INCLUDE_ANCHOR, INCLUDE_PATCH, "includes"),
        (PATH_ANCHOR, PATH_PATCH, "boot path"),
        (MAIN_ANCHOR, MAIN_PATCH, "main entry"),
    ):
        if text.count(anchor) != 1:
            raise SystemExit("Não reconheci exatamente o ponto de patch (" + label + ") em " + str(source))

    # Place helpers inside the existing anonymous namespace, immediately before
    # getExecutablePath(), after the stdio/logging and terminate helpers.
    text = text.replace(INCLUDE_ANCHOR, INCLUDE_PATCH, 1)
    text = text.replace(PATH_ANCHOR, helper_block() + "\n" + PATH_PATCH, 1)
    text = text.replace(MAIN_ANCHOR, MAIN_PATCH, 1)

    # Persist checkpoints immediately after path resolution and at each phase
    # so a force-close can be diagnosed from filesDir/logs/sotc-boot.log.
    path_line = "        std::filesystem::path pathObj = getExecutablePath(argc, argv);\n"
    path_replacement = path_line + '''#if defined(__ANDROID__)
        std::error_code bootError;
        const bool bootExists = std::filesystem::is_regular_file(pathObj, bootError) && !bootError;
        std::string bootDetail = std::string("path=") + pathObj.string() +
                                 ";exists=" + (bootExists ? "true" : "false");
        if (bootExists)
        {
            std::error_code sizeError;
            const auto bytes = std::filesystem::file_size(pathObj, sizeError);
            bootDetail += ";bytes=" + (sizeError ? std::string("unknown") : std::to_string(bytes));
        }
        androidBootLog("boot-path", bootDetail);
        if (!bootExists)
        {
            androidBootLog("boot-abort", "boot ELF missing/unreadable before runtime initialization");
            return 2;
        }
        const std::filesystem::path discRoot = pathObj.parent_path();
        for (const char *required : {"KERNEL.XFF", "MANAGER.XFF", "GAMECORE.XFF", "SYSTEM.CNF"})
        {
            const auto item = discRoot / required;
            std::error_code itemError;
            const bool exists = std::filesystem::is_regular_file(item, itemError) && !itemError;
            androidBootLog("disc-file", std::string(required) + (exists ? "=present" : "=missing"));
            if (!exists)
            {
                androidBootLog("boot-abort", std::string("required disc file missing: ") + required);
                return 3;
            }
        }
#endif
'''
    if text.count(path_line) != 1:
        raise SystemExit("Não encontrei a resolução única do caminho de boot em " + str(source))
    text = text.replace(path_line, path_replacement, 1)

    replacements = [
        ('        std::cout << "[main] creating runtime (" << sizeof(PS2Runtime) << " bytes)" << std::endl;\n',
         '        std::cout << "[main] creating runtime (" << sizeof(PS2Runtime) << " bytes)" << std::endl;\n#if defined(__ANDROID__)\n        androidBootLog("runtime-create", "creating PS2Runtime");\n#endif\n'),
        ('        if (!runtime.initialize(windowTitle.c_str()))\n',
         '#if defined(__ANDROID__)\n        androidBootLog("runtime-initialize", std::string("starting; title=") + windowTitle);\n#endif\n        if (!runtime.initialize(windowTitle.c_str()))\n'),
        ('            std::cerr << "Failed to initialize PS2 runtime" << std::endl;\n',
         '            std::cerr << "Failed to initialize PS2 runtime" << std::endl;\n#if defined(__ANDROID__)\n            androidBootLog("runtime-initialize-failed", "PS2Runtime::initialize returned false");\n#endif\n'),
        ('        std::cout << "[main] runtime initialized, loading " << filePathStr << std::endl;\n',
         '        std::cout << "[main] runtime initialized, loading " << filePathStr << std::endl;\n#if defined(__ANDROID__)\n        androidBootLog("runtime-initialized", "PS2Runtime::initialize returned true");\n        androidBootLog("elf-load", filePathStr);\n#endif\n'),
        ('            std::cerr << "Failed to load ELF file: " << filePathStr << std::endl;\n',
         '            std::cerr << "Failed to load ELF file: " << filePathStr << std::endl;\n#if defined(__ANDROID__)\n            androidBootLog("elf-load-failed", filePathStr);\n#endif\n'),
        ('        runtime.run();\n',
         '#if defined(__ANDROID__)\n        androidBootLog("runtime-run", "entering PS2Runtime::run");\n#endif\n        runtime.run();\n#if defined(__ANDROID__)\n        androidBootLog("runtime-run-returned", "PS2Runtime::run returned");\n#endif\n'),
    ]

    # The run() checkpoint replacement has a conditional that must remain
    # balanced around only the diagnostic call.
    for anchor, replacement, *extra in replacements:
        if text.count(anchor) != 1:
            raise SystemExit("Não encontrei exatamente o ponto de checkpoint em " + str(source) + ": " + anchor[:70])
        text = text.replace(anchor, replacement, 1)

    # Catch handlers should record the exception in the persistent boot log.
    catch_anchor = '''    catch (const std::exception &e)
    {
        std::cerr << "[main] fatal exception: " << e.what() << std::endl;
'''
    catch_patch = '''    catch (const std::exception &e)
    {
#if defined(__ANDROID__)
        androidBootLog("fatal-exception", e.what());
#endif
        std::cerr << "[main] fatal exception: " << e.what() << std::endl;
'''
    if text.count(catch_anchor) != 1:
        raise SystemExit("Não encontrei o handler de exceção esperado em " + str(source))
    text = text.replace(catch_anchor, catch_patch, 1)

    source.write_text(text, encoding="utf-8")
    print("Celosia Android startup diagnostics applied: " + str(source))


def self_test() -> None:
    assert 'std::getenv("SOTC_ANDROID_BOOT_ELF")' in PATH_PATCH
    assert "androidBootLog(\\"boot-path\\"" in (
        'androidBootLog("boot-path", bootDetail);'
    )
    assert "SOTC_ANDROID_FILES_DIR" in helper_block()
    assert "SOTC_ANDROID_BOOT_ELF" in helper_block()
    assert "runtime-initialize-failed" in helper_block() or "SOTC_ANDROID_FILES_DIR" in helper_block()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        print("Celosia Android startup patch self-test passed.")
        return 0
    if args.source is None:
        parser.error("--source is required unless --self-test is used")
    if not args.source.is_file():
        raise SystemExit("Arquivo main.cpp do runtime Celosia não encontrado: " + str(args.source))
    patch(args.source)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
