@echo off

set GRABOO_DIR=%LOCALAPPDATA%\graboo
set EXE=%GRABOO_DIR%\graboo-windows-x64.exe
set EXE_NEW=%EXE%-new

if exist "%EXE_NEW%" (
    ren "%EXE_NEW%" "%EXE%"
)

if exist %EXE% goto runGraboo

if not exist "%GRABOO_DIR%" mkdir %GRABOO_DIR%

powershell -Command "&{"^
		"$webclient = new-object System.Net.WebClient;"^
		"[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webclient.DownloadFile('https://github.com/jamesward/graboo/releases/latest/download/graboo-windows-x64.exe', '%EXE%')"^
		"}"

:runGraboo

set "dclickcmdx=%comspec% /c xx%~0x x"
set "actualcmdx=%cmdcmdline:"=x%"

set CMDS=%*
if /I "%dclickcmdx%" EQU "%actualcmdx%" (
    set CMDS=ide
)

%EXE% %CMDS%
