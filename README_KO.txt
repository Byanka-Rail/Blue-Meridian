BLUE MERIDIAN Android wrapper · v1.9.209

무엇이 들어 있나
- 원본 게임: BLUE_MERIDIAN_v1.9.209_HEAD_ON_1.html 기반
- Android WebView 앱
- 화면 회전 시 Activity 재생성 방지: 전투 중 회전으로 게임이 리셋되지 않음
- localStorage / DOM Storage 유지: 캠페인 진행과 설정이 앱 재실행 뒤에도 남음
- HTML <input type=file> 연결: 리플레이 JSON, 지형 JSON, 캠페인 파일 불러오기 가능
- Blob 다운로드 연결: JSON / 오디오 내보내기를 Downloads/BlueMeridian 에 저장
- Three.js 0.185.1 module + 0.160.1 classic fallback을 APK 내부에 포함하도록 빌드
- Cloudflare 분석 beacon 제거: 앱 실행에 불필요한 외부 요청 제거
- fullSensor 방향 지원, immersive fullscreen, 하드웨어 가속, 화면 꺼짐 방지

GitHub에서 APK 만드는 법
1. 이 폴더 안의 모든 파일을 새 GitHub 저장소의 루트에 업로드한다.
2. Actions 탭에서 "Build BLUE MERIDIAN APK"를 연다.
3. Run workflow 를 누른다. main 브랜치에 push해도 자동 실행된다.
4. 작업이 끝나면 Artifacts의 BLUE_MERIDIAN_v1.9.209_APK를 내려받는다.
5. 압축 안의 BLUE_MERIDIAN_v1.9.209.apk를 Android에 설치한다.

HTML을 다음 버전으로 교체할 때
- app/src/main/assets/index.html 을 새 HTML로 교체한다.
- 새 HTML의 Three.js 부트스트랩 URL을 아래처럼 로컬 상대경로로 바꾼다.
  https://cdn.jsdelivr.net/npm/three@0.185.1/build/three.module.min.js -> ./three.module.min.js
  https://cdn.jsdelivr.net/npm/three@0.160.1/build/three.min.js -> ./three.min.js
- Android Blob 내보내기 shim도 새 HTML 끝에 유지해야 한다.
- app/build.gradle 의 versionCode/versionName과 Workflow의 APK 파일명만 갱신한다.

참고
- 이 패키지는 debug-signing APK를 생성한다. 개인 설치/테스트에는 바로 쓸 수 있다.
- Play Store 배포용으로 쓰려면 별도의 release signing key 구성이 필요하다.
- 앱 삭제 시 Android 앱 데이터(localStorage)는 함께 지워질 수 있으므로 중요한 사용자 캠페인/지형/리플레이는 게임의 JSON 내보내기를 사용해 백업하는 편이 안전하다.
