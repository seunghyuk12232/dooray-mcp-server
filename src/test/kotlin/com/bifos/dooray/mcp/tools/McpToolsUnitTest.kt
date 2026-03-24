package com.bifos.dooray.mcp.tools

import com.bifos.dooray.mcp.client.DoorayClient
import com.bifos.dooray.mcp.types.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertContains
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpToolsUnitTest {

    @Test
    @DisplayName("위키 목록 조회 도구 - 성공 케이스")
    fun testGetWikisHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockWikis =
            listOf(
                Wiki(
                    id = "wiki1",
                    project = WikiProject(id = "project1"),
                    name = "테스트 위키",
                    type = "wiki",
                    scope = "private",
                    home = WikiHome(pageId = "home1")
                )
            )
        val mockResponse =
            WikiListResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = mockWikis
            )

        coEvery { mockDoorayClient.getWikis(any(), any()) } returns mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("page", 0)
                    put("size", 10)
                }

        // when
        val handler = getWikisHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "테스트 위키")
    }

    @Test
    @DisplayName("위키 목록 조회 도구 - API 에러 케이스")
    fun testGetWikisHandlerApiError() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockResponse =
            WikiListResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = false,
                        resultCode = 400,
                        resultMessage = "Bad Request"
                    ),
                result = emptyList()
            )

        coEvery { mockDoorayClient.getWikis(any(), any()) } returns mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns buildJsonObject {}

        // when
        val handler = getWikisHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "Bad Request")
    }

    @Test
    @DisplayName("위키 페이지 목록 조회 도구 - 성공 케이스")
    fun testGetWikiPagesHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockPages =
            listOf(
                WikiPage(
                    id = "page1",
                    wikiId = "wiki1",
                    version = 1,
                    root = false,
                    creator =
                        Creator(
                            type = "member",
                            member = Member(organizationMemberId = "member1")
                        ),
                    subject = "테스트 페이지"
                )
            )
        val mockResponse =
            WikiPagesResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = mockPages
            )

        coEvery { mockDoorayClient.getWikiPages(any<String>()) } returns mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns buildJsonObject { put("project_id", "project1") }

        // when
        val handler = getWikiPagesHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "테스트 페이지")
    }

    @Test
    @DisplayName("위키 페이지 목록 조회 도구 - project_id 누락 에러")
    fun testGetWikiPagesHandlerMissingProjectId() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns buildJsonObject {} // project_id 누락

        // when
        val handler = getWikiPagesHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "MISSING_PROJECT_ID")
    }

    @Test
    @DisplayName("프로젝트 목록 조회 도구 - 성공 케이스")
    fun testGetProjectsHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockProjects =
            listOf(
                Project(
                    id = "project1",
                    code = "TEST",
                    description = "테스트용 프로젝트입니다",
                    state = "active",
                    scope = "private",
                    type = "project"
                )
            )
        val mockResponse =
            ProjectListResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = mockProjects,
                totalCount = 1
            )

        coEvery { mockDoorayClient.getProjects(any(), any(), any(), any(), any()) } returns
                mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("page", 0)
                    put("size", 20)
                }

        // when
        val handler = getProjectsHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "TEST")
    }

    @Test
    @DisplayName("프로젝트 업무 목록 조회 도구 - project_id 누락 에러")
    fun testGetProjectPostsHandlerMissingProjectId() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns buildJsonObject {} // project_id 누락

        // when
        val handler = getProjectPostsHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "MISSING_PROJECT_ID")
    }

    @Test
    @DisplayName("프로젝트 업무 목록 조회 도구 - 추가 필터를 모두 전달한다")
    fun testGetProjectPostsHandlerPassesExtendedFilters() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockResponse =
            PostListResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = emptyList(),
                totalCount = 0
            )

        coEvery {
            mockDoorayClient.getPosts(
                projectId = any(),
                page = any(),
                size = any(),
                fromMemberIds = any(),
                toMemberIds = any(),
                ccMemberIds = any(),
                tagIds = any(),
                parentPostId = any(),
                postNumber = any(),
                postWorkflowClasses = any(),
                postWorkflowIds = any(),
                milestoneIds = any(),
                subjects = any(),
                createdAt = any(),
                updatedAt = any(),
                dueAt = any(),
                order = any()
            )
        } returns mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("page", 1)
                    put("size", 50)
                    putJsonArray("from_member_ids") { add("writer1") }
                    putJsonArray("to_member_ids") { add("worker1") }
                    putJsonArray("cc_member_ids") { add("cc1") }
                    putJsonArray("tag_ids") { add("tag1") }
                    put("parent_post_id", "parent1")
                    put("post_number", "POST-1")
                    putJsonArray("post_workflow_classes") { add("working") }
                    putJsonArray("post_workflow_ids") { add("workflow1") }
                    putJsonArray("milestone_ids") { add("milestone1") }
                    put("subjects", "검색어")
                    put("created_at", "2026-03-01")
                    put("updated_at", "2026-03-02")
                    put("due_at", "2026-03-03")
                    put("order", "-postUpdatedAt")
                }

        // when
        val handler = getProjectPostsHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")

        coVerify(exactly = 1) {
            mockDoorayClient.getPosts(
                projectId = "project1",
                page = 1,
                size = 50,
                fromMemberIds = listOf("writer1"),
                toMemberIds = listOf("worker1"),
                ccMemberIds = listOf("cc1"),
                tagIds = listOf("tag1"),
                parentPostId = "parent1",
                postNumber = "POST-1",
                postWorkflowClasses = listOf("working"),
                postWorkflowIds = listOf("workflow1"),
                milestoneIds = listOf("milestone1"),
                subjects = "검색어",
                createdAt = "2026-03-01",
                updatedAt = "2026-03-02",
                dueAt = "2026-03-03",
                order = "-postUpdatedAt"
            )
        }
    }

    @Test
    @DisplayName("프로젝트 업무 목록 조회 도구 - project_id에 Dooray URL이 오면 검증 에러")
    fun testGetProjectPostsHandlerRejectsUrlProjectId() = runTest {
        val mockDoorayClient = mockk<DoorayClient>()

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "https://nhnent.dooray.com/project/tasks/4294538452368557412")
                }

        val handler = getProjectPostsHandler(mockDoorayClient)
        val result = handler(mockRequest)

        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "URL_NOT_ALLOWED_FOR_PROJECT_ID")
    }

    @Test
    @DisplayName("업무 상세 조회 도구 - Dooray URL로 project_id 자동 조회")
    fun testGetProjectPostHandlerResolvesFromDoorayUrl() = runTest {
        val mockDoorayClient = mockk<DoorayClient>()

        coEvery { mockDoorayClient.resolveProjectIdForPost("4285609060273170515") } returns "project1"
        coEvery { mockDoorayClient.getPost("project1", "4285609060273170515") } returns
                PostDetailResponse(
                    header =
                        DoorayApiHeader(
                            isSuccessful = true,
                            resultCode = 0,
                            resultMessage = "success"
                        ),
                    result =
                        PostDetail(
                            id = "4285609060273170515",
                            subject = "테스트 업무",
                            project = ProjectInfo(id = "project1", code = "TEST"),
                            taskNumber = "TEST-123",
                            closed = false,
                            createdAt = "2026-03-24T10:00:00+09:00",
                            updatedAt = "2026-03-24T10:00:00+09:00",
                            number = 123,
                            body = PostBody(mimeType = "text/x-markdown", content = "본문"),
                            users =
                                PostUsers(
                                    from =
                                        PostUser(
                                            type = "member",
                                            member = Member(organizationMemberId = "writer1")
                                        ),
                                    to = emptyList(),
                                    cc = emptyList()
                                ),
                            workflow =
                                Workflow(
                                    id = "workflow1",
                                    name = "진행중"
                                ),
                            workflowClass = "working",
                            priority = "normal",
                            dueDate = null,
                            dueDateFlag = null,
                            parent = null,
                            milestone = null,
                            tags = emptyList(),
                            files = emptyList(),
                            fileIdList = emptyList()
                        )
                )

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("post_id", "https://nhnent.dooray.com/task/to/4285609060273170515?workflowClass=all")
                }

        val handler = getProjectPostHandler(mockDoorayClient)
        val result = handler(mockRequest)

        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "테스트 업무")

        coVerify(exactly = 1) { mockDoorayClient.resolveProjectIdForPost("4285609060273170515") }
        coVerify(exactly = 1) { mockDoorayClient.getPost("project1", "4285609060273170515") }
    }

    @Test
    @DisplayName("위키 페이지 생성 도구 - 성공 케이스")
    fun testCreateWikiPageHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockResponse =
            CreateWikiPageResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result =
                    CreateWikiPageResult(
                        id = "page1",
                        wikiId = "wiki1",
                        parentPageId = "parent1",
                        version = 1
                    )
            )

        coEvery { mockDoorayClient.createWikiPage(any(), any()) } returns mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("wiki_id", "wiki1")
                    put("subject", "새 위키 페이지")
                    put("body", "새 위키 페이지 내용")
                    put("parent_page_id", "parent1")
                }

        // when
        val handler = createWikiPageHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "성공적으로 생성")
    }

    @Test
    @DisplayName("위키 페이지 생성 도구 - wiki_id 누락 에러")
    fun testCreateWikiPageHandlerMissingWikiId() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("subject", "새 위키 페이지")
                    put("body", "새 위키 페이지 내용")
                    put("parent_page_id", "parent1")
                    // wiki_id 누락
                }

        // when
        val handler = createWikiPageHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "MISSING_WIKI_ID")
    }

    @Test
    @DisplayName("업무 생성 도구 - 성공 케이스")
    fun testCreateProjectPostHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockResponse =
            CreatePostApiResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = CreatePostResponse(id = "post1")
            )

        coEvery { mockDoorayClient.createPost(any(), any()) } returns mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("subject", "새 업무")
                    put("body", "새 업무 내용")
                    putJsonArray("to_member_ids") {
                        add("member1")
                        add("member2")
                    }
                }

        // when
        val handler = createProjectPostHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "성공적으로 생성")
    }

    @Test
    @DisplayName("업무 생성 도구 - to_member_ids 누락 에러")
    fun testCreateProjectPostHandlerMissingToMemberIds() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("subject", "새 업무")
                    put("body", "새 업무 내용")
                    // to_member_ids 누락
                }

        // when
        val handler = createProjectPostHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "MISSING_TO_MEMBER_IDS")
    }

    @Test
    @DisplayName("위키 페이지 수정 도구 - 수정할 내용 없음 에러")
    fun testUpdateWikiPageHandlerNoUpdateContent() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("wiki_id", "wiki1")
                    put("page_id", "page1")
                    // subject, body, referrer_member_ids 모두 누락
                }

        // when
        val handler = updateWikiPageHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "NO_UPDATE_CONTENT")
    }

    // === 댓글 관련 도구 테스트 ===

    @Test
    @DisplayName("업무 댓글 목록 조회 도구 - 성공 케이스")
    fun testGetPostCommentsHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockComments =
            listOf(
                PostComment(
                    id = "comment1",
                    post = PostInfo(id = "post1"),
                    type = "comment",
                    subtype = "general",
                    createdAt = "2025-01-25T10:00:00+09:00",
                    modifiedAt = null,
                    creator =
                        PostUser(
                            type = "member",
                            member = Member(organizationMemberId = "member1")
                        ),
                    mailUsers = null,
                    body =
                        PostCommentBody(
                            mimeType = "text/html",
                            content = "테스트 댓글입니다."
                        ),
                    files = null
                )
            )
        val mockResponse =
            PostCommentListResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = mockComments,
                totalCount = 1
            )

        coEvery { mockDoorayClient.getPostComments(any(), any(), any(), any(), any()) } returns
                mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("post_id", "post1")
                    put("page", 0)
                    put("size", 10)
                }

        // when
        val handler = getPostCommentsHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "\"comments\":")
        assertContains(responseText, "\"totalCount\": 1")
        assertContains(responseText, "\"currentPage\": 0")
        assertContains(responseText, "\"pageSize\": 10")
        assertContains(responseText, "테스트 댓글입니다")
    }

    @Test
    @DisplayName("업무 댓글 목록 조회 도구 - project_id 생략 시 자동 조회로 성공")
    fun testGetPostCommentsHandlerAutoResolveProjectId() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        // resolveProjectIdForPost 모킹
        coEvery { mockDoorayClient.resolveProjectIdForPost("post1") } returns "resolved_project1"

        val mockComments =
            listOf(
                PostComment(
                    id = "comment1",
                    post = PostInfo(id = "post1"),
                    type = "comment",
                    subtype = "general",
                    createdAt = "2025-01-25T10:00:00+09:00",
                    modifiedAt = null,
                    creator =
                        PostUser(
                            type = "member",
                            member = Member(organizationMemberId = "member1")
                        ),
                    mailUsers = null,
                    body =
                        PostCommentBody(
                            mimeType = "text/html",
                            content = "테스트 댓글입니다."
                        ),
                    files = null
                )
            )
        val mockResponse =
            PostCommentListResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = mockComments,
                totalCount = 1
            )

        coEvery { mockDoorayClient.getPostComments("resolved_project1", "post1", any(), any(), any()) } returns
                mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("post_id", "post1")
                    // project_id 생략 - 자동 조회
                }

        // when
        val handler = getPostCommentsHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "테스트 댓글입니다")
    }

    @Test
    @DisplayName("업무 댓글 목록 조회 도구 - post_id 누락 에러")
    fun testGetPostCommentsHandlerMissingPostId() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    // post_id 누락
                }

        // when
        val handler = getPostCommentsHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "MISSING_POST_ID")
    }

    @Test
    @DisplayName("업무 댓글 목록 조회 도구 - API 에러 케이스")
    fun testGetPostCommentsHandlerApiError() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockResponse =
            PostCommentListResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = false,
                        resultCode = 404,
                        resultMessage = "Post not found"
                    ),
                result = emptyList(),
                totalCount = 0
            )

        coEvery { mockDoorayClient.getPostComments(any(), any(), any(), any(), any()) } returns
                mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("post_id", "invalid_post_id")
                }

        // when
        val handler = getPostCommentsHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "Post not found")
        assertContains(responseText, "DOORAY_API_404")
    }

    @Test
    @DisplayName("업무 댓글 생성 도구 - 성공 케이스")
    fun testCreatePostCommentHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockResponse =
            CreateCommentApiResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = CreateCommentResponse(id = "comment1")
            )

        coEvery { mockDoorayClient.createPostComment(any(), any(), any()) } returns mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("post_id", "post1")
                    put("content", "새 댓글 내용")
                }

        // when
        val handler = createPostCommentHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "성공적으로 생성")
    }

    @Test
    @DisplayName("업무 댓글 생성 도구 - content 누락 에러")
    fun testCreatePostCommentHandlerMissingContent() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("post_id", "post1")
                    // content 누락
                }

        // when
        val handler = createPostCommentHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"isError\": true")
        assertContains(responseText, "MISSING_CONTENT")
    }

    @Test
    @DisplayName("업무 댓글 수정 도구 - 성공 케이스")
    fun testUpdatePostCommentHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockResponse =
            UpdateCommentResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = null
            )

        coEvery { mockDoorayClient.updatePostComment(any(), any(), any(), any()) } returns
                mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("post_id", "post1")
                    put("log_id", "comment1")
                    put("content", "수정된 댓글 내용")
                }

        // when
        val handler = updatePostCommentHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "성공적으로 수정")
    }

    @Test
    @DisplayName("업무 댓글 삭제 도구 - 성공 케이스")
    fun testDeletePostCommentHandlerSuccess() = runTest {
        // given
        val mockDoorayClient = mockk<DoorayClient>()

        val mockResponse =
            DeleteCommentResponse(
                header =
                    DoorayApiHeader(
                        isSuccessful = true,
                        resultCode = 0,
                        resultMessage = "success"
                    ),
                result = null
            )

        coEvery { mockDoorayClient.deletePostComment(any(), any(), any()) } returns mockResponse

        val mockRequest = mockk<CallToolRequest>()
        every { mockRequest.arguments } returns
                buildJsonObject {
                    put("project_id", "project1")
                    put("post_id", "post1")
                    put("log_id", "comment1")
                }

        // when
        val handler = deletePostCommentHandler(mockDoorayClient)
        val result = handler(mockRequest)

        // then
        assertTrue(result.content.isNotEmpty())
        val content = result.content.first() as TextContent
        val responseText = content.text ?: ""
        assertContains(responseText, "\"success\": true")
        assertContains(responseText, "성공적으로 삭제")
    }
}
