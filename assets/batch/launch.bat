:: Launcher for Winfoom - Basic Proxy Facade

echo off

setlocal EnableDelayedExpansion

set ARGS=-server -Dnashorn.args=--no-deprecation-warning

FOR %%a IN (%*) DO (

    IF NOT "%%a"=="--debug" IF NOT "%%a"=="--systemjre" (
		echo Unknow parameter: %%a
		exit 1;
	)

	IF "%%a"=="--debug" (
		SET ARGS=%ARGS% -Dlogging.level.root=DEBUG
	)

	IF "%%a"=="--systemjre" (
		SET JAVA_EXE=javaw
	)

)

IF NOT DEFINED JAVA_EXE set JAVA_EXE=jdk/bin/javaw

echo JAVA_EXE=%JAVA_EXE%
echo ARGS=%ARGS%

start %JAVA_EXE% %ARGS% -cp . -jar winfoom.jar