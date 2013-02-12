#!/bin/sh
source _locals.sh
javadoc -linkoffline http://developer.android.com/reference ${ANDROID_SDK}/docs/reference \
        -sourcepath ../src \
        -classpath ${ANDROID_SDK}/platforms/android-17/android.jar:${ANDROID_SDK}/tools/support/annotations.jar \
        -d ../../out/javadoc/ \
        -notree -nonavbar -noindex -notree -nohelp -nodeprecated \
        -stylesheetfile javadoc_stylesheet.css \
        -windowtitle "DashClock API" \
        -doctitle "DashClock API" \
        \
        com.google.android.apps.dashclock.api

cp prettify* ../../out/javadoc/resources/

python tweak_javadoc_html.py ../../out/javadoc/