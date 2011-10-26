@echo off

set JAVA_HOME="c:\Program Files (x86)\Java\jdk1.6.0_12"
set jardir=%~dp0
set PATH=F:\Programs\Python27;%PATH%
::echo %jardir%
::echo %JAVA_HOME%

%JAVA_HOME%\bin\java.exe -cp "%jardir%/jumpstart.jar;%jardir%/../lib/py4j0.7.jar" jumpstart %*

