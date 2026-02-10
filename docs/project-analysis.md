# dooray-mcp-server 프로젝트 분석

## 1. 프로젝트 개요

dooray-mcp-server는 NHN Dooray 협업 플랫폼의 Wiki, 프로젝트/업무, 댓글 API를 MCP (Model Context Protocol) 도구로 노출하는 서버입니다. AI 어시스턴트(예: Claude)가 Dooray의 협업 기능을 직접 호출할 수 있도록 브리지 역할을 수행합니다.

- **통신 방식**: STDIO (Standard Input/Output) 트랜스포트
- **언어**: Kotlin/JVM
- **현재 버전**: 0.2.1
- **제공 기능**: 위키 관리(5개), 프로젝트 조회(1개), 업무 관리(6개), 댓글 관리(4개) 도구

## 2. 기술 스택

| 구성 요소 | 기술 | 버전 |
|-----------|------|------|
| Language | Kotlin (JVM) | 2.1.20 |
| JDK | Eclipse Temurin | 21 |
| MCP SDK | io.modelcontextprotocol:kotlin-sdk | 0.6.0 |
| HTTP Client | Ktor Client | 3.1.1 |
| Serialization | kotlinx-serialization-json | - |
| Logging | Logback Classic + SLF4J | 1.5.18 |
| Build Tool | Gradle (Kotlin DSL) | 8.13 |
| Fat JAR Plugin | ShadowJar | 8.1.1 |
| Testing | JUnit 5 + MockK + kotlinx-coroutines-test + Ktor Mock | - |
| Container | Docker (multi-stage, Alpine JRE) | - |
| CI/CD | GitHub Actions | - |

## 3. 프로젝트 구조

```
dooray-mcp-server/
├── src/
│   ├── main/
│   │   ├── kotlin/com/bifos/dooray/mcp/
│   │   │   ├── Main.kt                      -- 진입점, 로깅 설정 초기화
│   │   │   ├── DoorayMcpServer.kt           -- MCP 서버 초기화, 도구 등록
│   │   │   ├── client/
│   │   │   │   ├── DoorayClient.kt          -- 인터페이스 (18개 suspend 함수)
│   │   │   │   └── DoorayHttpClient.kt      -- Ktor 기반 구현체
│   │   │   ├── tools/                       -- MCP 도구 정의 (16개 파일)
│   │   │   │   ├── GetWikisTool.kt
│   │   │   │   ├── GetWikiPagesTool.kt
│   │   │   │   ├── GetWikiPageTool.kt
│   │   │   │   ├── CreateWikiPageTool.kt
│   │   │   │   ├── UpdateWikiPageTool.kt
│   │   │   │   ├── GetProjectsTool.kt
│   │   │   │   ├── GetProjectPostsTool.kt
│   │   │   │   ├── GetProjectPostTool.kt
│   │   │   │   ├── CreateProjectPostTool.kt
│   │   │   │   ├── UpdateProjectPostTool.kt
│   │   │   │   ├── SetProjectPostWorkflowTool.kt
│   │   │   │   ├── SetProjectPostDoneTool.kt
│   │   │   │   ├── CreatePostCommentTool.kt
│   │   │   │   ├── GetPostCommentsTool.kt
│   │   │   │   ├── UpdatePostCommentTool.kt
│   │   │   │   └── DeletePostCommentTool.kt
│   │   │   ├── types/                       -- 직렬화 데이터 모델 (7개 파일)
│   │   │   │   ├── WikiListResponse.kt
│   │   │   │   ├── WikiPagesResponse.kt
│   │   │   │   ├── WikiPageResponse.kt
│   │   │   │   ├── ProjectPostResponse.kt
│   │   │   │   ├── DoorayApiSuccessType.kt
│   │   │   │   ├── DoorayApiErrorType.kt
│   │   │   │   └── ToolResponseTypes.kt
│   │   │   ├── exception/                   -- 예외 처리
│   │   │   │   ├── CustomException.kt       -- HTTP 계층 예외
│   │   │   │   └── ToolException.kt         -- 도구 계층 예외
│   │   │   ├── utils/
│   │   │   │   └── JsonUtils.kt             -- JSON 유틸리티
│   │   │   └── constants/
│   │   │       ├── VersionConst.kt          -- 버전 상수
│   │   │       └── EnvVariableConst.kt      -- 환경변수 상수
│   │   └── resources/
│   │       └── logback.xml                  -- Logback 설정
│   └── test/
│       └── kotlin/com/bifos/dooray/mcp/
│           ├── tools/
│           │   └── McpToolsUnitTest.kt      -- 도구 단위 테스트 (17개 테스트)
│           ├── client/dooray/
│           │   ├── WikiDoorayIntegrationTest.kt          -- 위키 통합 테스트 (6개)
│           │   ├── ProjectPostDoorayIntegrationTest.kt   -- 업무 통합 테스트 (12개)
│           │   ├── ProjectPostCommentsDoorayIntegrationTest.kt  -- 댓글 통합 테스트 (6개)
│           │   └── BaseDoorayIntegrationTest.kt          -- 통합 테스트 기본 클래스
│           └── util/
│               └── (유틸리티 테스트)
├── docs/                                    -- API 참조 문서
│   ├── wiki-api.md
│   ├── wiki-create-api.md
│   ├── project-api.md
│   └── project-post-comment.md
├── scripts/                                 -- Docker 빌드/푸시 스크립트
├── .github/workflows/                       -- CI/CD 파이프라인
│   ├── pr.yml                               -- PR 파이프라인
│   └── main.yml                             -- Main 브랜치 파이프라인
├── build.gradle.kts                         -- Gradle 빌드 설정
├── gradle.properties                        -- Gradle 속성 (버전 관리)
├── Dockerfile                               -- Docker 이미지 빌드
└── README.md                                -- 프로젝트 설명
```

## 4. 아키텍처

### 4.1 계층 구조

dooray-mcp-server는 명확한 3계층 아키텍처를 따릅니다:

#### Presentation Tier (표현 계층)
- **위치**: `tools/` 패키지
- **역할**: MCP 도구 정의 및 요청/응답 변환
- **구성요소**: 16개 도구 파일, 각각 `*Tool()` (스키마 정의)와 `*Handler()` (핸들러 로직) 함수 제공
- **책임**:
  - CallToolRequest에서 파라미터 파싱
  - 필수 파라미터 검증
  - ToolSuccessResponse 또는 ToolErrorResponse로 응답 포맷팅

#### Business Tier (비즈니스 계층)
- **위치**: 각 도구의 핸들러 로직 함수
- **역할**: 비즈니스 로직 처리
- **책임**:
  - 파라미터 검증 및 변환
  - Fetch-Merge-Update 패턴 구현 (부분 업데이트)
  - 데이터 변환 및 구조화
  - 에러 처리 및 ToolException 생성

#### Data Access Tier (데이터 접근 계층)
- **위치**: `client/` 패키지
- **역할**: Dooray REST API와의 HTTP 통신
- **구성요소**:
  - `DoorayClient` 인터페이스 (18개 suspend 함수)
  - `DoorayHttpClient` 구현체 (Ktor 기반)
- **책임**:
  - HTTP 요청 실행
  - 인증 헤더 설정
  - JSON 직렬화/역직렬화
  - HTTP 에러 처리

### 4.2 데이터 흐름

```
MCP Client (Claude 등)
    ↓ [STDIO - stdin]
StdioServerTransport
    ↓ [역직렬화]
DoorayMcpServer (도구 라우팅)
    ↓ [CallToolRequest]
Tool Handler (파라미터 추출 및 검증)
    ↓ [검증된 파라미터]
DoorayClient 인터페이스 메서드 호출
    ↓
DoorayHttpClient (HTTP 요청 실행)
    ↓ [HTTPS]
Dooray REST API
    ↓ [JSON 응답]
DoorayHttpClient (역직렬화)
    ↓ [타입 안전 객체]
Tool Handler (응답 변환)
    ↓ [ToolSuccessResponse/ToolErrorResponse]
StdioServerTransport (JSON 직렬화)
    ↓ [STDIO - stdout]
MCP Client
```

**상세 단계:**

1. MCP 클라이언트가 `CallToolRequest`를 STDIO(stdin)으로 전송
2. `StdioServerTransport`가 JSON을 역직렬화하여 등록된 핸들러로 라우팅
3. 핸들러가 `request.arguments`에서 파라미터 추출 (JsonElement → String/List 등)
4. 필수 파라미터 검증 (누락 시 `ToolErrorResponse` 반환)
5. `DoorayClient` 인터페이스 메서드 호출 (예: `getWikiPage()`)
6. `DoorayHttpClient`가 Ktor를 통해 HTTP 요청 실행
7. HTTP 응답을 kotlinx.serialization으로 역직렬화
8. `ToolSuccessResponse<T>` 또는 `ToolErrorResponse`로 래핑
9. JsonUtils를 통해 JSON 직렬화 후 `TextContent`로 반환
10. STDIO(stdout)를 통해 클라이언트로 전송

### 4.3 의존성 방향

```
Tools → DoorayClient (Interface) → DoorayHttpClient (Impl) → Dooray API
```

- **순방향 의존성만 존재**: 하위 계층이 상위 계층을 참조하지 않음
- **순환 의존성 없음**: 인터페이스를 통한 의존성 역전 원칙(DIP) 적용
- **테스트 용이성**: MockK를 사용해 DoorayClient를 모킹하여 도구 계층 단위 테스트 가능

## 5. MCP 도구 목록

### 5.1 위키 도구 (5개)

| 도구 이름 | 설명 | 주요 파라미터 |
|-----------|------|---------------|
| `dooray_wiki_list_projects` | 위키 프로젝트 목록 조회 | page, size |
| `dooray_wiki_list_pages` | 특정 위키의 페이지 목록 조회 | wikiId, parentPageId (선택) |
| `dooray_wiki_get_page` | 위키 페이지 상세 조회 | wikiId, pageId |
| `dooray_wiki_create_page` | 새 위키 페이지 생성 | wikiId, parentPageId (선택), subject, body |
| `dooray_wiki_update_page` | 위키 페이지 수정 | wikiId, pageId, subject/body/referrer_member_ids (선택) |

### 5.2 프로젝트 도구 (1개)

| 도구 이름 | 설명 | 주요 파라미터 |
|-----------|------|---------------|
| `dooray_project_list_projects` | 프로젝트 목록 조회 | type, scope, state, page, size |

### 5.3 업무 도구 (6개)

| 도구 이름 | 설명 | 주요 파라미터 |
|-----------|------|---------------|
| `dooray_project_list_posts` | 업무 목록 조회 | projectId, page, size, fromMemberIds, toMemberIds, ccMemberIds, tagIds, postWorkflowClasses, postWorkflowIds, milestoneIds, subjects, createdAt, updatedAt, dueAt, order (17개 필터 파라미터) |
| `dooray_project_get_post` | 업무 상세 조회 | projectId, postId |
| `dooray_project_create_post` | 업무 생성 | projectId, subject, body, toMemberIds, ccMemberIds, priority, milestoneId, tagIds, dueDate, parentPostId |
| `dooray_project_update_post` | 업무 수정 (Fetch-Merge-Update) | projectId, postId, subject/body/toMemberIds/ccMemberIds/priority/dueDate (선택) |
| `dooray_project_set_post_workflow` | 업무 워크플로우 상태 변경 | projectId, postId, workflowId |
| `dooray_project_set_post_done` | 업무 완료 처리 | projectId, postId |

### 5.4 댓글 도구 (4개)

| 도구 이름 | 설명 | 주요 파라미터 |
|-----------|------|---------------|
| `dooray_project_create_post_comment` | 업무에 댓글 생성 | projectId, postId, content, mimeType |
| `dooray_project_get_post_comments` | 업무 댓글 목록 조회 | projectId, postId, page, size, order |
| `dooray_project_update_post_comment` | 댓글 수정 | projectId, postId, logId, content |
| `dooray_project_delete_post_comment` | 댓글 삭제 | projectId, postId, logId |

## 6. 핵심 설계 패턴

### 6.1 Template Method 패턴

`DoorayHttpClient`는 두 개의 템플릿 메서드를 제공하여 HTTP 호출 라이프사이클을 표준화합니다:

#### `executeApiCall<T>()` - 일반 API 호출
```kotlin
private suspend inline fun <reified T> executeApiCall(
    operation: String,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
    successMessage: String? = null,
    crossinline apiCall: suspend () -> HttpResponse
): T
```
- 성공 시 타입 T로 역직렬화된 result 반환
- 18개 API 메서드 중 대부분이 이 템플릿 사용

#### `executeApiCallForNullableResult()` - result가 null일 수 있는 API
```kotlin
private suspend fun executeApiCallForNullableResult(
    operation: String,
    expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
    successMessage: String,
    apiCall: suspend () -> HttpResponse
): DoorayApiUnitResponse
```
- result가 null일 수 있는 응답 처리 (예: PUT, POST, DELETE)
- header.isSuccessful로 성공 여부 판단

**장점**:
- 로깅, 에러 처리, 상태 코드 검증 로직이 모든 API 호출에 일관되게 적용
- 중복 코드 제거

### 6.2 Factory Function 패턴

각 도구 파일은 두 개의 팩토리 함수를 제공합니다:

```kotlin
// 1. Tool 스키마 정의 함수
fun getWikiPageTool(): Tool {
    return Tool(
        name = "dooray_wiki_get_page",
        description = "...",
        inputSchema = Tool.Input(...)
    )
}

// 2. Handler 로직 함수
fun getWikiPageHandler(doorayClient: DoorayClient): suspend (CallToolRequest) -> CallToolResult {
    return { request ->
        // 파라미터 추출, 검증, API 호출, 응답 변환
    }
}
```

**특징**:
- 상태가 없는 함수형 코틀린 스타일
- `DoorayMcpServer.registerTool()`에서 `addTool(tool(), handler())` 형태로 등록
- 의존성 주입: DoorayClient를 파라미터로 받아 핸들러에서 사용

### 6.3 Fetch-Merge-Update 패턴

`UpdateWikiPageTool`과 `UpdateProjectPostTool`에서 사용하는 부분 업데이트 패턴:

```kotlin
// 1. 현재 상태 조회 (Fetch)
val currentPageResponse = doorayClient.getWikiPage(wikiId, pageId)
val currentPage = currentPageResponse.result

// 2. 사용자 제공 필드만 머지 (Merge)
val finalSubject = params.newSubject ?: currentPage.subject
val finalBody = params.newBodyContent?.let {
    WikiPageBody(mimeType = "text/x-markdown", content = it)
} ?: currentPage.body

// 3. 전체 데이터로 PUT 요청 (Update)
val updateRequest = UpdateWikiPageRequest(
    subject = finalSubject,
    body = finalBody,
    referrers = finalReferrers
)
doorayClient.updateWikiPage(wikiId, pageId, updateRequest)
```

**장점**:
- 사용자는 변경할 필드만 제공하면 됨 (UX 개선)
- Dooray API가 PATCH를 지원하지 않는 경우 대안

**단점**:
- HTTP 호출이 2번 필요 (GET + PUT)
- 동시성 이슈 가능성 (GET과 PUT 사이에 다른 업데이트 발생)

### 6.4 Envelope/Wrapper 패턴

모든 도구 응답은 표준화된 JSON 구조로 래핑됩니다:

#### 성공 응답
```kotlin
@Serializable
data class ToolSuccessResponse<T>(
    val success: Boolean = true,
    val data: T,
    val message: String? = null
)
```

#### 에러 응답
```kotlin
@Serializable
data class ToolErrorResponse(
    val isError: Boolean = true,
    val error: ToolError,
    val content: ToolErrorContent
)
```

**장점**:
- MCP 클라이언트가 성공/실패를 즉시 판단 가능
- 일관된 에러 처리
- 디버깅 용이 (message, details 제공)

### 6.5 Type Alias 패턴

제네릭 응답 타입에 의미론적 이름을 부여:

```kotlin
typealias WikiListResponse = DoorayApiResponse<List<Wiki>>
typealias WikiPageResponse = DoorayApiResponse<WikiPage>
typealias PostListResponse = DoorayApiResponse<PostListResult>
```

**장점**:
- 코드 가독성 향상
- 도메인 용어 명확화
- IDE 자동완성 개선

## 7. 에러 처리 전략

dooray-mcp-server는 3계층 예외 모델을 사용합니다:

### 7.1 계층별 예외

#### 1. HTTP 계층 - `CustomException`
```kotlin
class CustomException(
    message: String? = null,
    val httpStatus: Int? = null,
    rootCause: Throwable? = null
) : RuntimeException(...)
```
- **발생 위치**: `DoorayHttpClient`의 `handleErrorResponse()`, `handleGenericException()`
- **용도**: HTTP 통신 오류, Dooray API 에러 응답
- **정보**: HTTP 상태 코드, 결과 메시지, 근본 원인

#### 2. 도구 계층 - `ToolException`
```kotlin
class ToolException(
    val type: String,      // VALIDATION_ERROR, API_ERROR, PARAMETER_MISSING, INTERNAL_ERROR
    message: String,
    val code: String? = null,
    val details: String? = null,
    cause: Throwable? = null
) : Exception(...)
```
- **발생 위치**: 도구 핸들러의 검증 로직
- **용도**: 파라미터 검증 실패, 비즈니스 로직 오류
- **변환**: `toErrorResponse()` 메서드로 ToolErrorResponse 생성

#### 3. 응답 계층 - `ToolErrorResponse`
```kotlin
@Serializable
data class ToolErrorResponse(
    val isError: Boolean = true,
    val error: ToolError,
    val content: ToolErrorContent
)
```
- **발생 위치**: 도구 핸들러의 최종 응답
- **용도**: MCP 클라이언트로 전송되는 JSON 응답

### 7.2 에러 처리 흐름

```
HTTP 오류 발생
    ↓
DoorayHttpClient가 CustomException 발생
    ↓
도구 핸들러가 catch하여 ToolException으로 변환
    ↓
toErrorResponse()로 ToolErrorResponse 생성
    ↓
JSON 직렬화하여 TextContent로 반환
    ↓
MCP 클라이언트에 전송
```

### 7.3 에러 응답 형식 예시

```json
{
  "isError": true,
  "error": {
    "type": "PARAMETER_MISSING",
    "code": "MISSING_PROJECT_ID",
    "details": null
  },
  "content": {
    "type": "text",
    "text": "project_id 파라미터가 필요합니다. dooray_project_list_projects를 사용해서 프로젝트 ID를 먼저 조회하세요."
  }
}
```

## 8. 설정 및 환경변수

### 8.1 필수 환경변수

| 변수 이름 | 용도 | 예시 |
|-----------|------|------|
| `DOORAY_API_KEY` | Dooray API 인증 키 | `your_api_key_here` |
| `DOORAY_BASE_URL` | Dooray API 기본 URL | `https://api.dooray.com` |

### 8.2 선택적 환경변수

| 변수 이름 | 기본값 | 가능한 값 | 용도 |
|-----------|--------|-----------|------|
| `DOORAY_LOG_LEVEL` | `WARN` | `DEBUG`, `INFO`, `WARN`, `ERROR` | 일반 로깅 레벨 (Logback) |
| `DOORAY_HTTP_LOG_LEVEL` | `NONE` | `NONE`, `INFO`, `HEADERS`, `BODY`, `ALL` | HTTP 클라이언트 로깅 레벨 (Ktor) |

### 8.3 로깅 레벨 권장 사항

| 환경 | DOORAY_LOG_LEVEL | DOORAY_HTTP_LOG_LEVEL | 이유 |
|------|------------------|------------------------|------|
| **프로덕션** | `WARN` (기본값) | `NONE` (기본값) | MCP 통신 안정성 보장, stdout 오염 방지 |
| **개발** | `INFO` | `INFO` | 적절한 정보 로깅 |
| **디버깅** | `DEBUG` | `ALL` | 상세한 HTTP 요청/응답 확인 |

## 9. 로깅 전략

### 9.1 STDIO 트랜스포트와 로깅

MCP 서버는 **STDIO 트랜스포트**를 사용하므로 특별한 로깅 전략이 필요합니다:

- **stdout**: MCP 프로토콜 통신 전용 (JSON 메시지만 출력)
- **stderr**: 모든 로그 메시지 출력 (애플리케이션, HTTP, 에러)

### 9.2 로깅 구성

#### Logback 설정 (logback.xml)
```xml
<appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
    <target>System.err</target>
    ...
</appender>
```

#### Ktor HTTP 로깅 (DoorayHttpClient.kt)
```kotlin
install(Logging) {
    logger = object : Logger {
        override fun log(message: String) {
            log.debug("HTTP: $message")  // SLF4J로 라우팅 → stderr
        }
    }
    level = when (System.getenv("DOORAY_HTTP_LOG_LEVEL")?.uppercase()) {
        "ALL" -> LogLevel.ALL
        "HEADERS" -> LogLevel.HEADERS
        "BODY" -> LogLevel.BODY
        "INFO" -> LogLevel.INFO
        else -> LogLevel.NONE  // 기본값
    }
}
```

#### java.util.logging 리디렉션 (Main.kt)
```kotlin
private fun configureSystemLogging() {
    // SLF4J Simple Logger를 stderr로 설정
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.err")
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
    System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS")
}
```

### 9.3 로깅 레벨별 출력 내용

| 레벨 | 출력 내용 |
|------|-----------|
| ERROR | 치명적 오류, API 호출 실패 |
| WARN | 경고, API 응답 에러 |
| INFO | API 요청/응답, 성공 메시지 |
| DEBUG | 상세 정보, HTTP 요청/응답 본문 |

## 10. 테스트 구조

### 10.1 단위 테스트 (17개 테스트)

#### 파일: `McpToolsUnitTest.kt`
- **목적**: 도구 핸들러 로직 테스트 (독립적)
- **테스트 대상**: 5개 도구 (위키 조회, 페이지 조회, 페이지 상세, 페이지 생성, 프로젝트 조회)
- **모킹**: MockK로 DoorayClient 모킹
- **실행**: CI에서 자동 실행

#### 테스트 시나리오
1. **성공 케이스**: 정상 파라미터 → 성공 응답
2. **API 에러 케이스**: Dooray API 에러 응답 → ToolErrorResponse
3. **파라미터 누락 케이스**: 필수 파라미터 누락 → PARAMETER_MISSING 에러

#### 예시
```kotlin
@Test
fun `getWikiPage - 성공`() = runTest {
    // Given: Mock DoorayClient
    coEvery { mockClient.getWikiPage(any(), any()) } returns mockSuccessResponse

    // When: 도구 핸들러 호출
    val result = getWikiPageHandler(mockClient)(request)

    // Then: 성공 응답 검증
    assertThat(result.content[0].text).contains("\"success\":true")
}
```

### 10.2 통합 테스트 (24개 테스트)

#### 구조
- **공통 기반 클래스**: `BaseDoorayIntegrationTest`
  - 환경변수에서 실제 Dooray 인증 정보 로드
  - `DoorayHttpClient` 인스턴스 생성
  - 통합 테스트 스킵 로직 (`@BeforeEach`)

#### 테스트 파일

| 파일 | 테스트 수 | 테스트 대상 |
|------|-----------|-------------|
| `WikiDoorayIntegrationTest.kt` | 6개 | 위키 CRUD 전체 흐름 |
| `ProjectPostDoorayIntegrationTest.kt` | 12개 | 업무 CRUD 및 상태 변경 |
| `ProjectPostCommentsDoorayIntegrationTest.kt` | 6개 | 댓글 CRUD |

#### 실행 조건
- **로컬**: 환경변수 설정 시 실행
- **CI**: `CI=true` 환경변수 감지 시 자동 제외

#### 통합 테스트 예시
```kotlin
@Test
fun `위키 페이지 생성 및 조회`() = runTest {
    // 1. 위키 페이지 생성
    val createRequest = CreateWikiPageRequest(...)
    val createResponse = client.createWikiPage(wikiId, createRequest)
    assertThat(createResponse.header.isSuccessful).isTrue()

    // 2. 생성된 페이지 조회
    val pageId = createResponse.result.id
    val getResponse = client.getWikiPage(wikiId, pageId)
    assertThat(getResponse.result.subject).isEqualTo("Test Page")
}
```

### 10.3 테스트 커버리지 Gap

#### 미테스트 영역
1. **DoorayHttpClient 에러 핸들링 경로**:
   - `handleErrorResponse()` - Dooray API 에러 응답 파싱 실패
   - `handleGenericException()` - 네트워크 타임아웃 등

2. **일부 도구 핸들러 단위 테스트 누락**:
   - `SetProjectPostWorkflowTool`
   - `SetProjectPostDoneTool`
   - `DeletePostCommentTool`

#### 개선 제안
- Ktor MockEngine을 사용한 HTTP 에러 응답 시뮬레이션
- 누락된 도구 핸들러 단위 테스트 추가
- 통합 테스트 자동 클린업 로직 구현 (생성한 데이터 자동 삭제)

## 11. 빌드 및 배포

### 11.1 빌드

#### Gradle 빌드 명령어
```bash
# Fat JAR 빌드
./gradlew clean shadowJar
# 출력: build/libs/dooray-mcp-server-0.2.1-all.jar

# 테스트 실행 (통합 테스트 포함)
./gradlew test

# CI 환경 테스트 (통합 테스트 제외)
CI=true ./gradlew test

# 로컬 실행 (.env 파일 로드)
./gradlew runLocal
```

#### 버전 관리
- **gradle.properties**: `project.version=0.2.1`
- **VersionConst.kt**: `const val VERSION = "0.2.1"`
- **Dockerfile**: `ARG VERSION=0.1.7` (오래된 기본값)

### 11.2 Docker

#### Dockerfile 구조 (Multi-Stage Build)
```dockerfile
# 빌드 스테이지
FROM gradle:8.10-jdk21 AS builder
WORKDIR /app
# Gradle 의존성 및 소스 복사
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src/ src/
# 빌드 (의존성 다운로드 + 컴파일)
RUN ./gradlew clean shadowJar --no-daemon

# 런타임 스테이지
FROM eclipse-temurin:21-jre-alpine
ARG VERSION=0.1.7
ENV APP_VERSION=${VERSION}
# 비루트 사용자 생성 (dooray, UID 1000)
RUN addgroup -g 1000 dooray && \
    adduser -D -s /bin/sh -u 1000 -G dooray dooray
WORKDIR /app
COPY --from=builder /app/build/libs/dooray-mcp-server-${VERSION}-all.jar app.jar
USER dooray
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

#### Docker 빌드 및 실행
```bash
# 로컬 빌드
docker build -t dooray-mcp:local --build-arg VERSION=0.2.1 .

# Docker Hub에서 가져오기
docker pull bifos/dooray-mcp:latest

# 실행
docker run -e DOORAY_API_KEY="your_key" \
           -e DOORAY_BASE_URL="https://api.dooray.com" \
           bifos/dooray-mcp:latest
```

#### Docker 이미지 최적화
- **멀티 스테이지 빌드**: 빌드 도구 제외, 런타임만 포함
- **Alpine JRE**: 경량 이미지 (약 200MB)
- **비루트 사용자**: 보안 강화
- **헬스체크**: 프로세스 존재 여부 확인

### 11.3 CI/CD

#### 파이프라인 구조

##### PR 파이프라인 (`.github/workflows/pr.yml`)
```yaml
on: [pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - Checkout 코드
      - JDK 21 설정
      - Gradle 캐시 설정
      - ./gradlew test (통합 테스트 제외)
      - 테스트 결과 요약 출력
```

##### Main 파이프라인 (`.github/workflows/main.yml`)
```yaml
on:
  push:
    branches: [main]
jobs:
  test:
    # PR 파이프라인과 동일

  docker-build-and-push:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - Checkout 코드
      - Docker Buildx 설정 (멀티 플랫폼)
      - Docker Hub 로그인
      - 버전 추출 (gradle.properties)
      - Docker 빌드 및 푸시 (amd64 + arm64)
        - 태그: latest, 0.2.1
```

#### CI/CD 특징
- **PR 검증**: 코드 변경 시 자동 테스트
- **Main 배포**: 테스트 통과 후 Docker 이미지 자동 빌드 및 푸시
- **멀티 플랫폼**: AMD64만 활성화 (ARM64는 일시 비활성화)
- **버전 자동 추출**: gradle.properties에서 버전 읽어서 Docker 태그 생성

#### ARM64 빌드 이슈
현재 ARM64 빌드는 QEMU 에뮬레이션에서 Gradle 의존성 다운로드 단계가 멈추는 문제로 비활성화되어 있습니다.

**해결 방법**:
1. 네이티브 ARM64 러너 사용 (GitHub Actions self-hosted runner)
2. QEMU 타임아웃 증가
3. Gradle 캐시 최적화
4. 의존성 사전 다운로드

## 12. 발견된 이슈 및 개선 포인트

### 12.1 문서/설정 불일치

#### 1. README 도구 수 불일치
- **문제**: README에 "총 19개 도구" 기재, 실제로는 16개 등록
- **원인**: README의 위키 도구를 8개로 세었으나 실제로는 5개만 등록
  - `dooray_wiki_update_page_title` (README에만 존재)
  - `dooray_wiki_update_page_content` (README에만 존재)
  - `dooray_wiki_update_page_referrers` (README에만 존재)
- **영향**: 사용자 혼란
- **수정 방안**: README를 실제 등록된 16개 도구와 일치시키기

#### 2. Dockerfile VERSION 기본값 오래됨
- **문제**: `ARG VERSION=0.1.7` (현재 버전: 0.2.1)
- **영향**: `--build-arg` 없이 빌드 시 실패 (JAR 파일 경로 불일치)
- **수정 방안**: `ARG VERSION=0.2.1`로 업데이트

#### 3. .cursor/rules SDK 버전 오래됨
- **문제**: SDK 버전 0.5.0 기재 (현재: 0.6.0)
- **영향**: 문서 신뢰도 하락
- **수정 방안**: 0.6.0으로 업데이트

### 12.2 코드 품질

#### 4. 버전 이중 관리
- **문제**: `gradle.properties`와 `VersionConst.kt`에서 버전을 별도 관리
- **리스크**: 버전 불일치 가능성
- **수정 방안**: 빌드 시 gradle.properties에서 VersionConst.kt를 자동 생성
```kotlin
// build.gradle.kts
tasks.register("generateVersionConstants") {
    val version = project.version.toString()
    val file = file("src/main/kotlin/.../VersionConst.kt")
    file.writeText("""
        package com.bifos.dooray.mcp.constants
        object VersionConst {
            const val VERSION = "$version"
        }
    """.trimIndent())
}
tasks.named("compileKotlin") {
    dependsOn("generateVersionConstants")
}
```

#### 5. 도구 핸들러 보일러플레이트
- **문제**: 16개 핸들러가 ~70% 동일한 구조
  - 파라미터 추출 로직 반복
  - try-catch 블록 반복
  - ToolException → ToolErrorResponse 변환 반복
- **수정 방안**: 고차 함수로 추상화
```kotlin
fun <P, R> createToolHandler(
    paramExtractor: (CallToolRequest) -> P,
    paramValidator: (P) -> String?,
    apiCall: suspend (DoorayClient, P) -> R,
    responseMapper: (R) -> JsonElement
): suspend (CallToolRequest) -> CallToolResult {
    return { request ->
        try {
            val params = paramExtractor(request)
            val validationError = paramValidator(params)
            if (validationError != null) {
                // 에러 응답 생성
            } else {
                val result = apiCall(doorayClient, params)
                val data = responseMapper(result)
                // 성공 응답 생성
            }
        } catch (e: Exception) {
            // 예외 처리
        }
    }
}
```

#### 6. JsonUtils.parseStringArray() 사일런트 실패
- **문제**: 파싱 실패 시 빈 리스트 반환, 로깅 없음
```kotlin
fun parseStringArray(jsonArrayString: String): List<String> {
    return try {
        val jsonArray = json.parseToJsonElement(jsonArrayString) as JsonArray
        jsonArray.map { it.jsonPrimitive.content }
    } catch (e: Exception) {
        // 파싱 실패 시 빈 리스트 반환 - 로깅 없음!
        emptyList()
    }
}
```
- **리스크**: 파싱 오류를 사용자가 알 수 없음
- **수정 방안**: 로깅 추가 또는 예외 발생
```kotlin
fun parseStringArray(jsonArrayString: String): List<String> {
    return try {
        val jsonArray = json.parseToJsonElement(jsonArrayString) as JsonArray
        jsonArray.map { it.jsonPrimitive.content }
    } catch (e: Exception) {
        log.warn("JSON 배열 파싱 실패: $jsonArrayString", e)
        emptyList()
    }
}
```

### 12.3 기능 미완성

#### 7. DoorayClient 미사용 메서드
- **문제**: 인터페이스에 정의되었으나 MCP 도구로 노출되지 않은 메서드
  - `updatePostUserWorkflow()` - 특정 담당자의 상태 변경
  - `setPostParent()` - 상위 업무 설정
- **영향**: 인터페이스 불일치, 코드 복잡도 증가
- **수정 방안**:
  1. MCP 도구로 노출
  2. 불필요하면 인터페이스에서 제거

#### 8. 통합 테스트 클린업 미자동화
- **문제**: 통합 테스트가 생성한 위키 페이지, 업무, 댓글을 수동으로 삭제해야 함
- **리스크**: 테스트 데이터 누적, Dooray 프로젝트 오염
- **수정 방안**: `@AfterEach`에서 자동 클린업
```kotlin
@AfterEach
fun cleanup() = runTest {
    createdPageIds.forEach { pageId ->
        client.deleteWikiPage(wikiId, pageId)
    }
    createdPageIds.clear()
}
```

### 12.4 안정성/성능

#### 9. HTTP 재시도/Rate Limiting 로직 없음
- **문제**: 일시적 네트워크 오류나 Dooray API Rate Limit 시 즉시 실패
- **영향**: 안정성 저하
- **수정 방안**: Ktor Retry Plugin 추가
```kotlin
install(HttpRequestRetry) {
    retryOnServerErrors(maxRetries = 3)
    exponentialDelay()
}
```

#### 10. HttpClient 라이프사이클 미관리
- **문제**: `DoorayHttpClient` 생성 시 HttpClient를 생성하지만 `close()` 미호출
- **영향**: 리소스 누수 가능성
- **수정 방안**: `DoorayHttpClient`를 `Closeable` 구현
```kotlin
class DoorayHttpClient(...) : DoorayClient, Closeable {
    override fun close() {
        httpClient.close()
    }
}

// Main.kt
fun main() {
    DoorayHttpClient(...).use { client ->
        DoorayMcpServer(client).initServer()
    }
}
```

#### 11. ARM64 Docker 빌드 QEMU 에뮬레이션 이슈
- **문제**: ARM64 빌드가 Gradle 의존성 다운로드 단계에서 멈춤
- **현재 해결책**: AMD64만 빌드 (ARM64 일시 비활성화)
- **장기 해결책**: 네이티브 ARM64 러너 사용 또는 캐시 최적화

### 12.5 개선 제안 요약

| 우선순위 | 항목 | 예상 공수 | 영향도 |
|----------|------|-----------|--------|
| 높음 | README 도구 목록 수정 | 1시간 | 사용자 혼란 해소 |
| 높음 | Dockerfile VERSION 업데이트 | 10분 | 빌드 안정성 |
| 중간 | 버전 단일 소스 (gradle → VersionConst 자동 생성) | 2시간 | 유지보수성 |
| 중간 | 도구 핸들러 공통화 (고차 함수) | 1일 | 코드 품질 |
| 중간 | HTTP 재시도 로직 추가 | 3시간 | 안정성 |
| 낮음 | 미사용 API 메서드 처리 | 4시간 | 코드 정리 |
| 낮음 | 통합 테스트 자동 클린업 | 4시간 | 테스트 품질 |
| 낮음 | HttpClient 라이프사이클 관리 | 2시간 | 리소스 관리 |

---

## 결론

dooray-mcp-server는 명확한 3계층 아키텍처와 일관된 설계 패턴을 통해 Dooray API를 MCP 도구로 성공적으로 노출하는 프로젝트입니다. Kotlin/JVM 생태계의 현대적인 도구(Ktor, kotlinx.serialization)를 활용하여 타입 안전성과 코드 가독성을 확보했으며, Docker 및 CI/CD 파이프라인을 통해 자동화된 빌드 및 배포를 지원합니다.

주요 강점:
- 명확한 계층 분리와 의존성 방향
- Template Method, Factory Function 등 효과적인 디자인 패턴 활용
- STDIO 트랜스포트 특성을 고려한 로깅 전략
- 단위 테스트와 통합 테스트의 적절한 분리

개선 영역:
- 문서/설정 불일치 해소
- 보일러플레이트 코드 추상화
- 안정성 강화 (재시도, 리소스 관리)
- 테스트 커버리지 확대

전반적으로 잘 구조화된 프로젝트이며, 위 개선 사항을 단계적으로 적용하면 더욱 견고하고 유지보수하기 쉬운 코드베이스가 될 것입니다.
