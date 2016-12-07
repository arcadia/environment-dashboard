
@ECHO OFF

:: subtract one frmo each backup name
SET pathtemplate=C:\git\environment-dashboard\backup
FOR %%I IN ("%pathtemplate%??.zip") DO (
  SET "oldname=%%~nI"
  SETLOCAL EnableDelayedExpansion
  SET /A "newsuffix=1!oldname:~-2!-1"
  RENAME "%%I" "!oldname:~0,-2!!newsuffix:~1!.zip"
  ENDLOCAL
)

:: delete the olders
del /Q "%pathtemplate%00.zip"

:: create new backup (#5)
java -classpath "c:\Program Files (x86)\Jenkins\plugins\environment-dashboard\WEB-INF\lib\h2-1.4.188.jar" org.h2.tools.Shell -url "jdbc:h2:c:\Program Files (x86)\Jenkins\jenkins_dashboard;AUTO_SERVER=TRUE" -sql "BACKUP TO 'c:\git\environment-dashboard\backup05.zip';"
