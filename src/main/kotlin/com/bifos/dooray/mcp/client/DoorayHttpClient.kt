package com.bifos.dooray.mcp.client

import com.bifos.dooray.mcp.constants.EnvVariableConst.DOORAY_PRIORITY_PROJECT_CODES
import com.bifos.dooray.mcp.exception.CustomException
import com.bifos.dooray.mcp.types.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class DoorayHttpClient(private val baseUrl: String, private val doorayApiKey: String) :
        DoorayClient {

    companion object {
        private const val MAX_CACHE_SIZE = 500
        private const val PRIVATE_PROJECT_PLACEHOLDER = "__PRIVATE__"
        private const val DEFAULT_PRIORITY_PROJECT_CODES =
            "웹보드개발랩-전체공유,한게임포커통합-업데이트관리,$PRIVATE_PROJECT_PLACEHOLDER,포커클래식-QA,pc포커-bts"
    }

    private val log = LoggerFactory.getLogger(DoorayHttpClient::class.java)
    private val httpClient: HttpClient
    // 파일(multipart) 업로드 전용 클라이언트 (JSON 기본 Content-Type 미적용)
    private val fileHttpClient: HttpClient

    // post_id -> project_id 매핑 캐시
    private val postProjectIdCache = ConcurrentHashMap<String, String>()
    // page_id -> wiki_id 매핑 캐시
    private val pageWikiIdCache = ConcurrentHashMap<String, String>()

    init {
        httpClient = initHttpClient()
        fileHttpClient = initFileHttpClient()
    }

    private fun initHttpClient(): HttpClient {
        return HttpClient {
            defaultRequest {
                url(baseUrl)
                header("Authorization", "dooray-api $doorayApiKey")
                contentType(ContentType.Application.Json)
            }

            // install content negotiation plugin for JSON serialization/deserialization
            install(ContentNegotiation) {
                json(
                        Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                        }
                )
            }

            // HTTP 요청/응답 로깅 활성화 (SLF4J 사용, stdout 오염 방지)
            install(Logging) {
                logger =
                        object : Logger {
                            override fun log(message: String) {
                                log.debug("HTTP: $message")
                            }
                        }
                // 환경변수로 로깅 레벨 제어 (기본: NONE, 디버깅시: INFO)
                level =
                        when (System.getenv("DOORAY_HTTP_LOG_LEVEL")?.uppercase()) {
                            "ALL" -> LogLevel.ALL
                            "HEADERS" -> LogLevel.HEADERS
                            "BODY" -> LogLevel.BODY
                            "INFO" -> LogLevel.INFO
                            else -> LogLevel.NONE // 기본값: 로깅 비활성화
                        }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 10_000
            }
        }
    }

    private fun initFileHttpClient(): HttpClient {
        // 파일 전용 클라이언트: 기본 Content-Type(JSON)을 걸지 않아
        // MultiPartFormDataContent 가 자신의 multipart/form-data 경계를 그대로 사용하게 한다.
        // baseUrl 을 기본값으로 두지 않고 호출부에서 file-api 절대 URL 을 직접 지정한다.
        return HttpClient {
            defaultRequest { header("Authorization", "dooray-api $doorayApiKey") }

            install(ContentNegotiation) {
                json(
                        Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                        }
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    /**
     * 파일 업로드 URL 을 구성한다.
     * 파일 업로드는 api 호스트가 아니라 file-api 호스트의 /uploads 경로로 라우팅된다.
     * (api.dooray.com 에 직접 POST 하면 307 로 이 Location 을 알려준다 — 공식 API 문서 참고)
     */
    private fun buildUploadUrl(path: String): String {
        val trimmed = baseUrl.trimEnd('/')
        val fileHost = trimmed.replaceFirst("://api.", "://file-api.")
        if (fileHost == trimmed) {
            log.warn(
                    "파일 업로드 호스트 치환이 적용되지 않았습니다(baseUrl=$trimmed). " +
                            "api.dooray.com 형식이 아니면 업로드가 실패(307)할 수 있습니다."
            )
        }
        return "$fileHost/uploads$path"
    }

    /**
     * multipart Content-Disposition 의 파일명 파라미터를 구성한다.
     * Dooray 서버는 filename="..." 의 UTF-8 값을 그대로 보존하며 RFC 5987 filename* 는
     * 지원하지 않는다(단독 지정 시 업로드 실패, 병기 시 무시 — 실측 확인). 따라서 UTF-8
     * 파일명은 유지하되 헤더를 깨뜨리는 따옴표/역슬래시/제어문자만 '_' 로 치환한다.
     */
    private fun fileNameDispositionParams(fileName: String): String {
        val safe = buildString {
            for (c in fileName) {
                when {
                    c == '"' || c == '\\' -> append('_')
                    c.code < 0x20 -> append('_')
                    else -> append(c)
                }
            }
        }
        return "filename=\"$safe\""
    }

    /**
     * API 호출을 공통 템플릿으로 처리합니다.
     * @param operation API 요청 설명 (로깅용)
     * @param expectedStatusCode 성공으로 간주할 HTTP 상태 코드
     * @param successMessage 성공 시 로깅할 메시지 (null이면 기본 메시지)
     * @param apiCall 실제 HTTP 호출을 수행하는 lambda
     */
    private suspend inline fun <reified T> executeApiCall(
            operation: String,
            expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
            successMessage: String? = null,
            crossinline apiCall: suspend () -> HttpResponse
    ): T {
        try {
            log.info("🔗 API 요청: $operation")
            val response = apiCall()
            log.info("📡 응답 수신: ${response.status} ${response.status.description}")

            return when (response.status) {
                expectedStatusCode -> {
                    val result = response.body<T>()
                    log.info(successMessage ?: "✅ API 호출 성공")
                    result
                }
                else -> {
                    handleErrorResponse(response)
                }
            }
        } catch (e: CustomException) {
            throw e
        } catch (e: Exception) {
            handleGenericException(e)
        }
    }

    /** 에러 응답을 공통으로 처리합니다. */
    private suspend fun handleErrorResponse(response: HttpResponse): Nothing {
        val responseBody = response.bodyAsText()
        log.error("❌ API 오류 응답:")
        log.error("  상태 코드: ${response.status.value} ${response.status.description}")
        log.error("  응답 본문: $responseBody")

        try {
            val errorResponse = response.body<DoorayErrorResponse>()
            val errorMessage = "API 호출 실패: ${errorResponse.header.resultMessage}"
            throw CustomException(errorMessage, response.status.value)
        } catch (parseException: Exception) {
            val errorMessage = "API 응답 파싱 실패 (${response.status.value}): $responseBody"
            throw CustomException(errorMessage, response.status.value, parseException)
        }
    }

    /** 일반 예외를 공통으로 처리합니다. */
    private fun handleGenericException(e: Exception): Nothing {
        log.error("❌ 네트워크 또는 기타 오류:")
        log.error("  타입: ${e::class.simpleName}")
        log.error("  메시지: ${e.message}")
        log.error("스택 트레이스:", e)

        val errorMessage = "API 호출 중 오류 발생: ${e.message}"
        throw CustomException(errorMessage, null, e)
    }

    /** result가 null일 수 있는 API 호출을 위한 특별 처리 */
    private suspend fun executeApiCallForNullableResult(
            operation: String,
            expectedStatusCode: HttpStatusCode = HttpStatusCode.OK,
            successMessage: String,
            apiCall: suspend () -> HttpResponse
    ): DoorayApiUnitResponse {
        try {
            log.info("🔗 API 요청: $operation")
            val response = apiCall()
            log.info("📡 응답 수신: ${response.status} ${response.status.description}")

            return when (response.status) {
                expectedStatusCode -> {
                    // result가 null일 수 있는 응답을 파싱
                    val jsonResponse = response.body<DoorayApiUnitResponse>()
                    if (jsonResponse.header.isSuccessful) {
                        log.info(successMessage)
                    } else {
                        log.warn("⚠️ API 응답 에러: ${jsonResponse.header.resultMessage}")
                    }
                    jsonResponse
                }
                else -> {
                    handleErrorResponse(response)
                }
            }
        } catch (e: CustomException) {
            throw e
        } catch (e: Exception) {
            handleGenericException(e)
        }
    }

    override suspend fun getWikis(page: Int?, size: Int?): WikiListResponse {
        return executeApiCall(operation = "GET /wiki/v1/wikis", successMessage = "✅ 위키 목록 조회 성공") {
            httpClient.get("/wiki/v1/wikis") {
                page?.let { parameter("page", it) }
                size?.let { parameter("size", it) }
            }
        }
    }

    override suspend fun getWikiPages(projectId: String): WikiPagesResponse {
        return executeApiCall(
                operation = "GET /wiki/v1/wikis/$projectId/pages",
                successMessage = "✅ 위키 페이지 목록 조회 성공"
        ) { httpClient.get("/wiki/v1/wikis/$projectId/pages") }
    }

    override suspend fun getWikiPages(projectId: String, parentPageId: String): WikiPagesResponse {
        return executeApiCall(
                operation = "GET /wiki/v1/wikis/$projectId/pages?parentPageId=$parentPageId",
                successMessage = "✅ 자식 위키 페이지 목록 조회 성공"
        ) {
            httpClient.get("/wiki/v1/wikis/$projectId/pages") {
                parameter("parentPageId", parentPageId)
            }
        }
    }

    override suspend fun getWikiPage(projectId: String, pageId: String): WikiPageResponse {
        return executeApiCall(
                operation = "GET /wiki/v1/wikis/$projectId/pages/$pageId",
                successMessage = "✅ 위키 페이지 조회 성공"
        ) { httpClient.get("/wiki/v1/wikis/$projectId/pages/$pageId") }
    }

    override suspend fun createWikiPage(
            wikiId: String,
            request: CreateWikiPageRequest
    ): CreateWikiPageResponse {
        return executeApiCall(
                operation = "POST /wiki/v1/wikis/$wikiId/pages",
                expectedStatusCode = HttpStatusCode.Created,
                successMessage = "✅ 위키 페이지 생성 성공"
        ) { httpClient.post("/wiki/v1/wikis/$wikiId/pages") { setBody(request) } }
    }

    override suspend fun updateWikiPage(
            wikiId: String,
            pageId: String,
            request: UpdateWikiPageRequest
    ): DoorayApiUnitResponse {
        return executeApiCallForNullableResult(
                operation = "PUT /wiki/v1/wikis/$wikiId/pages/$pageId",
                successMessage = "✅ 위키 페이지 수정 성공"
        ) { httpClient.put("/wiki/v1/wikis/$wikiId/pages/$pageId") { setBody(request) } }
    }

    // ============ 프로젝트 업무 관련 API 구현 ============

    override suspend fun createPost(
            projectId: String,
            request: CreatePostRequest
    ): CreatePostApiResponse {
        return executeApiCall(
                operation = "POST /project/v1/projects/$projectId/posts",
                expectedStatusCode = HttpStatusCode.OK,
                successMessage = "✅ 업무 생성 성공"
        ) { httpClient.post("/project/v1/projects/$projectId/posts") { setBody(request) } }
    }

    override suspend fun getPosts(
            projectId: String,
            page: Int?,
            size: Int?,
            fromMemberIds: List<String>?,
            toMemberIds: List<String>?,
            ccMemberIds: List<String>?,
            tagIds: List<String>?,
            parentPostId: String?,
            postNumber: String?,
            postWorkflowClasses: List<String>?,
            postWorkflowIds: List<String>?,
            milestoneIds: List<String>?,
            subjects: String?,
            createdAt: String?,
            updatedAt: String?,
            dueAt: String?,
            order: String?
    ): PostListResponse {
        return executeApiCall(
                operation = "GET /project/v1/projects/$projectId/posts",
                successMessage = "✅ 업무 목록 조회 성공"
        ) {
            httpClient.get("/project/v1/projects/$projectId/posts") {
                page?.let { parameter("page", it) }
                size?.let { parameter("size", it) }
                fromMemberIds?.let {
                    if (it.isNotEmpty()) parameter("fromMemberIds", it.joinToString(","))
                }
                toMemberIds?.let {
                    if (it.isNotEmpty()) parameter("toMemberIds", it.joinToString(","))
                }
                ccMemberIds?.let {
                    if (it.isNotEmpty()) parameter("ccMemberIds", it.joinToString(","))
                }
                tagIds?.let { if (it.isNotEmpty()) parameter("tagIds", it.joinToString(",")) }
                parentPostId?.let { parameter("parentPostId", it) }
                postNumber?.let { parameter("postNumber", it) }
                postWorkflowClasses?.let {
                    if (it.isNotEmpty()) parameter("postWorkflowClasses", it.joinToString(","))
                }
                postWorkflowIds?.let {
                    if (it.isNotEmpty()) parameter("postWorkflowIds", it.joinToString(","))
                }
                milestoneIds?.let {
                    if (it.isNotEmpty()) parameter("milestoneIds", it.joinToString(","))
                }
                subjects?.let { parameter("subjects", it) }
                createdAt?.let { parameter("createdAt", it) }
                updatedAt?.let { parameter("updatedAt", it) }
                dueAt?.let { parameter("dueAt", it) }
                order?.let { parameter("order", it) }
            }
        }
    }

    override suspend fun getPost(projectId: String, postId: String): PostDetailResponse {
        return executeApiCall(
                operation = "GET /project/v1/projects/$projectId/posts/$postId",
                successMessage = "✅ 업무 상세 조회 성공"
        ) { httpClient.get("/project/v1/projects/$projectId/posts/$postId") }
    }

    override suspend fun updatePost(
            projectId: String,
            postId: String,
            request: UpdatePostRequest
    ): UpdatePostResponse {
        return executeApiCallForNullableResult(
                operation = "PUT /project/v1/projects/$projectId/posts/$postId",
                successMessage = "✅ 업무 수정 성공"
        ) { httpClient.put("/project/v1/projects/$projectId/posts/$postId") { setBody(request) } }
    }

    override suspend fun updatePostUserWorkflow(
            projectId: String,
            postId: String,
            organizationMemberId: String,
            workflowId: String
    ): DoorayApiUnitResponse {
        return executeApiCallForNullableResult(
                operation =
                        "PUT /project/v1/projects/$projectId/posts/$postId/to/$organizationMemberId",
                successMessage = "✅ 담당자 상태 변경 성공"
        ) {
            httpClient.put(
                    "/project/v1/projects/$projectId/posts/$postId/to/$organizationMemberId"
            ) { setBody(SetWorkflowRequest(workflowId)) }
        }
    }

    override suspend fun setPostWorkflow(
            projectId: String,
            postId: String,
            workflowId: String
    ): DoorayApiUnitResponse {
        return executeApiCallForNullableResult(
                operation = "POST /project/v1/projects/$projectId/posts/$postId/set-workflow",
                successMessage = "✅ 업무 상태 변경 성공"
        ) {
            httpClient.post("/project/v1/projects/$projectId/posts/$postId/set-workflow") {
                setBody(SetWorkflowRequest(workflowId))
            }
        }
    }

    override suspend fun setPostDone(projectId: String, postId: String): DoorayApiUnitResponse {
        return executeApiCallForNullableResult(
                operation = "POST /project/v1/projects/$projectId/posts/$postId/set-done",
                successMessage = "✅ 업무 완료 처리 성공"
        ) { httpClient.post("/project/v1/projects/$projectId/posts/$postId/set-done") }
    }

    override suspend fun setPostParent(
            projectId: String,
            postId: String,
            parentPostId: String
    ): DoorayApiUnitResponse {
        return executeApiCallForNullableResult(
                operation = "POST /project/v1/projects/$projectId/posts/$postId/set-parent-post",
                successMessage = "✅ 상위 업무 설정 성공"
        ) {
            httpClient.post("/project/v1/projects/$projectId/posts/$postId/set-parent-post") {
                setBody(SetParentPostRequest(parentPostId))
            }
        }
    }

    override suspend fun movePost(
            projectId: String,
            postId: String,
            targetProjectId: String?,
            includeSubPosts: Boolean
    ): MovePostResponse {
        return executeApiCall(
                operation = "POST /project/v1/projects/$projectId/posts/$postId/move",
                successMessage = "✅ 업무 이동 성공"
        ) {
            httpClient.post("/project/v1/projects/$projectId/posts/$postId/move") {
                setBody(MovePostRequest(targetProjectId, includeSubPosts))
            }
        }
    }

    override suspend fun getPostById(postId: String): PostByIdResponse {
        return executeApiCall(
                operation = "GET /project/v1/posts/$postId",
                successMessage = "✅ 업무 단건 조회 성공"
        ) { httpClient.get("/project/v1/posts/$postId") }
    }

    // ============ 업무 첨부파일 관련 API 구현 ============

    override suspend fun uploadPostFile(
            projectId: String,
            postId: String,
            fileName: String,
            fileBytes: ByteArray,
            mimeType: String?
    ): UploadPostFileResponse {
        val uploadUrl = buildUploadUrl("/project/v1/projects/$projectId/posts/$postId/files")
        return executeApiCall(
                operation = "POST $uploadUrl",
                successMessage = "✅ 업무 첨부파일 업로드 성공"
        ) {
            fileHttpClient.post(uploadUrl) {
                setBody(
                        MultiPartFormDataContent(
                                formData {
                                    append(
                                            "file",
                                            fileBytes,
                                            Headers.build {
                                                append(
                                                        HttpHeaders.ContentDisposition,
                                                        fileNameDispositionParams(fileName)
                                                )
                                                append(
                                                        HttpHeaders.ContentType,
                                                        mimeType ?: "application/octet-stream"
                                                )
                                            }
                                    )
                                }
                        )
                )
            }
        }
    }

    override suspend fun getPostFiles(projectId: String, postId: String): PostFileListResponse {
        return executeApiCall(
                operation = "GET /project/v1/projects/$projectId/posts/$postId/files",
                successMessage = "✅ 업무 첨부파일 목록 조회 성공"
        ) { httpClient.get("/project/v1/projects/$projectId/posts/$postId/files") }
    }

    override suspend fun deletePostFile(
            projectId: String,
            postId: String,
            fileId: String
    ): DeletePostFileResponse {
        return executeApiCallForNullableResult(
                operation =
                        "DELETE /project/v1/projects/$projectId/posts/$postId/files/$fileId",
                successMessage = "✅ 업무 첨부파일 삭제 성공"
        ) {
            httpClient.delete("/project/v1/projects/$projectId/posts/$postId/files/$fileId")
        }
    }

    // ============ 업무 댓글 관련 API 구현 ============

    override suspend fun createPostComment(
            projectId: String,
            postId: String,
            request: CreateCommentRequest
    ): CreateCommentApiResponse {
        return executeApiCall(
                operation = "POST /project/v1/projects/$projectId/posts/$postId/logs",
                successMessage = "✅ 업무 댓글 생성 성공"
        ) {
            httpClient.post("/project/v1/projects/$projectId/posts/$postId/logs") {
                setBody(request)
            }
        }
    }

    override suspend fun getPostComments(
            projectId: String,
            postId: String,
            page: Int?,
            size: Int?,
            order: String?
    ): PostCommentListResponse {
        return executeApiCall(
                operation = "GET /project/v1/projects/$projectId/posts/$postId/logs",
                successMessage = "✅ 업무 댓글 목록 조회 성공"
        ) {
            httpClient.get("/project/v1/projects/$projectId/posts/$postId/logs") {
                page?.let { parameter("page", it) }
                size?.let { parameter("size", it) }
                order?.let { parameter("order", it) }
            }
        }
    }

    override suspend fun getPostComment(
            projectId: String,
            postId: String,
            logId: String
    ): PostCommentDetailResponse {
        return executeApiCall(
                operation = "GET /project/v1/projects/$projectId/posts/$postId/logs/$logId",
                successMessage = "✅ 업무 댓글 상세 조회 성공"
        ) { httpClient.get("/project/v1/projects/$projectId/posts/$postId/logs/$logId") }
    }

    override suspend fun updatePostComment(
            projectId: String,
            postId: String,
            logId: String,
            request: UpdateCommentRequest
    ): UpdateCommentResponse {
        return executeApiCallForNullableResult(
                operation = "PUT /project/v1/projects/$projectId/posts/$postId/logs/$logId",
                successMessage = "✅ 업무 댓글 수정 성공"
        ) {
            httpClient.put("/project/v1/projects/$projectId/posts/$postId/logs/$logId") {
                setBody(request)
            }
        }
    }

    override suspend fun deletePostComment(
            projectId: String,
            postId: String,
            logId: String
    ): DeleteCommentResponse {
        return executeApiCallForNullableResult(
                operation = "DELETE /project/v1/projects/$projectId/posts/$postId/logs/$logId",
                successMessage = "✅ 업무 댓글 삭제 성공"
        ) { httpClient.delete("/project/v1/projects/$projectId/posts/$postId/logs/$logId") }
    }

    // ============ 프로젝트 관련 API 구현 ============

    override suspend fun getProjects(
            page: Int?,
            size: Int?,
            type: String?,
            scope: String?,
            state: String?
    ): ProjectListResponse {
        return executeApiCall(
                operation = "GET /project/v1/projects",
                successMessage = "✅ 프로젝트 목록 조회 성공"
        ) {
            httpClient.get("/project/v1/projects") {
                parameter("member", "me")
                page?.let { parameter("page", it) }
                size?.let { parameter("size", it) }
                type?.let { parameter("type", it) }
                scope?.let { parameter("scope", it) }
                state?.let { parameter("state", it) }
            }
        }
    }

    // ============ 우선순위 프로젝트 정렬 헬퍼 ============

    private fun getPriorityProjectCodes(): List<String> {
        val env = System.getenv(DOORAY_PRIORITY_PROJECT_CODES)?.trim()?.ifEmpty { null }
        return (env ?: DEFAULT_PRIORITY_PROJECT_CODES)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun <T> sortByPriority(
        items: List<T>,
        priorityCodes: List<String>,
        getCode: (T) -> String,
        getType: (T) -> String?
    ): List<T> {
        val priorityMap = mutableMapOf<Int, MutableList<T>>()
        val remaining = mutableListOf<T>()

        for (item in items) {
            val code = getCode(item)
            val type = getType(item)
            val priorityIndex = priorityCodes.indexOfFirst { entry ->
                if (entry == PRIVATE_PROJECT_PLACEHOLDER) type == "private"
                else entry == code
            }
            if (priorityIndex >= 0) {
                priorityMap.getOrPut(priorityIndex) { mutableListOf() }.add(item)
            } else {
                remaining.add(item)
            }
        }

        val sorted = mutableListOf<T>()
        for (i in priorityCodes.indices) {
            priorityMap[i]?.let { sorted.addAll(it) }
        }
        sorted.addAll(remaining)
        return sorted
    }

    private suspend fun fetchAllProjects(): List<Project> {
        // Dooray /project/v1/projects 는 type 미지정 시 개인(private) 프로젝트를 제외한다(실측).
        // 개인 프로젝트(@내계정)의 업무도 project_id 자동 조회가 가능하도록 기본 + private 를 모두 수집·병합한다.
        val byId = LinkedHashMap<String, Project>()
        val pageSize = 100
        for (type in listOf<String?>(null, "private")) {
            var page = 0
            while (true) {
                val response = getProjects(page = page, size = pageSize, type = type)
                if (!response.header.isSuccessful || response.result.isEmpty()) break
                response.result.forEach { byId[it.id] = it }
                if (response.result.size < pageSize) break
                page++
            }
        }
        return byId.values.toList()
    }

    private suspend fun fetchAllWikis(): List<Wiki> {
        val allWikis = mutableListOf<Wiki>()
        var page = 0
        val pageSize = 200
        while (true) {
            val response = getWikis(page = page, size = pageSize)
            if (!response.header.isSuccessful || response.result.isEmpty()) break
            allWikis.addAll(response.result)
            if (response.result.size < pageSize) break
            page++
        }
        return allWikis
    }

    // ============ ID 자동 조회 (resolve) API 구현 ============

    override suspend fun resolveProjectIdForPost(postId: String): String {
        // 캐시에 있으면 바로 반환
        postProjectIdCache[postId]?.let { return it }

        log.info("🔍 post_id=$postId 에 대한 project_id 자동 조회 시작...")

        return withTimeout(30_000) {
            val allProjects = fetchAllProjects()
            val priorityCodes = getPriorityProjectCodes()
            val sortedProjects = sortByPriority(
                allProjects, priorityCodes,
                getCode = { it.code },
                getType = { it.type }
            )

            log.info("📋 우선순위 적용된 프로젝트 검색 순서: ${sortedProjects.take(5).joinToString { it.code }}")

            for (project in sortedProjects) {
                try {
                    val postResponse = getPost(project.id, postId)
                    if (postResponse.header.isSuccessful && postResponse.result.id == postId) {
                        log.info("✅ post_id=$postId → project_id=${project.id} (${project.code}) 매핑 완료")
                        if (postProjectIdCache.size < MAX_CACHE_SIZE) {
                            postProjectIdCache[postId] = project.id
                        }
                        return@withTimeout project.id
                    }
                } catch (e: Exception) {
                    // 해당 프로젝트에 업무가 없으면 다음 프로젝트로
                    log.debug("프로젝트 ${project.id}(${project.code})에서 업무 $postId 미발견")
                }
            }

            throw CustomException("post_id=$postId 에 해당하는 프로젝트를 찾을 수 없습니다. 접근 권한이 있는 프로젝트에 해당 업무가 존재하는지 확인하세요.", null)
        }
    }

    override suspend fun resolveWikiIdForPage(pageId: String): String {
        // 캐시에 있으면 바로 반환
        pageWikiIdCache[pageId]?.let { return it }

        log.info("🔍 page_id=$pageId 에 대한 wiki_id 자동 조회 시작...")

        return withTimeout(30_000) {
            val allWikis = fetchAllWikis()
            val priorityCodes = getPriorityProjectCodes()
            val sortedWikis = sortByPriority(
                allWikis, priorityCodes,
                getCode = { it.name },
                getType = { it.type }
            )

            log.info("📋 우선순위 적용된 위키 검색 순서: ${sortedWikis.take(5).joinToString { it.name }}")

            for (wiki in sortedWikis) {
                try {
                    val wikiPageResponse = getWikiPage(wiki.id, pageId)
                    if (wikiPageResponse.header.isSuccessful && wikiPageResponse.result.id == pageId) {
                        log.info("✅ page_id=$pageId → wiki_id=${wiki.id} (${wiki.name}) 매핑 완료")
                        if (pageWikiIdCache.size < MAX_CACHE_SIZE) {
                            pageWikiIdCache[pageId] = wiki.id
                        }
                        return@withTimeout wiki.id
                    }
                } catch (e: Exception) {
                    log.debug("위키 ${wiki.id}(${wiki.name})에서 페이지 $pageId 미발견")
                }
            }

            throw CustomException("page_id=$pageId 에 해당하는 위키를 찾을 수 없습니다. 접근 권한이 있는 위키에 해당 페이지가 존재하는지 확인하세요.", null)
        }
    }
}
