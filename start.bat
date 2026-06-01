@echo off
set JAVA_HOME=D:\Java\java17
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "D:\Program Files (x86)\work\aiProject\backend"
mvn spring-boot:run
