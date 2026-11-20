# analysisLunch
점심 식사를 분석하는 프로그램


로컬에서 실행하려면
gradle run

act로 실행하려면
act workflow_dispatch --secret-file .secrets

## AI 공급자 선택

`AI_PROVIDER` 환경 변수로 `gemini` 또는 `openai`를 선택합니다. 미설정 시 기본값은
`gemini`이며, 선택한 공급자의 키만 필수입니다: Gemini는 `GEMINI_API_KEY`, OpenAI는
`OPENAI_API_KEY`를 설정합니다. 선택하지 않은 공급자의 키는 없어도 됩니다.

GitHub Actions에서는 저장소 Variables의 `AI_PROVIDER`를 설정하고 해당 공급자의
API 키를 같은 이름의 Secret에 등록합니다. `AI_PROVIDER` Variable이 없으면 기존처럼
Gemini를 사용합니다. 나머지 채널 및 공통 환경 변수 설정은 그대로 유지합니다.
