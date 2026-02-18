# 🐍 Python 통합 코드 컨벤션 가이드 (PEP 8 + Google Style Full Spec)

이 문서는 Python 공식 스타일 가이드인 **PEP 8**과 가독성이 뛰어난 **Google Python Style Guide**를 통합하여 정리한 단일 가이드라인입니다.

---

## 1. 명칭 규칙 (Naming Conventions)

가독성을 위해 일관된 네이밍 규칙을 준수합니다.

| 대상 | 컨벤션 | 예시 | 비고 |
| :--- | :--- | :--- | :--- |
| **모듈 (Module)** | `snake_case` | `data_processor.py` | 짧은 소문자 권장 |
| **패키지 (Package)** | `lower` | `mypackage` | 언더바(`_`) 사용 지양 |
| **클래스 (Class)** | **PascalCase** | `UserAccount` | 단어 첫 글자 대문자 |
| **함수 (Function)** | **snake_case** | `fetch_user_id()` | 동사로 시작 권장 |
| **변수 (Variable)** | **snake_case** | `retry_count` | 명사형 사용 |
| **상수 (Constant)** | **UPPER_SNAKE** | `MAX_LIMIT` | 전역 상수에 사용 |
| **보호된 속성** | `_leading_under` | `_internal_var` | 클래스 내부 전용 변수 |
| **비공개 속성** | `__double_under` | `__private_var` | 네임 망글링 적용 |

---

## 2. 레이아웃 및 포맷 (Layout & Formatting)

### 2.1 들여쓰기 및 줄 길이

- **들여쓰기:** 반드시 **공백(Space) 4칸**을 사용합니다. (Tab 사용 금지)
- **줄 길이:** 한 줄은 최대 **79~80자**를 권장하며, 가독성을 위해 최대 **120자**까지 허용합니다.
- **줄 바꿈:** 괄호나 중괄호 내에서는 암시적 줄 바꿈을 사용하며, 연산자 앞에서 줄을 바꿉니다.

### 2.2 빈 줄 (Blank Lines)

- **최상위 클래스 및 함수:** 각 정의 사이에는 **두 줄**을 띕니다.
- **클래스 내부 메서드:** 각 메서드 사이에는 **한 줄**을 띕니다.
- **함수 내부:** 논리적인 로직 묶음 사이에 가독성을 위해 한 줄을 띌 수 있습니다.

### 2.3 임포트 (Imports)

파일 최상단에서 아래 순서로 그룹화하며, 그룹 사이에는 빈 줄을 넣습니다.

1. **Standard Library:** Python 기본 라이브러리 (`os`, `sys` 등)
2. **Third-party:** 외부 설치 라이브러리 (`fastapi`, `pandas` 등)
3. **Local application:** 해당 프로젝트의 다른 모듈

```python
# ✅ 올바른 임포트 순서 예시
import asyncio
import hashlib
import json
import os
from collections import Counter

import google.generativeai as genai
import requests

from mypackage import utils
```

---

## 3. Google 스타일 독스트링 (Docstrings)

Google 스타일은 섹션 헤더를 명확히 구분하여 자동 문서화와 가독성에 최적화되어 있습니다.

### 3.1 함수 및 메서드 스펙

```python
def process_data(data: list, mode: str = "fast") -> dict:
    """데이터를 분석하여 결과 딕셔너리를 생성합니다. (Short Summary)

    여기에 함수에 대한 상세한 설명을 작성합니다. 입력 데이터의 형식이나
    처리 로직의 특이사항을 명시합니다. (Extended Description)

    Args:
        data (list): 분석할 숫자 데이터가 담긴 리스트.
        mode (str): 처리 모드. "fast" 또는 "accurate". 기본값은 "fast".

    Returns:
        dict: 분석 결과(평균, 최대값 등)를 담은 딕셔너리.

    Yields:
        str: 제너레이터인 경우 반환되는 값의 의미.

    Raises:
        ValueError: data가 비어있거나 mode가 유효하지 않을 때 발생.
        TypeError: 입력 데이터 형식이 잘못되었을 때 발생.

    Examples:
        >>> process_data([1, 2, 3], mode="fast")
        {'avg': 2.0, 'max': 3}

    Note:
        이 함수는 대용량 데이터 처리 시 메모리 사용량이 급증할 수 있습니다.

    Todo:
        향후 GPU 가속 로직 추가 예정.
    """
    if not data:
        raise ValueError("Data cannot be empty.")
    ...
```

### 3.2 클래스 스펙 (Class Template)

클래스 독스트링에는 클래스 전체의 목적과 인스턴스 변수(Attributes)를 명시합니다.

```python
class DatabaseConnector:
    """데이터베이스 연결 및 쿼리 실행을 관리하는 클래스.

    이 클래스는 싱글톤 패턴으로 설계되었으며, 커넥션 풀을 내부적으로 관리합니다.

    Attributes:
        host (str): 데이터베이스 서버 호스트 주소.
        port (int): 데이터베이스 서버 포트 번호.
        is_connected (bool): 현재 연결 상태 여부.
    """

    def __init__(self, host: str, port: int):
        """DatabaseConnector 초기화.

        Args:
            host (str): 접속할 서버의 IP 또는 도메인.
            port (int): 서비스 포트 (예: 3306, 5432).
        """
        self.host = host
        self.port = port
        self.is_connected = False

    def connect(self) -> bool:
        """서버에 연결을 시도합니다.

        Returns:
            bool: 연결 성공 시 True, 실패 시 False.
        """
        ...
```

---

## 4. 주요 권장 사항 (Best Practices)

### 4.1 타입 힌팅 (Type Hinting)

함수의 인자와 반환값에 타입을 반드시 명시합니다.

```python
# ✅ 권장
def calculate(x: int, y: int) -> float:
    return x / y

# ❌ 비권장
def calculate(x, y):
    return x / y
```

### 4.2 문자열 포맷팅

f-string(`f"User: {name}"`) 사용을 권장합니다.

```python
name = "Alice"

# ✅ 권장: f-string
message = f"Hello, {name}!"

# ❌ 비권장: % 포맷팅
message = "Hello, %s!" % name

# ❌ 비권장: .format()
message = "Hello, {}!".format(name)
```

### 4.3 조건 비교

빈 리스트나 문자열 체크 시 Implicit 타입 비교를 사용합니다.

```python
my_list = []

# ✅ 권장
if not my_list:
    ...

# ❌ 비권장
if len(my_list) == 0:
    ...
```

### 4.4 예외 처리

`except Exception:` 과 같은 포괄적 처리를 피하고, 구체적인 예외를 처리합니다.

```python
# ✅ 권장: 구체적인 예외 처리
try:
    data = json.loads(response.text)
except json.JSONDecodeError as e:
    logger.error("JSON 파싱 실패: %s", e)
    return []

# ❌ 비권장: 포괄적 예외 처리
try:
    data = json.loads(response.text)
except Exception as e:
    print(e)
    return []
```

### 4.5 로깅 (Logging)

`print()` 대신 `logging` 모듈을 사용합니다.

```python
import logging

logger = logging.getLogger(__name__)

# ✅ 권장
logger.info("처리 시작: %s", file_name)
logger.error("오류 발생: %s", error_msg)

# ❌ 비권장
print(f"처리 시작: {file_name}")
```

### 4.6 상수 정의

매직 넘버(Magic Number)를 피하고 상수로 의미를 명시합니다.

```python
# ✅ 권장
MAX_RETRY_COUNT = 3
REQUEST_TIMEOUT_SECONDS = 30

response = requests.get(url, timeout=REQUEST_TIMEOUT_SECONDS)

# ❌ 비권장
response = requests.get(url, timeout=30)
```

---

## 5. 권장 자동화 도구

| 도구 | 역할 | 설치 명령 |
| :--- | :--- | :--- |
| **Black** | 코드 포맷 자동 교정 | `pip install black` |
| **isort** | Import 구문 정렬 | `pip install isort` |
| **Flake8** | 문법 에러 및 스타일 검사 | `pip install flake8` |
| **Pylint** | 심층 코드 품질 검사 | `pip install pylint` |
| **mypy** | 정적 타입 검사 | `pip install mypy` |

### 권장 실행 순서

```bash
# 1. Import 정렬
isort .

# 2. 코드 포맷 자동 교정
black .

# 3. 스타일 검사
flake8 .

# 4. 타입 검사
mypy .
```

---

## 6. 참고 자료

- [PEP 8 – Style Guide for Python Code](https://peps.python.org/pep-0008/)
- [Google Python Style Guide](https://google.github.io/styleguide/pyguide.html)
- [Real Python: Python Code Quality](https://realpython.com/python-code-quality/)
