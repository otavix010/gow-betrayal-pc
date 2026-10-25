# Monta o pacote Windows x64: dist\gowb-port + Java enxuto (jlink) + GoW Betrayal.exe -> gowb-port-windows-x64.zip
# Requer: JDK 21 (jlink no PATH), Git Bash (para o build.sh) e o Visual Studio 2022 ou Build Tools com C++ e Clang.
# Uso: powershell -ExecutionPolicy Bypass -File pacote\montar-windows.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# o app (camada MIDP + host + tradução)
& bash build.sh
if ($LASTEXITCODE -ne 0) { throw 'build.sh falhou' }

$out = Join-Path $root 'dist\windows'
$p = Join-Path $out 'gowb-port'
Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
Copy-Item -Recurse 'dist\gowb-port' $p
Remove-Item "$p\jogar.sh", "$p\lib\*natives-linux*.jar" -Force
Copy-Item 'pacote\LEIA-ME-windows.txt' "$p\LEIA-ME.txt" -Force
Copy-Item 'LICENSE' "$p\LICENSE.txt" -Force

# Java enxuto, só os módulos que o app usa
$jlink = (Get-Command jlink).Source
& $jlink --add-modules java.base,java.desktop,jdk.unsupported --strip-debug --no-header-files --no-man-pages `
    --compress=zip-6 --output "$p\jre"
if ($LASTEXITCODE -ne 0) { throw 'jlink falhou' }

# GoW Betrayal.exe: abre o javaw do pacote e espera o jogo
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
$vs = & $vswhere -latest -products * -property installationPath
$clang = Join-Path $vs 'VC\Tools\Llvm\x64\bin\clang.exe'
cmd /c "`"$vs\Common7\Tools\VsDevCmd.bat`" -arch=amd64 -host_arch=amd64 >nul && `"$clang`" -O2 pacote\abrir.c -o `"$p\GoW Betrayal.exe`" -luser32 -Wl,/subsystem:windows -Wl,/entry:wWinMainCRTStartup"
if ($LASTEXITCODE -ne 0) { throw 'clang falhou' }

$zip = Join-Path $root 'gowb-port-windows-x64.zip'
Remove-Item $zip -ErrorAction SilentlyContinue
& "$env:SystemRoot\System32\tar.exe" -a -c -f $zip -C $out gowb-port
if ($LASTEXITCODE -ne 0) { throw 'zip falhou' }
'pronto: ' + $zip + ' (' + [math]::Round((Get-Item $zip).Length / 1MB, 1) + ' MB)'
