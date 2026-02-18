# ☕ Java 통합 코드 컨벤션 가이드 (Google Java Style Guide)

이 문서는 Java 코드 품질과 일관성을 위해 **Google Java Style Guide**를 기준으로 정리한 가이드라인입니다.
본 프로젝트의 모든 Java 코드는 이 규칙을 준수해야 합니다.

> 참고: [Google Java Style Guide 공식 문서](https://google.github.io/styleguide/javaguide.html)

---

## J1. 명칭 규칙 (Naming Conventions)

| 대상 | 컨벤션 | 예시 | 비고 |
| :--- | :--- | :--- | :--- |
| **패키지 (Package)** | `lowercase` | `com.example.myapp` | 언더바(`_`) 사용 금지 |
| **클래스 / 인터페이스** | **UpperCamelCase** | `UserService`, `Runnable` | 명사 또는 명사구 |
| **메서드 (Method)** | **lowerCamelCase** | `getUserById()` | 동사 또는 동사구로 시작 |
| **변수 (Variable)** | **lowerCamelCase** | `retryCount` | 명사형 사용 |
| **상수 (Constant)** | **UPPER_SNAKE_CASE** | `MAX_RETRY_COUNT` | `static final` 필드 |
| **타입 파라미터** | 단일 대문자 또는 대문자+숫자 | `T`, `E`, `K`, `V`, `T2` | 제네릭 타입 |

```java
// ✅ 올바른 예시
public class UserAccountService {
    private static final int MAX_RETRY_COUNT = 3;
    private String userName;

    public User getUserById(long userId) { ... }
}

// ❌ 잘못된 예시
public class user_account_service {           // 클래스명에 snake_case
    private static final int maxRetry = 3;   // 상수에 lowerCamelCase
    private String UserName;                  // 변수에 UpperCamelCase
}
```

---

## J2. 패키지 구조 (Package Structure)

- 패키지명은 **모두 소문자**, 언더바(`_`) 사용 금지
- 도메인 역순 + 프로젝트명 + 레이어명 구조 권장

```
com.example.myapp
├── controller      // 또는 presentation
├── service
├── repository      // 또는 infrastructure
├── domain          // 또는 model
└── config
```

```java
// ✅ 올바른 예시
package com.example.analysislunch.service;

// ❌ 잘못된 예시
package com.example.Analysis_Lunch.Service;
```

---

## J3. 임포트 순서 (Import Order)

아래 순서로 그룹화하며, 그룹 사이에는 **빈 줄**을 넣습니다.
와일드카드 임포트(`import java.util.*`)는 **사용 금지**합니다.

1. `static` 임포트
2. `java.*` (JDK 표준 라이브러리)
3. `javax.*`, `jakarta.*`
4. 서드파티 라이브러리 (`org.*`, `com.*` 등)
5. 프로젝트 내부 모듈

```java
// ✅ 올바른 임포트 순서
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import jakarta.persistence.Entity;

import org.springframework.stereotype.Service;

import com.example.analysislunch.domain.User;
```

---

## J4. 클래스 내부 구조 순서 (Class Member Ordering)

클래스 내부 멤버는 아래 순서로 선언합니다.

1. `static` 상수 (`static final` 필드)
2. `static` 변수 (`static` 필드)
3. 인스턴스 변수 (필드)
4. 생성자 (`Constructor`)
5. `static` 팩토리 메서드
6. 공개 메서드 (`public`)
7. 보호 메서드 (`protected`)
8. 비공개 메서드 (`private`)
9. `equals()`, `hashCode()`, `toString()` 오버라이드

```java
// ✅ 올바른 클래스 구조 예시
public class OrderService {

    // 1. static 상수
    private static final int MAX_ORDER_LIMIT = 100;

    // 2. 인스턴스 변수
    private final OrderRepository orderRepository;

    // 3. 생성자
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // 4. 공개 메서드
    public Order createOrder(OrderRequest request) { ... }

    // 5. 비공개 메서드
    private void validateOrder(OrderRequest request) { ... }
}
```

---

## J5. Javadoc

모든 `public` 클래스, 메서드, 필드에는 Javadoc을 작성합니다.

```java
/**
 * 사용자 계정을 관리하는 서비스 클래스.
 *
 * <p>사용자 생성, 조회, 수정, 삭제(CRUD) 기능을 제공합니다.
 * 모든 작업은 트랜잭션 내에서 수행됩니다.
 *
 * @author 작성자명
 * @since 1.0.0
 */
public class UserService {

    /**
     * ID로 사용자를 조회합니다.
     *
     * @param userId 조회할 사용자의 고유 ID (양수여야 함)
     * @return 조회된 {@link User} 객체
     * @throws UserNotFoundException userId에 해당하는 사용자가 없을 때
     * @throws IllegalArgumentException userId가 0 이하일 때
     */
    public User getUserById(long userId) { ... }
}
```

---

## J6. 예외 처리 (Exception Handling)

- `Exception`, `Throwable`을 직접 `catch`하는 것을 **금지**합니다.
- `catch` 블록을 비워두는 것을 **금지**합니다. (빈 catch 금지)
- 예외는 반드시 **로깅하거나 재던지기(re-throw)**해야 합니다.
- 비즈니스 예외는 **커스텀 예외 클래스**를 사용합니다.

```java
// ✅ 올바른 예외 처리
try {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
} catch (DataAccessException e) {
    log.error("DB 조회 실패 - userId: {}", userId, e);
    throw new ServiceException("사용자 조회 중 오류가 발생했습니다.", e);
}

// ❌ 잘못된 예외 처리
try {
    return userRepository.findById(userId);
} catch (Exception e) {   // 너무 광범위한 catch
    e.printStackTrace();  // System.err 출력 금지
}

// ❌ 빈 catch 블록 금지
try {
    process();
} catch (IOException e) {
    // 아무것도 하지 않음 - 절대 금지!
}
```

---

## J7. 로깅 (Logging)

- `System.out.println()`, `e.printStackTrace()`는 **절대 사용 금지**합니다.
- **SLF4J + Logback** 조합을 표준으로 사용합니다.
- Lombok의 `@Slf4j` 어노테이션 사용을 권장합니다.
- 로그 메시지에 민감 정보(비밀번호, 토큰 등)를 **포함하지 않습니다**.

```java
// ✅ 올바른 로깅 (Lombok @Slf4j 사용)
@Slf4j
@Service
public class UserService {

    public User getUserById(long userId) {
        log.info("사용자 조회 시작 - userId: {}", userId);
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
            log.debug("사용자 조회 성공 - user: {}", user.getEmail());
            return user;
        } catch (UserNotFoundException e) {
            log.warn("사용자를 찾을 수 없음 - userId: {}", userId);
            throw e;
        }
    }
}

// ❌ 잘못된 로깅
System.out.println("userId: " + userId);  // System.out 금지
e.printStackTrace();                       // printStackTrace 금지
log.info("password: " + user.getPassword()); // 민감 정보 로깅 금지
```

---

## J8. 매직 넘버 및 상수 (Magic Numbers & Constants)

의미 없는 리터럴 숫자/문자열을 직접 사용하지 않고 **`static final` 상수**로 정의합니다.

```java
// ✅ 올바른 상수 사용
public class OrderValidator {
    private static final int MAX_ORDER_QUANTITY = 100;
    private static final String STATUS_PENDING = "PENDING";
    private static final long TOKEN_EXPIRY_SECONDS = 3600L;

    public void validate(Order order) {
        if (order.getQuantity() > MAX_ORDER_QUANTITY) {
            throw new IllegalArgumentException("주문 수량 초과: " + MAX_ORDER_QUANTITY);
        }
    }
}

// ❌ 잘못된 매직 넘버 사용
public void validate(Order order) {
    if (order.getQuantity() > 100) {   // 100이 무엇을 의미하는지 불명확
        throw new IllegalArgumentException("주문 수량 초과");
    }
}
```

---

## J9. 포맷팅 (Formatting)

### 들여쓰기 및 줄 길이
- **들여쓰기:** 스페이스 **4칸** (Tab 사용 금지)
- **줄 길이:** 최대 **100자** (Google Style 기준)
- **줄 바꿈:** 연산자 앞에서 줄을 바꿉니다.

### 빈 줄 규칙
- 클래스 내 멤버 그룹 사이: **1줄**
- 메서드 내 논리 블록 사이: **1줄** (선택)
- 클래스 정의 시작 직후 빈 줄 **금지**

### 중괄호 (Braces)
- **K&R 스타일** 사용: 여는 중괄호는 같은 줄에 위치
- `if`, `for`, `while` 등 단일 문장이라도 **중괄호 필수**

```java
// ✅ 올바른 포맷팅
if (user.isActive()) {
    processUser(user);
}

for (int i = 0; i < MAX_RETRY_COUNT; i++) {
    retry();
}

// ❌ 잘못된 포맷팅
if (user.isActive())
    processUser(user);  // 중괄호 생략 금지

if(user.isActive()){   // 여는 괄호 앞 공백 필수, 중괄호 줄 바꿈 금지
    processUser(user);
}
```

---

## 권장 자동화 도구

| 도구 | 역할 | 설정 방법 |
| :--- | :--- | :--- |
| **google-java-format** | 코드 포맷 자동 교정 | IntelliJ 플러그인 또는 Maven/Gradle 플러그인 |
| **Checkstyle** | 스타일 규칙 검사 (CI 연동) | `checkstyle.xml` 설정 후 Maven/Gradle 연동 |
| **SpotBugs** | 정적 버그 분석 | Maven/Gradle 플러그인 |
| **SonarQube** | 종합 코드 품질 분석 | CI/CD 파이프라인 연동 |

---

## 참고 자료

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Oracle Java Code Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html)
- [Effective Java (Joshua Bloch)](https://www.oreilly.com/library/view/effective-java/9780134686097/)
