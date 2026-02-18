import os
import json
import asyncio
import hashlib
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

async def get_gemini_review(instance_id: int, diff: str, temperature: float) -> list[dict]:
    """각기 다른 온도로 Gemini 모델을 호출하여 코드 리뷰를 수행한다.

    Args:
        instance_id (int): 병렬 실행 인스턴스 식별자.
        diff (str): GitHub PR의 unified diff 텍스트.
        temperature (float): Gemini 생성 온도 (다양성 조절).

    Returns:
        list[dict]: 리뷰 항목 리스트. 실패 시 빈 리스트.
    """
    model = genai.GenerativeModel(MODEL_NAME)

    prompt = f"""
    당신은 10년 차 시니어 소프트웨어 엔지니어이자 Python 코드 품질 전문가입니다.
    아래의 코드 변경사항(diff)을 **보안/로직/성능** 관점과 **Python 코드 컨벤션** 관점 모두에서 엄격하게 리뷰해 주세요.

    ===== [필수 리뷰 기준 1] 보안 / 로직 / 성능 =====
    - 보안 취약점 (인증 누락, 민감 정보 노출, 인젝션 등)
    - 로직 오류 (잘못된 조건, 엣지 케이스 미처리 등)
    - 성능 저하 요인 (불필요한 반복, 비효율적 자료구조 등)
    - HTTP 응답 에러 처리 누락 (raise_for_status, timeout 미설정 등)
    - 환경 변수 유효성 검증 누락

    ===== [필수 리뷰 기준 2] Python 코드 컨벤션 (PEP 8 + Google Style) =====
    아래 항목들을 **하나도 빠짐없이** 검사하세요. 위반 시 반드시 이슈로 보고해야 합니다.

    [C1] 명칭 규칙 (Naming Conventions)
    - 함수/변수: snake_case 사용 여부 (예: fetchUserId ❌ → fetch_user_id ✅)
    - 클래스: PascalCase 사용 여부 (예: userAccount ❌ → UserAccount ✅)
    - 전역 상수: UPPER_SNAKE_CASE 사용 여부 (예: modelName ❌ → MODEL_NAME ✅)
    - 매직 넘버: 의미 없는 리터럴 숫자/문자열을 상수로 분리했는지 여부

    [C2] 임포트 순서 (Import Order)
    - 표준 라이브러리 → 서드파티 → 로컬 순서 준수 여부
    - 각 그룹 사이에 빈 줄이 있는지 여부

    [C3] 타입 힌팅 (Type Hinting)
    - 모든 함수의 파라미터와 반환값에 타입 힌트가 명시되어 있는지 여부
    - 예: def func(x, y): ❌ → def func(x: int, y: str) -> bool: ✅

    [C4] 독스트링 (Google Style Docstring)
    - 모든 공개 함수/클래스에 독스트링이 있는지 여부
    - Args, Returns, Raises 섹션이 올바르게 작성되었는지 여부

    [C5] 예외 처리 (Exception Handling)
    - except Exception: 과 같은 포괄적 예외 처리 사용 여부 (구체적 예외 타입 사용 필수)
    - 예외 발생 시 단순 pass 또는 print만 하고 있는지 여부

    [C6] 로깅 (Logging)
    - print() 대신 logging 모듈을 사용하는지 여부
    - logger = logging.getLogger(__name__) 패턴 사용 여부

    [C7] 문자열 포맷팅
    - % 포맷팅 또는 .format() 대신 f-string을 사용하는지 여부

    [C8] 조건 비교
    - len(x) == 0 대신 if not x: 와 같은 Pythonic 비교를 사용하는지 여부
    - None 비교 시 == 대신 is None / is not None 사용 여부

    [C9] 빈 줄 규칙
    - 최상위 함수/클래스 사이에 두 줄 빈 줄이 있는지 여부
    - 클래스 내 메서드 사이에 한 줄 빈 줄이 있는지 여부

    ===== [응답 형식] =====
    결과는 반드시 아래 JSON 형식으로만 응답하세요. (마크다운 코드 블록 제외, 순수 JSON만)
    {{
      "reviews": [
        {{
          "file": "path/from/diff",
          "line": line_number,
          "side": "RIGHT|LEFT",
          "category": "SECURITY|LOGIC|PERFORMANCE|CONVENTION",
          "convention_rule": "C1|C2|C3|C4|C5|C6|C7|C8|C9|N/A",
          "issue": "이슈 설명 (위반된 규칙과 올바른 예시를 함께 명시)",
          "severity": "CRITICAL|MAJOR|MINOR"
        }}
      ]
    }}

    [응답 규칙]
    - file: diff의 파일 경로(예: "src/foo/Bar.java"). 파일 헤더(+++ b/... 또는 --- a/...)를 참고하세요.
    - side: RIGHT(추가/수정된 라인), LEFT(삭제된 라인) 중 하나.
    - line: side 기준의 라인 번호. (RIGHT는 신규 라인, LEFT는 기존 라인)
    - category: 보안/로직/성능 이슈는 SECURITY/LOGIC/PERFORMANCE, 컨벤션 위반은 CONVENTION.
    - convention_rule: 컨벤션 위반인 경우 해당 규칙 번호(C1~C9), 아니면 N/A.
    - severity 기준:
        * CRITICAL: 보안 취약점, 데이터 손실 가능성, 런타임 오류 유발
        * MAJOR: 로직 오류, 타입 힌트 전면 누락, 예외 처리 부재, 로깅 미사용
        * MINOR: 네이밍, 독스트링, 임포트 순서, 빈 줄, 문자열 포맷팅 등 스타일 위반

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

def build_diff_link_map(diff_text):
    """PR diff 텍스트에서 파일별 diff hash 매핑 생성 (rename/삭제/추가 대응)"""
    file_to_hash = {}
    for line in diff_text.splitlines():
        if line.startswith("diff --git "):
            parts = line.split(" ")
            if len(parts) >= 4:
                a_path = parts[2]
                b_path = parts[3]
                if a_path.startswith("a/") and b_path.startswith("b/"):
                    key = f"{a_path} {b_path}"
                    diff_hash = hashlib.sha1(key.encode("utf-8")).hexdigest()
                    a_file = a_path[2:]
                    b_file = b_path[2:]
                    if a_file != "dev/null":
                        file_to_hash[a_file] = diff_hash
                    if b_file != "dev/null":
                        file_to_hash[b_file] = diff_hash
    return file_to_hash

def get_pr_info():
    """PR 메타데이터(HEAD SHA 등) 조회"""
    url = f"https://api.github.com/repos/{GITHUB_REPO}/pulls/{PR_NUMBER}"
    headers = {"Authorization": f"token {GITHUB_TOKEN}", "Accept": "application/vnd.github.v3+json"}
    response = requests.get(url, headers=headers)
    if response.status_code != 200:
        return {}
    return response.json()

def post_github_comment(comment):
    """PR에 최종 결과 댓글 작성"""
    url = f"https://api.github.com/repos/{GITHUB_REPO}/issues/{PR_NUMBER}/comments"
    headers = {"Authorization": f"token {GITHUB_TOKEN}", "Content-Type": "application/json"}
    requests.post(url, headers=headers, json={"body": comment})

async def main():
    pr_info = get_pr_info()
    head_sha = pr_info.get("head", {}).get("sha")
    diff = get_pr_diff()
    if not diff:
        return
    diff_link_map = build_diff_link_map(diff)

    # 1. 3개의 인스턴스를 병렬로 실행 (온도를 0.2, 0.7, 1.0으로 다르게 설정)
    tasks = [
        get_gemini_review(1, diff, 0.2),
        get_gemini_review(2, diff, 0.7),
        get_gemini_review(3, diff, 1.0)
    ]
    
    all_results = await asyncio.gather(*tasks)
    
    # 2. 다수결 로직 (Consensus)
    flat_reviews = [item for sublist in all_results for item in sublist]
    line_counts = Counter([(r.get('file', 'unknown'), r.get('line'), r.get('side', 'RIGHT')) for r in flat_reviews])
    
    consensus_issues = []
    for (file_path, line, side), count in line_counts.items():
        # 3개 중 2개 이상의 인스턴스가 지적한 경우만 채택
        if count >= 2:
            relevant_reviews = [
                r for r in flat_reviews
                if r.get('file', 'unknown') == file_path
                and r.get('line') == line
                and r.get('side', 'RIGHT') == side
            ]
            consensus_issues.append({
                "file": file_path,
                "line": line,
                "side": side,
                "count": count,
                "issue": relevant_reviews[0]['issue'],
                "severity": relevant_reviews[0]['severity'],
                "category": relevant_reviews[0].get('category', 'N/A'),
                "convention_rule": relevant_reviews[0].get('convention_rule', 'N/A'),
            })

    # 3. 결과 리포트 생성
    SEVERITY_EMOJI = {"CRITICAL": "🚨", "MAJOR": "⚠️", "MINOR": "💡"}
    CATEGORY_LABEL = {
        "SECURITY": "🔒 보안",
        "LOGIC": "🧠 로직",
        "PERFORMANCE": "⚡ 성능",
        "CONVENTION": "📐 컨벤션",
    }

    if consensus_issues:
        report = "## 🤖 Gemini 다수결 코드 리뷰 결과\n"
        report += "> 3개의 Gemini 인스턴스가 교차 검증을 수행했습니다. (2표 이상 득표 항목만 표시)\n"
        report += "> 리뷰 기준: **보안/로직/성능** + **Python 코드 컨벤션 (PEP 8 + Google Style)**\n\n"

        # 카테고리 우선순위 순으로 정렬 (SECURITY > LOGIC > PERFORMANCE > CONVENTION)
        category_order = {"SECURITY": 0, "LOGIC": 1, "PERFORMANCE": 2, "CONVENTION": 3}
        sorted_issues = sorted(
            consensus_issues,
            key=lambda x: (
                category_order.get(x.get('category', 'CONVENTION'), 99),
                x.get('file', ''),
                x['line'],
                x.get('side', 'RIGHT'),
            )
        )

        # 카테고리별 섹션 분리 출력
        current_category = None
        for issue in sorted_issues:
            category = issue.get('category', 'N/A')
            if category != current_category:
                current_category = category
                section_label = CATEGORY_LABEL.get(category, f"� {category}")
                report += f"\n### {section_label}\n"

            severity = issue.get('severity', 'MINOR')
            severity_emoji = SEVERITY_EMOJI.get(severity, "💡")
            file_path = issue.get('file', 'unknown')
            line = issue['line']
            side = issue.get('side', 'RIGHT')
            side_anchor = "R" if side == "RIGHT" else "L"
            convention_rule = issue.get('convention_rule', 'N/A')
            rule_badge = f" `[{convention_rule}]`" if convention_rule != "N/A" else ""

            file_link = ""
            if file_path != "unknown":
                diff_hash = diff_link_map.get(file_path)
                if diff_hash:
                    file_link = f"https://github.com/{GITHUB_REPO}/pull/{PR_NUMBER}/files#diff-{diff_hash}{side_anchor}{line}"
                elif head_sha:
                    file_link = f"https://github.com/{GITHUB_REPO}/blob/{head_sha}/{file_path}#L{line}"
                else:
                    file_link = f"https://github.com/{GITHUB_REPO}/blob/main/{file_path}#L{line}"
                report += f"- {severity_emoji}{rule_badge} **[{file_path}:{line}]({file_link})**: {issue['issue']} ({issue['count']}/3 동의)\n"
            else:
                report += f"- {severity_emoji}{rule_badge} **{file_path}:{line}**: {issue['issue']} ({issue['count']}/3 동의)\n"

        post_github_comment(report)
    else:
        post_github_comment("## ✅ Gemini 리뷰 완료\n보안/로직/성능 이슈 및 코드 컨벤션 위반이 발견되지 않았습니다.")

if __name__ == "__main__":
    asyncio.run(main())