# analysisLunch
점심 식사를 분석하는 프로그램


로컬에서 실행하려면
gradle run

act로 실행하려면
act workflow_dispatch --secret-file .secrets

## AI 공급자 선택

GitHub Actions에서는 저장소 Variables의 `AI_PROVIDER`를 설정하고 해당 공급자의
API 키를 같은 이름의 Secret에 등록합니다. `AI_PROVIDER` Variable이 없으면 기존처럼
Gemini를 사용합니다. 나머지 채널 및 공통 환경 변수 설정은 그대로 유지합니다.
