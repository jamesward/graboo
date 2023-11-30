@echo off

if exist "%USERPROFILE%\graboo-windows-x64.exe" goto runGraboo

powershell -Command "&{"^
		"$webclient = new-object System.Net.WebClient;"^
		"[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webclient.DownloadFile('https://github.com/jamesward/graboo/releases/latest/download/graboo-windows-x64.exe', '%USERPROFILE%\graboo-windows-x64.exe')"^
		"}"

:runGraboo
%USERPROFILE%\graboo-windows-x64.exe %*
