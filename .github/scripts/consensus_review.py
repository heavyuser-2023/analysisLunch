import os
import sys
import json
import asyncio
import hashlib
import requests
from collections import Counter
from google import genai
from google.genai import types

# 환경 변수 로드
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
GITHUB_REPO = os.getenv("GITHUB_REPO")
PR_NUMBER = os.getenv("PR_NUMBER")

# Gemini 설정
client = genai.Client(api_key=GEMINI_API_KEY)
# 코딩·에이전트용 정식 Flash 모델 (환경에 맞춰 모델명 수정 가능)
MODEL_NAME = "gemini-3.8-flash"
# 병렬로 실행할 리뷰 인스턴스 수와, 이슈 채택에 필요한 최소 득표 수
INSTANCE_COUNT = 3
CONSENSUS_THRESHOLD = 2

async def get_gemini_review(instance_id, diff):
    """Gemini 모델 호출. 실패 시 None을 반환해 '이슈 없음'([])과 구분"""
    prompt = f"""
    당신은 10년 차 시니어 소프트웨어 엔지니어이자 Java 코드 품질 전문가입니다.
    아래의 코드 변경사항(diff)을 **보안/로직/성능** 관점과 **Java 코드 컨벤션** 관점 모두에서 엄격하게 리뷰해 주세요.

    ===== [필수 리뷰 기준 1] 보안 / 로직 / 성능 =====
    - 보안 취약점 (인증 누락, 민감 정보 노출, SQL 인젝션, XSS 등)
    - 로직 오류 (잘못된 조건, NullPointerException 위험, 엣지 케이스 미처리 등)
    - 성능 저하 요인 (N+1 쿼리, 불필요한 반복, 비효율적 자료구조 등)
    - 리소스 누수 (Connection, Stream, File 등 close() 미처리)
    - 스레드 안전성 문제 (동시성 이슈, 공유 상태 미보호 등)

    ===== [필수 리뷰 기준 2] Java 코드 컨벤션 (Google Java Style Guide) =====
    diff에 .java 파일이 포함된 경우, 아래 항목들을 **하나도 빠짐없이** 검사하세요.
    위반 시 반드시 이슈로 보고해야 합니다.

    [J1] 명칭 규칙 (Naming Conventions)
    - 클래스/인터페이스: UpperCamelCase 사용 여부 (예: userService ❌ → UserService ✅)
    - 메서드/변수: lowerCamelCase 사용 여부 (예: GetUser ❌ → getUser ✅)
    - 상수(static final): UPPER_SNAKE_CASE 사용 여부 (예: maxRetry ❌ → MAX_RETRY ✅)
    - 패키지: 모두 소문자, 언더바 금지 여부 (예: com.Example.My_App ❌ → com.example.myapp ✅)

    [J2] 패키지 구조 (Package Structure)
    - 패키지명이 모두 소문자인지 여부
    - 언더바(`_`)나 대문자가 포함되어 있는지 여부

    [J3] 임포트 순서 (Import Order)
    - static 임포트 → java.* → javax./jakarta.* → 서드파티 → 내부 모듈 순서 준수 여부
    - 와일드카드 임포트(`import java.util.*`) 사용 여부 (사용 금지)
    - 그룹 사이 빈 줄 존재 여부

    [J4] 클래스 내부 구조 순서 (Class Member Ordering)
    - static 상수 → static 변수 → 인스턴스 변수 → 생성자 → public 메서드 → private 메서드 순서 준수 여부

    [J5] Javadoc
    - 모든 public 클래스, 메서드에 Javadoc이 있는지 여부
    - @param, @return, @throws 태그가 올바르게 작성되었는지 여부

    [J6] 예외 처리 (Exception Handling)
    - Exception/Throwable을 직접 catch하는지 여부 (금지)
    - 빈 catch 블록이 있는지 여부 (금지)
    - e.printStackTrace() 사용 여부 (금지, 로거 사용 필수)
    - 예외 발생 시 로깅 또는 재던지기(re-throw) 없이 무시하는지 여부

    [J7] 로깅 (Logging)
    - System.out.println() 또는 e.printStackTrace() 사용 여부 (금지)
    - SLF4J(log.info/warn/error) 또는 @Slf4j 사용 여부
    - 로그 메시지에 비밀번호, 토큰 등 민감 정보가 포함되는지 여부

    [J8] 매직 넘버 / 상수 (Magic Numbers & Constants)
    - 의미 없는 리터럴 숫자/문자열을 static final 상수로 분리했는지 여부
    - 예: if (count > 100) ❌ → if (count > MAX_ORDER_COUNT) ✅

    [J9] 포맷팅 (Formatting)
    - 들여쓰기: 스페이스 4칸 사용 여부 (Tab 금지)
    - 줄 길이: 100자 이내 여부
    - if/for/while 등 단일 문장에도 중괄호 사용 여부 (생략 금지)
    - 여는 중괄호가 같은 줄에 위치하는지 여부 (K&R 스타일)

    ===== [응답 형식] =====
    결과는 반드시 아래 JSON 형식으로만 응답하세요. (마크다운 코드 블록 제외, 순수 JSON만)
    {{
      "reviews": [
        {{
          "file": "path/from/diff",
          "line": line_number,
          "side": "RIGHT|LEFT",
          "category": "SECURITY|LOGIC|PERFORMANCE|CONVENTION",
          "convention_rule": "J1|J2|J3|J4|J5|J6|J7|J8|J9|N/A",
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
    - convention_rule: 컨벤션 위반인 경우 해당 규칙 번호(J1~J9), 아니면 N/A.
    - severity 기준:
        * CRITICAL: 보안 취약점, 데이터 손실 가능성, 런타임 오류 유발, 리소스 누수
        * MAJOR: 로직 오류, 예외 처리 부재, 로깅 미사용, Javadoc 전면 누락
        * MINOR: 네이밍, 임포트 순서, 포맷팅, 매직 넘버 등 스타일 위반

    [Diff]
    {diff}
    """

    try:
        # Gemini 3 계열은 temperature 기본값(1.0) 유지가 공식 권장이므로,
        # 같은 설정으로 독립 호출해 생기는 응답 차이로 의견 다양성 확보
        response = await client.aio.models.generate_content(
            model=MODEL_NAME,
            contents=prompt,
            config=types.GenerateContentConfig(response_mime_type="application/json"),
        )
        return json.loads(response.text).get("reviews") or []
    except Exception as e:
        print(f"Instance {instance_id} failed: {e}")
        return None


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

    # 1. 리뷰 인스턴스를 병렬로 실행
    tasks = [get_gemini_review(i, diff) for i in range(1, INSTANCE_COUNT + 1)]
    all_results = await asyncio.gather(*tasks)

    # 실패한 인스턴스는 제외. 다수결이 불가능하면 '이슈 없음'으로 보이지 않도록 실패를 알리고 종료
    succeeded = [r for r in all_results if r is not None]
    responded = len(succeeded)
    if responded < CONSENSUS_THRESHOLD:
        post_github_comment(
            "## ❌ Gemini 리뷰 실패\n"
            f"{INSTANCE_COUNT}개 인스턴스 중 {responded}개만 응답하여 다수결 리뷰를 수행하지 못했습니다. "
            "워크플로 로그를 확인해 주세요."
        )
        sys.exit(1)

    # 2. 다수결 로직 (Consensus)
    flat_reviews = [item for sublist in succeeded for item in sublist]
    line_counts = Counter([(r.get('file', 'unknown'), r.get('line'), r.get('side', 'RIGHT')) for r in flat_reviews])

    consensus_issues = []
    for (file_path, line, side), count in line_counts.items():
        # CONSENSUS_THRESHOLD개 이상의 인스턴스가 지적한 경우만 채택
        if count >= CONSENSUS_THRESHOLD:
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
        "CONVENTION": "📐 컨벤션 (Google Java Style Guide)",
    }
    # 카테고리 우선순위: SECURITY > LOGIC > PERFORMANCE > CONVENTION
    CATEGORY_ORDER = {"SECURITY": 0, "LOGIC": 1, "PERFORMANCE": 2, "CONVENTION": 3}

    if consensus_issues:
        report = "## 🤖 Gemini 다수결 코드 리뷰 결과\n"
        report += f"> {responded}개의 Gemini 인스턴스가 교차 검증을 수행했습니다. ({CONSENSUS_THRESHOLD}표 이상 득표 항목만 표시)\n"
        report += "> 리뷰 기준: **보안/로직/성능** + **Java 코드 컨벤션 (Google Java Style Guide)**\n\n"

        sorted_issues = sorted(
            consensus_issues,
            key=lambda x: (
                CATEGORY_ORDER.get(x.get('category', 'CONVENTION'), 99),
                x.get('file', ''),
                x['line'],
                x.get('side', 'RIGHT'),
            )
        )

        current_category = None
        for issue in sorted_issues:
            category = issue.get('category', 'N/A')
            if category != current_category:
                current_category = category
                section_label = CATEGORY_LABEL.get(category, f"📌 {category}")
                report += f"\n### {section_label}\n"

            severity = issue.get('severity', 'MINOR')
            severity_emoji = SEVERITY_EMOJI.get(severity, "💡")
            file_path = issue.get('file', 'unknown')
            line = issue['line']
            side = issue.get('side', 'RIGHT')
            side_anchor = "R" if side == "RIGHT" else "L"
            convention_rule = issue.get('convention_rule', 'N/A')
            rule_badge = f" `[{convention_rule}]`" if convention_rule not in ('N/A', None) else ""

            file_link = ""
            if file_path != "unknown":
                diff_hash = diff_link_map.get(file_path)
                if diff_hash:
                    file_link = f"https://github.com/{GITHUB_REPO}/pull/{PR_NUMBER}/files#diff-{diff_hash}{side_anchor}{line}"
                elif head_sha:
                    file_link = f"https://github.com/{GITHUB_REPO}/blob/{head_sha}/{file_path}#L{line}"
                else:
                    file_link = f"https://github.com/{GITHUB_REPO}/blob/main/{file_path}#L{line}"
                report += f"- {severity_emoji}{rule_badge} **[{file_path}:{line}]({file_link})**: {issue['issue']} ({issue['count']}/{responded} 동의)\n"
            else:
                report += f"- {severity_emoji}{rule_badge} **{file_path}:{line}**: {issue['issue']} ({issue['count']}/{responded} 동의)\n"

        post_github_comment(report)
    else:
        post_github_comment("## ✅ Gemini 리뷰 완료\n보안/로직/성능 이슈 및 Java 코드 컨벤션 위반이 발견되지 않았습니다.")

if __name__ == "__main__":
    asyncio.run(main())