@echo off

set GRABOO_DIR="%LOCALAPPDATA%\graboo"
set EXE="%GRABOO_DIR%\graboo-windows-x64.exe"

if exist %EXE% goto runGraboo

if not exist "%GRABOO_DIR%" mkdir %GRABOO_DIR%

powershell -Command "&{"^
		"$webclient = new-object System.Net.WebClient;"^
		"[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webclient.DownloadFile('https://github.com/jamesward/graboo/releases/latest/download/graboo-windows-x64.exe', '%EXE%')"^
		"}"

:runGraboo
%EXE% %*
