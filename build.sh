#!/bin/bash
set -e
cd "$(dirname "$0")"

ROOT=$(pwd)/..
TOOLS=$ROOT/tools
JDK=$TOOLS/jdk-17.0.2
BT=$TOOLS/build-tools-34
PLATFORM=$TOOLS/platform-34/android.jar
export PATH=$JDK/bin:$BT:$PATH
export JAVA_HOME=$JDK

WORK=build
rm -rf $WORK
mkdir -p $WORK/compiled $WORK/gen $WORK/classes $WORK/dex

echo "[1/6] aapt2 compile"
aapt2 compile --dir res -o $WORK/res.zip

echo "[2/6] aapt2 link"
aapt2 link \
  -I $PLATFORM \
  --manifest AndroidManifest.xml \
  --java $WORK/gen \
  -A assets \
  -o $WORK/base.apk \
  $WORK/res.zip

echo "[3/6] javac"
find src $WORK/gen -name "*.java" > $WORK/sources.txt
javac --release 8 -classpath $PLATFORM \
  -d $WORK/classes @$WORK/sources.txt 2>&1 | tee $WORK/javac.log

echo "[4/6] d8"
find $WORK/classes -name "*.class" > $WORK/classes.txt
d8 --lib $PLATFORM --output $WORK/dex @$WORK/classes.txt 2>&1 | tee $WORK/d8.log

echo "[5/6] merge dex into apk"
cd $WORK/dex && zip -u ../base.apk classes.dex && cd ../..

echo "[6/6] zipalign + apksigner"
zipalign -f 4 $WORK/base.apk $WORK/aligned.apk

if [ ! -f $WORK/debug.keystore ]; then
  keytool -genkeypair -v -keystore $WORK/debug.keystore \
    -alias eost -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass eost123 -keypass eost123 \
    -dname "CN=EOST, OU=ET, O=LTOS, L=MZ, ST=GD, C=CN"
fi

apksigner sign \
  --ks $WORK/debug.keystore --ks-pass pass:eost123 \
  --ks-key-alias eost --key-pass pass:eost123 \
  --out $WORK/EOST-Packer.apk $WORK/aligned.apk

echo "== DONE =="
ls -lh $WORK/EOST-Packer.apk
aapt2 dump badging $WORK/EOST-Packer.apk | head -20
