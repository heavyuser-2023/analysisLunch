import os
import json
import asyncio
import google.generativeai as genai
import requests
from collections import Counter

# 환경 변수 로드
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
GITHUB_REPO = os.getenv("GITHUB_REPO")
PR_NUMBER = os.getenv("PR_NUMBER")

# Gemini 설정
genai.configure(api_key=GEMINI_API_KEY)
# 2026년 기준 최신 Pro 모델 사용 (환경에 맞춰 모델명 수정 가능)
MODEL_NAME = "gemini-3-pro-preview" 

async def get_gemini_review(instance_id, diff, temperature):
    """각기 다른 온도로 Gemini 모델 호출"""
    model = genai.GenerativeModel(MODEL_NAME)
    
    prompt = f"""
    당신은 10년 차 시니어 소프트웨어 엔지니어입니다. 아래의 코드 변경사항(diff)을 엄격하게 리뷰해 주세요.
    
    [리뷰 지침]
    1. 보안 취약점, 로직 오류, 성능 저하 요인을 우선적으로 찾으세요.
    2. 결과는 반드시 아래 JSON 형식으로만 응답하세요. (마크다운 코드 블록 제외, 순수 JSON만)
    {{
      "reviews": [
        {{"line": line_number, "issue": "이슈 설명", "severity": "CRITICAL|MAJOR|MINOR"}}
      ]
    }}
    
    [Diff]
    {diff}
    """
    
    try:
        # 온도 설정을 다르게 하여 각 인스턴스의 의견 다양성 확보
        response = await model.generate_content_async(
            prompt, 
            generation_config={"temperature": temperature, "response_mime_type": "application/json"}
        )
        return json.loads(response.text).get("reviews", [])
    except Exception as e:
        print(f"Instance {instance_id} failed: {e}")
        return []

def get_pr_diff():
    """GitHub API를 통해 PR의 Diff 정보를 가져옴"""
    url = f"https://api.github.com/repos/{GITHUB_REPO}/pulls/{PR_NUMBER}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}", "Accept": "application/vnd.github.v3.diff"}
    response = requests.get(url, headers=headers)
    return response.text

def post_github_comment(comment):
    """PR에 최종 결과 댓글 작성"""
    url = f"https://api.github.com/repos/{GITHUB_REPO}/issues/{PR_NUMBER}/comments"
    headers = {"Authorization": f"token {GITHUB_TOKEN}", "Content-Type": "application/json"}
    requests.post(url, headers=headers, json={"body": comment})

async def main():
    diff = get_pr_diff()
    if not diff:
        return

    # 1. 3개의 인스턴스를 병렬로 실행 (온도를 0.2, 0.7, 1.0으로 다르게 설정)
    tasks = [
        get_gemini_review(1, diff, 0.2),
        get_gemini_review(2, diff, 0.7),
        get_gemini_review(3, diff, 1.0)
    ]
    
    all_results = await asyncio.gather(*tasks)
    
    # 2. 다수결 로직 (Consensus)
    flat_reviews = [item for sublist in all_results for item in sublist]
    line_counts = Counter([r['line'] for r in flat_reviews])
    
    consensus_issues = []
    for line, count in line_counts.items():
        # 3개 중 2개 이상의 인스턴스가 지적한 경우만 채택
        if count >= 2:
            relevant_reviews = [r for r in flat_reviews if r['line'] == line]
            consensus_issues.append({
                "line": line,
                "count": count,
                "issue": relevant_reviews[0]['issue'],
                "severity": relevant_reviews[0]['severity']
            })

    # 3. 결과 리포트 생성
    if consensus_issues:
        report = "## 🤖 Gemini 다수결 코드 리뷰 결과\n"
        report += "> 3개의 Gemini 인스턴스가 교차 검증을 수행했습니다. (2표 이상 득표 항목만 표시)\n\n"
        
        for issue in sorted(consensus_issues, key=lambda x: x['line']):
            severity_emoji = "🚨" if issue['severity'] == "CRITICAL" else "⚠️"
            report += f"- {severity_emoji} **Line {issue['line']}**: {issue['issue']} ({issue['count']}/3 동의)\n"
        
        post_github_comment(report)
    else:
        post_github_comment("## ✅ Gemini 리뷰 완료\n특별한 로직 오류가 발견되지 않았습니다.")

if __name__ == "__main__":
    asyncio.run(main())