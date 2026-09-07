#!/usr/bin/env bash
set -euo pipefail

# 離線備援驗證。使用現有 SimpleUI JAR 內嵌的 GDX，不代表正式 Gradle / GDX 1.13.1 建置。
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
validation_root="${project_root}/build/offline-validation"
cd "${project_root}"
mkdir -p "${validation_root}/classes"
java -version
if [[ ! -f libs/simpleui-1.1.2.jar ]]; then
    echo 'Missing local libs/simpleui-1.1.2.jar; restore local assets first.' >&2
    exit 1
fi
find core/src/main/java -name '*.java' | sort > "${validation_root}/main-sources.txt"
find core/src/test/java -name '*.java' | sort > "${validation_root}/test-sources.txt"
javac --release 17 -encoding UTF-8 -cp libs/simpleui-1.1.2.jar \
    -d "${validation_root}/classes" "@${validation_root}/main-sources.txt"
javac --release 17 -encoding UTF-8 -cp "${validation_root}/classes:libs/simpleui-1.1.2.jar" \
    -d "${validation_root}/classes" "@${validation_root}/test-sources.txt"
for test_name in VerticalSliceSmokeTest NationalCampaignSmokeTest NationalActionPointSmokeTest \
    UiResourceSmokeTest CampaignGrowthSmokeTest MusicPlaybackSmokeTest; do
    java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
        -cp "${validation_root}/classes:libs/simpleui-1.1.2.jar:core/src/test/resources" \
        "idv.kuan.studio.sango.validation.${test_name}" assets
 done
