#!/usr/bin/env pwsh
# Copies a compiled engine binary into the Android module's jniLibs directory so
# Gradle packages it as a native library.
#
# Why a .so name: since Android 10 the kernel refuses execve on files in
# writable app storage. The package manager extracts jniLibs to nativeLibraryDir,
# which is the only exec-allowed location. Verified on Android 16 — see
# handoff.md §1.4.
#
# Usage:
#   ./script/android/stage-engine.ps1 -Abi arm64-v8a -Source <path-to-opencode>
#   ./script/android/stage-engine.ps1 -Abi x86_64  -Source <path-to-opencode>

param(
    [Parameter(Mandatory = $true)][ValidateSet("arm64-v8a", "x86_64")][string]$Abi,
    [Parameter(Mandatory = $true)][string]$Source,
    # Directory holding the `git` and `rg` binaries built by build-tools.ps1.
    # They are shipped as lib*.so for the same reason as the engine: only
    # nativeLibraryDir is exec-allowed on API 29+.
    [string]$ToolsDir,
    [string]$RepoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Source)) { throw "engine binary not found: $Source" }

$dest = Join-Path $RepoRoot "android/app/src/main/jniLibs/$Abi"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

# Android requires the name to match lib<name>.so. The engine is invoked through
# this path directly, so the name is arbitrary but must stay stable.
$target = Join-Path $dest "libopencode.so"
Copy-Item $Source $target -Force

$sizeMb = [math]::Round((Get-Item $target).Length / 1MB, 1)
Write-Host "staged $Abi engine -> $target ($sizeMb MB)"

# Sanity check: a wrong-architecture binary would install fine and fail at exec
# with a bare "Permission denied", which is hard to diagnose. Fail here instead.
$magic = [System.IO.File]::ReadAllBytes($target)[0..3]
if (-not ($magic[0] -eq 0x7F -and $magic[1] -eq 0x45 -and $magic[2] -eq 0x4C -and $magic[3] -eq 0x46)) {
    throw "not an ELF file: $target"
}

$machine = [System.IO.File]::ReadAllBytes($target)[18]
$expected = if ($Abi -eq "arm64-v8a") { 0xB7 } else { 0x3E }
if ($machine -ne $expected) {
    throw "architecture mismatch: $target reports e_machine=0x$($machine.ToString('X2')), expected 0x$($expected.ToString('X2')) for $Abi"
}
Write-Host "verified ELF e_machine=0x$($machine.ToString('X2')) for $Abi"

# Tool binaries. EngineBinary.linkTools() hard-links these to
# files/engine/tools/bin/{git,rg} and puts that directory on PATH, which is how
# the engine's `which("git")` and `which("rg")` find them.
if ($ToolsDir) {
    foreach ($tool in @("git", "rg")) {
        $from = Join-Path $ToolsDir $tool
        if (-not (Test-Path $from)) { throw "tool binary missing: $from" }
        $to = Join-Path $dest "lib$tool.so"
        Copy-Item $from $to -Force

        $bytes = [System.IO.File]::ReadAllBytes($to)
        $toolMachine = [BitConverter]::ToUInt16($bytes, 18)
        if ($toolMachine -ne $expected) {
            throw "architecture mismatch: $to reports e_machine=0x$('{0:X2}' -f $toolMachine), expected 0x$('{0:X2}' -f $expected) for $Abi"
        }

        # 16 KB gate. A 4 KB-aligned ELF installs and runs fine on the emulator
        # (4 KB kernel) and fails or degrades on Android 15+ devices with a 16 KB
        # kernel, which is exactly the case this project cannot test locally.
        $phoff = [BitConverter]::ToUInt64($bytes, 32)
        $phentsize = [BitConverter]::ToUInt16($bytes, 54)
        $phnum = [BitConverter]::ToUInt16($bytes, 56)
        for ($i = 0; $i -lt $phnum; $i++) {
            $o = [int]$phoff + $i * $phentsize
            if ([BitConverter]::ToUInt32($bytes, $o) -eq 1) {
                $align = [BitConverter]::ToUInt64($bytes, $o + 48)
                if ($align -lt 0x4000) {
                    throw "$to has a PT_LOAD segment with p_align=0x$('{0:X}' -f $align); needs 0x4000 for 16 KB page sizes"
                }
            }
        }

        $toolMb = [math]::Round($bytes.Length / 1MB, 2)
        Write-Host "staged $Abi $tool -> $to ($toolMb MB, 16 KB aligned)"
    }
}
else {
    Write-Warning "no -ToolsDir given: `git` and `rg` will be missing from the APK, so VCS and ripgrep-backed search stay broken (handoff.md §2.31)"
}
