BLUE MERIDIAN Android wrapper · v1.9.227

무엇이 들어 있나
- 원본 게임: BLUE_MERIDIAN_v1.9.227_VIEWER_PLATE.html 기반 (실지형 6종 포함)
- Android WebView 앱
- 화면 회전 시 Activity 재생성 방지: 전투 중 회전으로 게임이 리셋되지 않음
- localStorage / DOM Storage 유지: 캠페인 진행과 설정이 앱 재실행 뒤에도 남음
- HTML <input type=file> 연결: 리플레이 JSON, 지형 JSON, 캠페인 파일 불러오기 가능
- Blob 다운로드 연결: JSON / 오디오 내보내기를 Downloads/BlueMeridian 에 저장
- Three.js 0.185.1 module + 0.160.1 classic fallback을 APK 내부에 포함하도록 빌드
- fullSensor 방향 지원, immersive fullscreen, 하드웨어 가속, 화면 꺼짐 방지

GitHub에서 APK 만드는 법
1. 이 폴더 안의 모든 파일을 저장소 루트에 올린다 (기존 파일은 덮어쓴다).
2. Actions 탭에서 "Build BLUE MERIDIAN APK"를 연다.
3. Run workflow 를 누른다. main 브랜치에 push해도 자동 실행된다.
4. 작업이 끝나면 Artifacts의 BLUE_MERIDIAN_v1.9.227_APK를 내려받는다.
5. 압축 안의 BLUE_MERIDIAN_v1.9.227.apk를 Android에 설치한다.

업데이트 설치에 관하여
- 앱 안에는 자동 업데이트 기능이 없다. 게임 HTML이 APK 안에 구워져 있으므로
  새 버전은 새 APK를 만들어 설치하는 방식으로만 올라간다.
- 패키지명(com.bluemeridian.game)과 서명 키가 같고 versionCode 가 더 크면
  기존 앱 위에 덮어쓰기 설치가 되고 localStorage(캠페인 진행·설정)는 유지된다.
  지우고 새로 깔면 진행이 날아가므로 반드시 덮어쓰기로 설치할 것.
- versionCode 규칙: 1.9.227 -> 109227 (major*100000 + minor*1000 + patch)

HTML을 다음 버전으로 교체할 때
- app/src/main/assets/index.html 을 새 HTML로 교체한다.
- 새 HTML의 Three.js 부트스트랩 URL을 아래처럼 로컬 상대경로로 바꾼다.
  https://cdn.jsdelivr.net/npm/three@0.185.1/build/three.module.min.js -> ./three.module.min.js
  https://cdn.jsdelivr.net/npm/three@0.160.1/build/three.min.js        -> ./three.min.js
- HTML 맨 끝의 <script id="bmAndroidBridgeShim"> 블록을 반드시 유지한다.
  (Blob 내보내기를 안드로이드 저장소로 넘겨 주는 다리. 빠지면 내보내기가 조용히 실패한다.)
- app/build.gradle 의 versionCode/versionName 과 워크플로의 APK 파일명을 갱신한다.

v1.9.227에서 고친 빌드 문제
- three.module.min.js 는 r167 이후로 자체 완결이 아니다 — 내부에서
  ./three.core.min.js 를 import 한다. 종전 prepare_assets.py 는 core 를 받지 않아
  ES 모듈 경로가 항상 실패했고, 앱은 조용히 0.160.1 classic 폴백으로 돌고 있었다.
  (동작은 했으나 의도한 0.185.1 이 아니었다.)
- prepare_assets.py 가 three.core.min.js 도 내려받도록 고쳤고, 누락 시 빌드가
  실패하도록 검사를 넣었다. 오프라인 실측 결과 __VF_THREE_SOURCE__ = "R185 DIRECT".

참고
- 이 패키지는 debug-signing APK를 생성한다. 개인 설치/테스트에는 바로 쓸 수 있다.
- Play Store 배포용으로 쓰려면 별도의 release signing key 구성이 필요하다.
- 앱 삭제 시 localStorage 는 함께 지워진다. 중요한 캠페인/지형/리플레이는
  게임의 JSON 내보내기로 백업해 두는 편이 안전하다.
