#!/bin/bash
set -e
set -o pipefail
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
javac --release 8 -classpath $PLATFORM:libs/* \
  -d $WORK/classes @$WORK/sources.txt 2>&1 | tee $WORK/javac.log

echo "[4/6] d8"
find $WORK/classes -name "*.class" > $WORK/classes.txt
d8 --lib $PLATFORM --min-api 21 --output $WORK/dex @$WORK/classes.txt \
  libs/commons-compress.jar libs/xz.jar 2>&1 | tee $WORK/d8.log

echo "[5/6] merge dex + jniLibs into apk"
cd $WORK/dex && zip -u ../base.apk classes.dex && cd ../..
# 打包 jniLibs 到 lib/<abi>/（Android 安装时自动解压到 nativeLibraryDir，天然可执行）
if [ -d jniLibs ]; then
  rm -rf build/lib && mkdir -p build/lib
  for abi in arm64-v8a armeabi-v7a; do
    if [ -d "jniLibs/$abi" ]; then
      cp -r "jniLibs/$abi" "build/lib/$abi"
    fi
  done
  # .so 用 stored 方式（-0 不压缩），兼容 extractNativeLibs=false 的设备
  (cd build && zip -0 -ru ../$WORK/base.apk lib/)
fi

echo "[6/6] zipalign + apksigner (ETC, v1+v2)"
zipalign -f -p 4 $WORK/base.apk $WORK/aligned.apk

apksigner sign \
  --ks assets/etckey.jks --ks-pass pass:etc123 \
  --ks-key-alias etc --key-pass pass:etc123 \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out $WORK/EOST-Packer.apk $WORK/aligned.apk

echo "== DONE =="
ls -lh $WORK/EOST-Packer.apk
apksigner verify --verbose $WORK/EOST-Packer.apk | head -8
aapt2 dump badging $WORK/EOST-Packer.apk | head -20
