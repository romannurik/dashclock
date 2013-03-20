#!/bin/sh
cd `dirname $0`
IS_BETA=`cat ../main/AndroidManifest.xml | grep -ie "versionName=.*beta"`
if [[ -z ${IS_BETA} ]]; then
  echo "versionName should include 'beta'." >&2
  exit
fi

cd ../main
ant clean release
cd bin
mv DashClock-release.apk DashClock-beta.apk

# See https://code.google.com/p/support/source/browse/trunk/scripts/googlecode_upload.py
googlecode_upload.py -s "Latest beta APK" -p dashclock -l Featured DashClock-beta.apk