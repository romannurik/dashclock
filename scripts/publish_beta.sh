#!/bin/sh
cd `dirname $0`
VERSION_NAME=`cat ../main/AndroidManifest.xml | grep -iEe "versionName=\"(.+)\"" | sed -E s@.*Name=\"\(.+\)\".*@\\\1@g`
if [[ "${VERSION_NAME}" =~ "beta" ]]; then
  echo "versionName '$VERSION_NAME' does not include 'beta'." >&2
  exit
fi

echo Building beta version $VERSION_NAME...
cd ../main
ant clean release
cd bin
mv DashClock-release.apk DashClock-beta.apk

# See https://code.google.com/p/support/source/browse/trunk/scripts/googlecode_upload.py
googlecode_upload.py -s "Latest beta APK (version $VERSION_NAME)" -p dashclock -l Featured DashClock-beta.apk