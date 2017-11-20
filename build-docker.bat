@echo off
for /F "usebackq tokens=1,2 delims==" %%i in (`wmic os get LocalDateTime /VALUE 2^>NUL`) do if '.%%i.'=='.LocalDateTime.' set ldt=%%j
set BUILD_DATE=%ldt:~0,4%-%ldt:~4,2%-%ldt:~6,2%T%ldt:~8,2%:%ldt:~10,2%:%ldt:~12,6%Z

for /F "delims=" %%a in ('git rev-parse --short HEAD') do @set VCS_REF=%%a

docker build -t gdragon/throw-voice:%VERSION% --build-arg VCS_REF=%VCS_REF% --build-arg BUILD_DATE=%BUILD_DATE% --build-arg VERSION=%VERSION% .
