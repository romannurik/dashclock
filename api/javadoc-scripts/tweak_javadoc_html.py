#!/usr/bin/env python
# Copyright 2013 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import re
import os
import sys
import shutil


PACKAGE_NAME = 'com.google.android.apps.dashclock.api'


def main():
  root = sys.argv[1]
  for path, _, files in os.walk(root):
    for f in [f for f in files if f.endswith('.html')]:
      fp = open(os.path.join(path, f), 'r')
      html = fp.read()
      fp.close()

      toroot = '.'
      if path.startswith(root):
        subpath = path[len(root):]
        toroot = '../' * (subpath.count('/') + 1)

      html = process(toroot, html)
      if f.endswith('package-summary.html'):
        html = process_package_summary(toroot, html)

      fp = open(os.path.join(path, f), 'w')
      fp.write(html)
      fp.close()

  shutil.copy('index.html', os.path.join(path, 'index.html'))


def process(toroot, html):
  re_flags = re.I | re.M | re.S
  #html = re.sub(r'<HR>\s+<HR>', '', html, 0, re_flags)
  html = re.sub(r'<body>', '<body onload="prettyPrint();">', html, 0, re_flags)
  html = re.sub(r'\s+</pre>', '</pre>', html, 0, re_flags)
  html = re.sub(r'<div class="header">', '''<div class="header">
<a class="home-link" href="%(root)sindex.html">API Home</a>
''' % dict(root=toroot), html, 0, re_flags)
  html = re.sub(PACKAGE_NAME + '</font>', '<a href="package-summary.html" style="border:0">' + PACKAGE_NAME + '</a></font>', html, 0, re_flags)
  html = re.sub(r'<head>', '''<head>
<meta name=viewport content="width=device-width, initial-scale=1">
<link rel="stylesheet" type="text/css" href="http://fonts.googleapis.com/css?family=Roboto:400,500,700|Inconsolata:400,700">
<link rel="stylesheet" type="text/css" href="%(root)sresources/prettify.css">
<script src="%(root)sresources/prettify.js"></script>
''' % dict(root=toroot), html, 0, re_flags)
  #html = re.sub(r'<HR>\s+<HR>', '', html, re.I | re.M | re.S)
  return html


def process_package_summary(toroot, html):
  re_flags = re.I | re.M | re.S
  # html = re.sub(r'</H2>\s+.*?\n', '</H2>\n', html, 0, re_flags)
  # html = re.sub(r'<B>See:</B>\n<br>', '\n', html, 0, re_flags)
  # html = re.sub(r'&nbsp;&nbsp;(&nbsp;)+[^\n]+\n', '\n', html, 0, re_flags)
  # html = re.sub(r'\n[^\n]+\s+description\n', '\nDescription\n', html, 0, re_flags)
  return html


if __name__ == '__main__':
  main()
