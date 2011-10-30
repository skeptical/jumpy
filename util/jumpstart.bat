@echo off

:: edit the 2 paths below if necessary
set PYTHON=c:\Python27
set JAVA_HOME="c:\Program Files (x86)\Java\jdk1.6.0_12"

set lib=%~dp0\lib
set PATH=%PYTHON%;%PATH%

%JAVA_HOME%\bin\java.exe -cp "%lib%/jumpstart.jar;%lib%/py4j0.7.jar" jumpstart %*

