#!/usr/bin/env bash
# 디버그 APK 를 빌드해 Firebase App Distribution 의 테스터 그룹으로 바로 배포한다.
#
#   ./scripts/distribute.sh "출시 노트"        # 출시 노트를 직접 적기
#   ./scripts/distribute.sh                    # 마지막 커밋 제목을 출시 노트로 쓰기
#
# 처음 한 번: npx -y firebase-tools@15.36.0 login  (브라우저에서 직접 로그인)
# 필요한 값(local.properties, 커밋되지 않음):
#   routealarm.firebaseAppId=1:...:android:...   (Firebase 콘솔 > 프로젝트 설정 > 내 앱 > 앱 ID)
#   routealarm.firebaseGroups=팀원               (선택, 기본값 팀원. 쉼표로 여러 그룹)
#
# 주의: 디버그 APK 는 빌드한 컴퓨터의 키로 서명된다. 테스터가 업데이트를 받으려면 항상 같은 컴퓨터에서 빌드해야 한다.
set -euo pipefail

FIREBASE_TOOLS="firebase-tools@15.36.0"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

prop() { grep -E "^$1=" local.properties 2>/dev/null | head -1 | cut -d= -f2- || true; }

APP_ID="$(prop routealarm.firebaseAppId)"
TESTER_GROUPS="$(prop routealarm.firebaseGroups)"
TESTER_GROUPS="${TESTER_GROUPS:-팀원}"
if [[ -z "$APP_ID" ]]; then
  echo "local.properties 에 routealarm.firebaseAppId 가 없어요. Firebase 콘솔 > 프로젝트 설정 > 내 앱 > 앱 ID 를 넣어 주세요." >&2
  exit 1
fi
if grep -qE '^routealarm.proxyBaseUrl=' local.properties; then
  echo "local.properties 에 routealarm.proxyBaseUrl 이 켜져 있어요. 로컬 서버를 가리키는 APK 가 배포되지 않게 지운 뒤 다시 실행해 주세요." >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "커밋하지 않은 변경이 있어요. 버전 이름(커밋 해시)과 실제 코드가 달라지지 않게 커밋한 뒤 배포해 주세요." >&2
  exit 1
fi

NOTES="${1:-$(git log -1 --pretty=%s)}"

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

echo "▶ 디버그 APK 빌드"
./gradlew :app:assembleDebug --console=plain -q
APK="app/build/outputs/apk/debug/app-debug.apk"

echo "▶ Firebase App Distribution 업로드 (그룹: $TESTER_GROUPS)"
npx -y "$FIREBASE_TOOLS" appdistribution:distribute "$APK" \
  --app "$APP_ID" \
  --groups "$TESTER_GROUPS" \
  --release-notes "$NOTES"

echo "✔ 배포 완료: $(git log -1 --pretty='%h') · 출시 노트: $NOTES"
