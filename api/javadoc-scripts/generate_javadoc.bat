@echo off & setlocal
rem #
rem # Copyright 2013 Google Inc.
rem #
rem # Licensed under the Apache License, Version 2.0 (the "License");
rem # you may not use this file except in compliance with the License.
rem # You may obtain a copy of the License at
rem #
rem #     http://www.apache.org/licenses/LICENSE-2.0
rem #
rem # Unless required by applicable law or agreed to in writing, software
rem # distributed under the License is distributed on an "AS IS" BASIS,
rem # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem # See the License for the specific language governing permissions and
rem # limitations under the License.

cd %~dp0

FOR /F "usebackq" %%Z IN ( `Findstr sdk.dir ..\..\local.properties` ) DO SET ANDROID_SDK=%%Z
set ANDROID_SDK=%ANDROID_SDK:~8%
set PLATFORM=android-21
set OUT_PATH=..\build\javadoc

javadoc -linkoffline http://developer.android.com/reference %ANDROID_SDK%\docs\reference -sourcepath ..\src\main\java;..\build\source\aidl\debug -classpath %ANDROID_SDK%\platforms\%PLATFORM%\android.jar;%ANDROID_SDK%\tools\support\annotations.jar -d %OUT_PATH% -notree -nonavbar -noindex -notree -nohelp -nodeprecated -stylesheetfile javadoc_stylesheet.css -windowtitle "DashClock API" -doctitle "DashClock API" com.google.android.apps.dashclock.api

mkdir %OUT_PATH%\resources\
copy prettify* %OUT_PATH%\resources\

python tweak_javadoc_html.py %OUT_PATH%\
