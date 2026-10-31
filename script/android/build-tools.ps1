#!/usr/bin/env pwsh
# Builds the `git` and `rg` binaries that the engine shells out to, for both
# Android ABIs, and drops them in .cache/tools/<abi>/ ready for staging.
#
# Why build instead of downloading: every prebuilt distribution of these tools
# for Android (Termux, most CI artifacts) is linked with a 4 KB max-page-size.
# Android 15+ devices can run a 16 KB kernel, where a 4 KB-aligned ELF either
# crashes or is silently forced into compatibility mode. Building against NDK
# r29 gives 16 KB alignment by default, and we pass the linker flags explicitly
# so a future NDK bump cannot quietly regress it.
#
# Why no curl/openssl in git: v1 only needs local VCS operations (status, diff,
# branch, commit). Dropping the network stack removes libcurl, libssl, libcrypto
# and the CA bundle from the APK, and removes every DT_NEEDED except libc/libz/
# libdl, which Android already provides. Clone/push/fetch over https is a
# deliberate later step; see handoff.md.
#
# Prerequisites (all user-level, no admin):
#   - Android SDK with NDK 29.x           (only NDK 29 is 16 KB-aligned by default)
#   - Git for Windows                     (provides sh, sed, awk for git's Makefile)
#   - rustup + targets aarch64/x86_64-linux-android  (for rg)
#
# Usage:
#   ./script/android/build-tools.ps1
#   ./script/android/build-tools.ps1 -Abi arm64-v8a

param(
    [ValidateSet("arm64-v8a", "x86_64", "all")][string]$Abi = "all",
    [string]$WorkDir = "C:\ocbuild",
    [string]$RepoRoot = (Resolve-Path "$PSScriptRoot/../..").Path
)

$ErrorActionPreference = "Stop"

$GitVersion = "2.56.0"
$RipgrepVersion = "15.1.0"
$AndroidApi = "29"

# Paths with spaces break the tools' own shell invocations (see handoff.md §7:
# the unzip shim had to live at C:\ocbin for exactly this reason), so the work
# directory must be space-free.
if ($WorkDir -match ' ') { throw "WorkDir must not contain spaces: $WorkDir" }

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$ndkRoot = Join-Path $sdk "ndk"
$ndk = Get-ChildItem $ndkRoot -Directory |
    Where-Object { $_.Name -like "29.*" } |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (-not $ndk) { throw "NDK 29.x not found under $ndkRoot. Only NDK 29 aligns to 16 KB by default." }

$ndkBin = "$($ndk.FullName)/toolchains/llvm/prebuilt/windows-x86_64/bin".Replace('\', '/')
$make = Join-Path $ndk.FullName "prebuilt/windows-x86_64/bin/make.exe"
if (-not (Test-Path $make)) { throw "make.exe not found in NDK: $make" }

$gitUsrBin = "C:\Program Files\Git\usr\bin"
if (-not (Test-Path "$gitUsrBin/sh.exe")) { throw "Git for Windows not found at $gitUsrBin" }

# git's Makefile shells out to `sh` by bare name for feature probes. Without
# this on PATH every probe fails and the build silently takes the wrong branch.
$env:PATH = "$gitUsrBin;$env:PATH"

Write-Host "NDK   : $($ndk.Name)"
Write-Host "make  : $make"
Write-Host "sh    : $gitUsrBin"

$targets = if ($Abi -eq "all") { @("arm64-v8a", "x86_64") } else { @($Abi) }

# git's Makefile and cargo both write progress to stderr. Under
# $ErrorActionPreference = "Stop", PowerShell turns any stderr line from a native
# command into a terminating error, so capture output with the preference
# relaxed and decide from the exit code instead.
function Invoke-Native([scriptblock]$block) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $block 2>&1
        return @{ Output = $output; ExitCode = $LASTEXITCODE }
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Get-AbiConfig([string]$name) {
    if ($name -eq "arm64-v8a") {
        return @{ Clang = "aarch64-linux-android$AndroidApi-clang.cmd"; UnameM = "aarch64"; Machine = 0xB7; Rust = "aarch64-linux-android" }
    }
    return @{ Clang = "x86_64-linux-android$AndroidApi-clang.cmd"; UnameM = "x86_64"; Machine = 0x3E; Rust = "x86_64-linux-android" }
}

# --- sources -----------------------------------------------------------------

function Get-Sources {
    $srcGit = Join-Path $WorkDir "git-$GitVersion"
    if (-not (Test-Path (Join-Path $srcGit "Makefile"))) {
        $tarball = Join-Path $WorkDir "git-$GitVersion.tar.gz"
        if (-not (Test-Path $tarball)) {
            Write-Host "downloading git $GitVersion"
            Invoke-WebRequest "https://mirrors.edge.kernel.org/pub/software/scm/git/git-$GitVersion.tar.gz" -OutFile $tarball -UseBasicParsing
        }
        # gzip, not xz: Windows' bundled bsdtar has no xz support.
        & tar.exe -xzf $tarball -C $WorkDir
    }

    $srcRg = Join-Path $WorkDir "ripgrep-$RipgrepVersion"
    if (-not (Test-Path (Join-Path $srcRg "Cargo.toml"))) {
        $tarball = Join-Path $WorkDir "ripgrep-$RipgrepVersion.tar.gz"
        if (-not (Test-Path $tarball)) {
            Write-Host "downloading ripgrep $RipgrepVersion"
            Invoke-WebRequest "https://github.com/BurntSushi/ripgrep/archive/refs/tags/$RipgrepVersion.tar.gz" -OutFile $tarball -UseBasicParsing
        }
        & tar.exe -xzf $tarball -C $WorkDir
    }
    return @{ Git = $srcGit; Rg = $srcRg }
}

# --- git ---------------------------------------------------------------------

function Build-Git([string]$src, [string]$abi, $cfg, [string]$dest) {
    Write-Host "`n=== git $GitVersion / $abi ==="

    # git's Makefile probes the host with uname and picks the MINGW/NT branch when
    # it sees Windows. Forcing uname_S/uname_M on the command line is the
    # supported way to cross-compile without patching the Makefile.
    #
    # NO_PTHREADS: bionic has no pthread_setcancelstate/PTHREAD_CANCEL_DISABLE,
    # which run-command.c uses for its atfork handler. Upstream's own NO_PTHREADS
    # path uses sigprocmask instead and is the correct fallback here.
    #
    # NO_RUST: git 2.56 ships a Rust component; the C-only build is complete for
    # the local operations we need and avoids a second cross-compile toolchain.
    # NOTE: do not name this `$args` — that is a PowerShell automatic variable and
    # assigning to it inside a function silently produces an empty array.
    $makeArgs = @(
        "uname_S=Linux", "uname_M=$($cfg.UnameM)",
        "NO_CURL=1", "NO_EXPAT=1", "NO_OPENSSL=1", "NO_GETTEXT=1",
        "NO_TCLTK=1", "NO_PERL=1", "NO_PYTHON=1", "NO_INSTALL_HARDLINKS=1",
        "NO_RUST=1", "NO_PTHREADS=1",
        "CC=$ndkBin/$($cfg.Clang)", "AR=$ndkBin/llvm-ar.exe",
        "-j8"
    )

    # in-tree build: git's Makefile references gitweb/Makefile relatively, so an
    # out-of-tree build with -f fails.
    Invoke-Native { & $make -C $src clean } | Out-Null
    $build = Invoke-Native { & $make -C $src @makeArgs git }
    if ($build.ExitCode -ne 0) {
        $build.Output | Select-Object -Last 40 | Write-Host
        throw "git build failed for $abi"
    }

    Invoke-Native { & "$ndkBin/llvm-strip.exe" (Join-Path $src "git") } | Out-Null
    Copy-Item (Join-Path $src "git") $dest -Force
}

# --- rg ----------------------------------------------------------------------

function Build-Rg([string]$src, [string]$abi, $cfg, [string]$dest) {
    Write-Host "`n=== ripgrep $RipgrepVersion / $abi ==="

    # cargo reads .cargo/config.toml relative to the working directory, not the
    # manifest, so this has to be written next to the source and cargo has to be
    # invoked from there.
    $cargoDir = Join-Path $src ".cargo"
    New-Item -ItemType Directory -Force -Path $cargoDir | Out-Null
    $align = @(
        "rustflags = [""-C"", ""link-arg=-Wl,-z,max-page-size=16384"", ""-C"", ""link-arg=-Wl,-z,common-page-size=16384""]"
    )
    @"
[target.aarch64-linux-android]
linker = "$ndkBin/aarch64-linux-android$AndroidApi-clang.cmd"
$($align[0])

[target.x86_64-linux-android]
linker = "$ndkBin/x86_64-linux-android$AndroidApi-clang.cmd"
$($align[0])
"@ | Set-Content (Join-Path $cargoDir "config.toml") -Encoding ASCII

    $env:CARGO_HOME = Join-Path $WorkDir "cargo"
    $env:RUSTUP_HOME = Join-Path $WorkDir "rustup"
    $cargo = Join-Path $env:CARGO_HOME "bin/cargo.exe"
    if (-not (Test-Path $cargo)) { throw "cargo not found at $cargo. Install rustup into $WorkDir first." }

    Push-Location $src
    try {
        $build = Invoke-Native { & $cargo build --release --target $cfg.Rust }
        if ($build.ExitCode -ne 0) {
            $build.Output | Select-Object -Last 40 | Write-Host
            throw "rg build failed for $abi"
        }
    } finally {
        Pop-Location
    }

    $built = Join-Path $src "target/$($cfg.Rust)/release/rg"
    Invoke-Native { & "$ndkBin/llvm-strip.exe" $built } | Out-Null
    Copy-Item $built $dest -Force
}

# --- verification ------------------------------------------------------------

# A wrong-architecture or 4 KB-aligned binary installs and starts fine on this
# machine, then fails only on the user's device. Verify statically and hard-fail.
function Assert-Elf([string]$path, $cfg) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    if (-not ($bytes[0] -eq 0x7F -and $bytes[1] -eq 0x45 -and $bytes[2] -eq 0x4C -and $bytes[3] -eq 0x46)) {
        throw "not an ELF file: $path"
    }
    if ($bytes[4] -ne 2) { throw "not ELF64: $path" }

    $machine = [BitConverter]::ToUInt16($bytes, 18)
    if ($machine -ne $cfg.Machine) {
        throw "architecture mismatch: $path e_machine=0x$('{0:X}' -f $machine), expected 0x$('{0:X}' -f $cfg.Machine)"
    }

    $phoff = [BitConverter]::ToUInt64($bytes, 32)
    $phentsize = [BitConverter]::ToUInt16($bytes, 54)
    $phnum = [BitConverter]::ToUInt16($bytes, 56)
    for ($i = 0; $i -lt $phnum; $i++) {
        $o = [int]$phoff + $i * $phentsize
        if ([BitConverter]::ToUInt32($bytes, $o) -eq 1) {
            $align = [BitConverter]::ToUInt64($bytes, $o + 48)
            if ($align -lt 0x4000) {
                throw "$path has a PT_LOAD segment with p_align=0x$('{0:X}' -f $align); Android 15+ on a 16 KB kernel will reject or degrade it"
            }
        }
    }
}

# --- main --------------------------------------------------------------------

$sources = Get-Sources
$outRoot = Join-Path $RepoRoot ".cache/tools"

foreach ($abi in $targets) {
    $cfg = Get-AbiConfig $abi
    $dest = Join-Path $outRoot $abi
    New-Item -ItemType Directory -Force -Path $dest | Out-Null

    Build-Git $sources.Git $abi $cfg (Join-Path $dest "git")
    Build-Rg $sources.Rg $abi $cfg (Join-Path $dest "rg")

    foreach ($tool in @("git", "rg")) {
        $p = Join-Path $dest $tool
        Assert-Elf $p $cfg
        $mb = [math]::Round((Get-Item $p).Length / 1MB, 2)
        Write-Host ("  {0,-4} {1,-10} {2,6} MB  16 KB aligned, e_machine=0x{3:X}" -f $tool, $abi, $mb, $cfg.Machine)
    }
}

Write-Host "`ntools built into $outRoot"
Write-Host "next: ./script/android/stage-engine.ps1 -Abi <abi> -ToolsDir <abi-dir>"
