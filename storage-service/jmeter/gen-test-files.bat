@echo off
rem =========================================================
rem  gen-test-files.bat
rem  Creates a "test-files" sub-folder in the same directory
rem  and generates seven verifiable test files:
rem  1 KB, 10 KB, 100 KB, 1 MB, 10 MB, 100 MB, 1 GB
rem =========================================================
setlocal enabledelayedexpansion

:: Output folder
set "OUT_DIR=%~dp0test-files"
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

:: File sizes in bytes
set SIZES=1024 10240 102400 1048576 10485760 104857600 1073741824

echo [INFO] Generating test files to %OUT_DIR%
for %%S in (%SIZES%) do (
    call :genFile %%S
)
echo [INFO] All done!
pause
goto :eof

::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
:genFile  sizeInBytes
::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
set "size=%~1"
set "file=%OUT_DIR%\%size%.dat"

:: Skip if already exists and size is correct
if exist "%file%" (
    for %%F in ("%file%") do (
        if "%%~zF"=="%size%" (
            echo [SKIP] %file% already exists and size is correct
            goto :eof
        )
    )
)

echo [GEN ] %file% ...

:: 1. Convert decimal size to hexadecimal
set "h=%size%"
set "hex="
:dec2hex
set /a mod=h%%16, h/=16
set "modMap=0123456789ABCDEF"
set "hex=!modMap:~%mod%,1!!hex!"
if %h% gtr 0 goto dec2hex

:: 2. Build repeated hex string
set /a loops=%size%/2
set /a loops+=1
set "hexLine="
for /l %%i in (1,1,100) do set "hexLine=!hexLine!!hex!"
set "hexLine=!hexLine:~0,%loops%!"

:: 3. Create temp hex file and convert to binary
set "tmpHex=%OUT_DIR%\%size%.hex"
echo %hexLine% > "%tmpHex%"
certutil -decodehex -f "%tmpHex%" "%file%" >nul 2>&1
del "%tmpHex%" 2>nul

:: 4. Truncate to exact size
fsutil file seteof "%file%" %size% >nul 2>&1

:: 5. Verify
for %%F in ("%file%") do (
    if not "%%~zF"=="%size%" (
        echo [FAIL] %file% size mismatch!
        goto :eof
    )
)
echo [ OK ] %file% generated successfully
goto :eof