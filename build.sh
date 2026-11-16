#!/bin/sh
# Requires JDK 26 on PATH
rm -rf out && mkdir out
javac --release 26 -d out src/hashcheck/*.java || exit 1
jar --create --file ChecksumChecker.jar --main-class hashcheck.Main -C out .
echo "Built ChecksumChecker.jar  (run: java -jar ChecksumChecker.jar [file] [hash])"
