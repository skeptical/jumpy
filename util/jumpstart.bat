@echo off

:: edit the 3 paths below if necessary
::    - you may want to add other interpreter paths if they're not installed system-wide, e.g.
::         set OTHERS=c:\Perl\bin;c:\ruby\bin
set PYTHON=c:\Python27
set OTHERS=
set JAVA_HOME="c:\Program Files (x86)\Java\jdk1.6.0_12"

if not "%path_set%"=="yes" set path=%PYTHON%;%OTHERS%;%PATH%
set path_set=yes

pushd .
cd "%~dp0"
set lib=%CD%\lib
popd

%JAVA_HOME%\bin\java.exe -cp "%lib%/jumpstart.jar;%lib%/py4j0.8.jar;%lib%/py4j0.7-97b47c.jar;%lib%/py4j0.7.jar;%lib%/commons-exec-1.1.jar;%lib%/winp-1.14.jar" jumpstart %*

