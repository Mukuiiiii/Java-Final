@echo off
echo Starting TronClass Java Auto Rollcall in Windows...
call .\apache-maven-3.9.6\bin\mvn.cmd clean compile exec:java -Dexec.mainClass=com.finalproject.TronUI
pause
