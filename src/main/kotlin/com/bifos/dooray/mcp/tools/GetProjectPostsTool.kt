package com.bifos.dooray.mcp.tools

import com.bifos.dooray.mcp.client.DoorayClient
import com.bifos.dooray.mcp.exception.ToolException
import com.bifos.dooray.mcp.types.ToolSuccessResponse
import com.bifos.dooray.mcp.utils.DoorayWebInputUtils
import com.bifos.dooray.mcp.utils.JsonUtils
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun getProjectPostsTool(): Tool {
    return Tool(
        name = "dooray_project_list_posts",
        description = "두레이 프로젝트의 업무 목록을 조회합니다. 다양한 필터 조건과 정렬 옵션을 지원합니다.",
        inputSchema =
            Tool.Input(
                properties =
                    buildJsonObject {
                        putJsonObject("project_id") {
                            put("type", "string")
                            put("description", "프로젝트 ID (필수)")
                        }
                        putJsonObject("page") {
                            put("type", "integer")
                            put("description", "페이지 번호 (기본값: 0)")
                            put("default", 0)
                        }
                        putJsonObject("size") {
                            put("type", "integer")
                            put("description", "페이지 크기 (기본값: 20, 최대: 100)")
                            put("default", 20)
                        }
                        putJsonObject("to_member_ids") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "담당자 멤버 ID 목록 (선택사항)")
                        }
                        putJsonObject("from_member_ids") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "등록자 멤버 ID 목록 (선택사항)")
                        }
                        putJsonObject("cc_member_ids") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "참조자 멤버 ID 목록 (선택사항)")
                        }
                        putJsonObject("tag_ids") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "태그 ID 목록 (선택사항)")
                        }
                        putJsonObject("parent_post_id") {
                            put("type", "string")
                            put(
                                "description",
                                "상위 업무 ID - 특정 업무의 하위 업무들만 조회 (선택사항)"
                            )
                        }
                        putJsonObject("post_workflow_classes") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put(
                                "description",
                                "워크플로우 클래스 (backlog, registered, working, closed) (선택사항)"
                            )
                        }
                        putJsonObject("post_workflow_ids") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "워크플로우 ID 목록 (선택사항)")
                        }
                        putJsonObject("milestone_ids") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "마일스톤 ID 목록 (선택사항)")
                        }
                        putJsonObject("post_number") {
                            put("type", "string")
                            put("description", "업무 번호 (선택사항)")
                        }
                        putJsonObject("subjects") {
                            put("type", "string")
                            put("description", "업무 제목 검색어 (선택사항)")
                        }
                        putJsonObject("created_at") {
                            put("type", "string")
                            put("description", "생성일 필터 값 (선택사항)")
                        }
                        putJsonObject("updated_at") {
                            put("type", "string")
                            put("description", "수정일 필터 값 (선택사항)")
                        }
                        putJsonObject("due_at") {
                            put("type", "string")
                            put("description", "만기일 필터 값 (선택사항)")
                        }
                        putJsonObject("order") {
                            put("type", "string")
                            put(
                                "description",
                                "정렬 조건 (postDueAt, postUpdatedAt, createdAt, 역순은 앞에 '-' 추가) (선택사항)"
                            )
                        }
                    },
                required = listOf("project_id")
            ),
        outputSchema = null,
        annotations = null
    )
}

fun getProjectPostsHandler(
    doorayClient: DoorayClient
): suspend (CallToolRequest) -> CallToolResult {
    return { request ->
        try {
            val projectId =
                DoorayWebInputUtils.requireRawId(
                    parameterName = "project_id",
                    input = request.arguments["project_id"]?.jsonPrimitive?.content
                )

            if (projectId == null) {
                val errorResponse =
                    ToolException(
                        type = ToolException.PARAMETER_MISSING,
                        message = "project_id 파라미터가 필요합니다. 프로젝트 ID를 입력하세요.",
                        code = "MISSING_PROJECT_ID"
                    )
                        .toErrorResponse()

                CallToolResult(content = listOf(TextContent(JsonUtils.toJsonString(errorResponse))))
            } else {
                val page = request.arguments["page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val size = request.arguments["size"]?.jsonPrimitive?.content?.toIntOrNull() ?: 20

                // 배열 파라미터 처리
                val toMemberIds =
                    request.arguments["to_member_ids"]?.let { element ->
                        JsonUtils.parseStringArray(element.toString())
                    }
                val fromMemberIds =
                    request.arguments["from_member_ids"]?.let { element ->
                        JsonUtils.parseStringArray(element.toString())
                    }
                val ccMemberIds =
                    request.arguments["cc_member_ids"]?.let { element ->
                        JsonUtils.parseStringArray(element.toString())
                    }
                val tagIds =
                    request.arguments["tag_ids"]?.let { element ->
                        JsonUtils.parseStringArray(element.toString())
                    }
                val postWorkflowClasses =
                    request.arguments["post_workflow_classes"]?.let { element ->
                        JsonUtils.parseStringArray(element.toString())
                    }
                val postWorkflowIds =
                    request.arguments["post_workflow_ids"]?.let { element ->
                        JsonUtils.parseStringArray(element.toString())
                    }
                val milestoneIds =
                    request.arguments["milestone_ids"]?.let { element ->
                        JsonUtils.parseStringArray(element.toString())
                    }

                // 단일 값 파라미터 처리
                val parentPostId = request.arguments["parent_post_id"]?.jsonPrimitive?.content
                val postNumber = request.arguments["post_number"]?.jsonPrimitive?.content
                val subjects = request.arguments["subjects"]?.jsonPrimitive?.content
                val createdAt = request.arguments["created_at"]?.jsonPrimitive?.content
                val updatedAt = request.arguments["updated_at"]?.jsonPrimitive?.content
                val dueAt = request.arguments["due_at"]?.jsonPrimitive?.content
                val order = request.arguments["order"]?.jsonPrimitive?.content

                val response =
                    doorayClient.getPosts(
                        projectId = projectId,
                        page = page,
                        size = size,
                        fromMemberIds = fromMemberIds,
                        toMemberIds = toMemberIds,
                        ccMemberIds = ccMemberIds,
                        tagIds = tagIds,
                        parentPostId = parentPostId,
                        postNumber = postNumber,
                        postWorkflowClasses = postWorkflowClasses,
                        postWorkflowIds = postWorkflowIds,
                        milestoneIds = milestoneIds,
                        subjects = subjects,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        dueAt = dueAt,
                        order = order
                    )

                if (response.header.isSuccessful) {
                    val pageInfo = if (page == 0) "첫 번째 페이지" else "${page + 1}번째 페이지"

                    val nextStepHint =
                        if (response.result.isNotEmpty()) {
                            "\n\n💡 다음 단계: 특정 업무의 상세 정보를 보려면 dooray_project_get_post를 사용하세요."
                        } else {
                            if (page == 0) "\n\n📋 조회 결과가 없습니다. 필터 조건을 확인해주세요."
                            else "\n\n📄 더 이상 업무가 없습니다."
                        }

                    val successResponse =
                        ToolSuccessResponse(
                            data = response.result,
                            message =
                                "📋 프로젝트 업무 목록을 성공적으로 조회했습니다 ($pageInfo, 총 ${response.result.size}개)$nextStepHint"
                        )

                    CallToolResult(
                        content = listOf(TextContent(JsonUtils.toJsonString(successResponse)))
                    )
                } else {
                    val errorResponse =
                        ToolException(
                            type = ToolException.API_ERROR,
                            message = response.header.resultMessage,
                            code = "DOORAY_API_${response.header.resultCode}"
                        )
                            .toErrorResponse()

                    CallToolResult(
                        content = listOf(TextContent(JsonUtils.toJsonString(errorResponse)))
                    )
                }
            }
        } catch (e: ToolException) {
            CallToolResult(content = listOf(TextContent(JsonUtils.toJsonString(e.toErrorResponse()))))
        } catch (e: Exception) {
            val errorResponse =
                ToolException(
                    type = ToolException.INTERNAL_ERROR,
                    message = "내부 오류가 발생했습니다: ${e.message}"
                )
                    .toErrorResponse()

            CallToolResult(content = listOf(TextContent(JsonUtils.toJsonString(errorResponse))))
        }
    }
}
