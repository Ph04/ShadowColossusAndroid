[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string] $IsoPath,

    [ValidateRange(1, 64)]
    [int] $Jobs = 1,

    [ValidateSet("release", "debug")]
    [string] $BuildType = "release",

    [switch] $SkipVu1,
    [switch] $CodegenOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ExitCode = 0

Push-Location $ProjectRoot
try {
    function Require-Command([string] $Name, [string] $Purpose) {
        if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
            throw "Comando '$Name' não encontrado. $Purpose"
        }
    }

    Require-Command "git" "Instale o Git for Windows e abra este script a partir do clone do projeto."
    Require-Command "cmake" "Instale o CMake e habilite-o no PATH."
    if (-not (Test-Path (Join-Path $ProjectRoot ".gitmodules"))) {
        throw "Este diretório não parece ser o clone Git completo do projeto."
    }

    Write-Host "== Inicializando/verificando submódulos (incluindo PS2Recomp) ==" -ForegroundColor Cyan
    & git submodule update --init --recursive
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao preparar os submódulos. Confira a conexão e tente novamente."
    }

    if ([string]::IsNullOrWhiteSpace($IsoPath)) {
        $candidates = @()
        foreach ($folder in @("Game", "game", "isos")) {
            $candidateFolder = Join-Path $ProjectRoot $folder
            if (Test-Path $candidateFolder -PathType Container) {
                $candidates += @(Get-ChildItem -LiteralPath $candidateFolder -Filter "*.iso" -File -ErrorAction SilentlyContinue |
                    Where-Object { $_.Length -eq 4635918336 })
            }
        }

        if ($candidates.Count -eq 1) {
            $IsoPath = $candidates[0].FullName
            Write-Host "ISO encontrada: $IsoPath"
        } else {
            $IsoPath = Read-Host "Caminho completo para sua ISO PAL SCES-53326 v1.00"
        }
    }

    if ([string]::IsNullOrWhiteSpace($IsoPath)) {
        throw "Nenhum caminho de ISO foi informado."
    }
    $isoResolved = Resolve-Path -LiteralPath $IsoPath -ErrorAction Stop
    if ($isoResolved.Provider.Name -ne "FileSystem" -or (Test-Path $isoResolved.Path -PathType Container)) {
        throw "O caminho informado não é um arquivo ISO."
    }
    $isoFile = Get-Item -LiteralPath $isoResolved.Path
    if ($isoFile.Length -ne 4635918336) {
        throw ("Tamanho incompatível: {0:N0} bytes; esperado exatamente 4.635.918.336 bytes (SCES-53326 PAL v1.00)." -f $isoFile.Length)
    }

    $py = Get-Command "py" -ErrorAction SilentlyContinue
    if ($py) {
        $pythonExe = $py.Source
        $pythonPrefix = @("-3")
    } else {
        Require-Command "python" "Instale Python 3.10+ e habilite-o no PATH."
        $pythonExe = (Get-Command "python").Source
        $pythonPrefix = @()
    }

    if (-not $CodegenOnly) {
        Require-Command "java" "Instale JDK 17 e habilite java no PATH."
        Require-Command "gradle" "Instale Gradle 8.9 e habilite gradle no PATH."
        $sdk = $env:ANDROID_HOME
        if ([string]::IsNullOrWhiteSpace($sdk)) { $sdk = $env:ANDROID_SDK_ROOT }
        if ([string]::IsNullOrWhiteSpace($sdk) -or -not (Test-Path $sdk -PathType Container)) {
            throw "Defina ANDROID_HOME ou ANDROID_SDK_ROOT para seu Android SDK (incluindo NDK 28.2.13676358 e CMake 3.22.1)."
        }
    }

    $commandArgs = @("scripts/prepare_sotc_android.py", "--iso", $isoFile.FullName, "--jobs", "$Jobs")
    if ($CodegenOnly) { $commandArgs += "--codegen-only" }
    else { $commandArgs += @("--build-type", $BuildType) }
    if ($SkipVu1) { $commandArgs += "--skip-vu1" }

    Write-Host ""
    Write-Host "ISO: $($isoFile.FullName)"
    Write-Host "Tarefas paralelas: $Jobs"
    Write-Host "VU1 estático: $(if ($SkipVu1) { 'ignorado (primeiro teste)' } else { 'ativado' })"
    if ($CodegenOnly) {
        Write-Host "Modo: gerar e validar fontes; não compilar APK."
    } else {
        Write-Host "Modo: gerar fontes e compilar APK $BuildType."
    }
    Write-Host "A ISO e as fontes geradas permanecem neste computador; o script não as envia ao GitHub." -ForegroundColor Yellow
    Write-Host ""

    & $pythonExe @pythonPrefix @commandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "A preparação falhou (código $LASTEXITCODE). Leia o primeiro erro completo exibido acima."
    }

    if (-not $CodegenOnly) {
        $apkType = if ($BuildType -eq "debug") { "debug" } else { "release" }
        $apkName = if ($BuildType -eq "debug") { "app-debug.apk" } else { "app-release.apk" }
        $apkPath = Join-Path $ProjectRoot "app/build/outputs/apk/$apkType/$apkName"
        if (Test-Path $apkPath -PathType Leaf) {
            Write-Host ""
            Write-Host "APK personalizado criado: $apkPath" -ForegroundColor Green
            Write-Host "Este APK contém as fontes geradas a partir da ISO usada; ainda precisa de teste real no Android."
            if (Get-Command "explorer.exe" -ErrorAction SilentlyContinue) {
                Start-Process explorer.exe -ArgumentList ('/select,"' + $apkPath + '"')
            }
        } else {
            throw "Gradle terminou, mas não encontrei o APK esperado em $apkPath."
        }
    }
} catch {
    Write-Host ""
    Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
    $ExitCode = 1
} finally {
    Pop-Location
}

if ($ExitCode -ne 0) { exit $ExitCode }
