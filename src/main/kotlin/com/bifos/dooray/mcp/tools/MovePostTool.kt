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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun movePostTool(): Tool {
    return Tool(
        name = "dooray_project_move_post",
        description = "두레이 업무를 다른 프로젝트로 이동합니다. 주의: 이동 시 기존 단계(workflow)와 태그 정보는 사라집니다.",
        inputSchema =
            Tool.Input(
                properties =
                    buildJsonObject {
                        putJsonObject("project_id") {
                            put("type", "string")
                            put("description", "현재(원본) 프로젝트 ID (선택사항, 생략 시 post_id로 자동 조회)")
                        }
                        putJsonObject("post_id") {
                            put("type", "string")
                            put(
                                "description",
                                "이동할 업무 ID 또는 Dooray 웹 URL (예: /project/tasks/{postId}, /task/to/{postId}) (필수)"
                            )
                        }
                        putJsonObject("target_project_id") {
                            put("type", "string")
                            put(
                                "description",
                                "이동 대상 프로젝트 ID (선택사항, 미지정 시 동일 프로젝트로 간주)"
                            )
                        }
                        putJsonObject("include_sub_posts") {
                            put("type", "boolean")
                            put("description", "하위 업무 포함 이동 여부 (기본값: true)")
                        }
                    },
                required = listOf("post_id")
            ),
        outputSchema = null,
        annotations = null
    )
}

fun movePostHandler(
    doorayClient: DoorayClient
): suspend (CallToolRequest) -> CallToolResult {
    return handler@{ request ->
        try {
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val postRef =
                DoorayWebInputUtils.normalizePostReference(
                    request.arguments["post_id"]?.jsonPrimitive?.content
                )
            val targetProjectId =
                request.arguments["target_project_id"]?.jsonPrimitive?.content
            val includeSubPosts =
                request.arguments["include_sub_posts"]?.jsonPrimitive?.booleanOrNull ?: true

            if (postRef == null) {
                return@handler CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                JsonUtils.toJsonString(
                                    ToolException(
                                        type = ToolException.PARAMETER_MISSING,
                                        message = "post_id 파라미터가 필요합니다.",
                                        code = "MISSING_POST_ID"
                                    )
                                        .toErrorResponse()
                                )
                            )
                        )
                )
            }

            val resolvedProjectId =
                projectId ?: postRef.projectId ?: doorayClient.resolveProjectIdForPost(postRef.postId)

            val response =
                doorayClient.movePost(
                    resolvedProjectId,
                    postRef.postId,
                    targetProjectId,
                    includeSubPosts
                )

            if (response.header.isSuccessful) {
                val targetProjectDesc = response.result?.project?.id ?: "(동일 프로젝트)"
                val successResponse =
                    ToolSuccessResponse(
                        data = response.result,
                        message =
                            "🚚 업무가 성공적으로 이동되었습니다 (대상 프로젝트: $targetProjectDesc)"
                    )
                CallToolResult(
                    content = listOf(TextContent(JsonUtils.toJsonString(successResponse)))
                )
            } else {
                CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                JsonUtils.toJsonString(
                                    ToolException(
                                        type = ToolException.API_ERROR,
                                        message = response.header.resultMessage,
                                        code = "DOORAY_API_${response.header.resultCode}"
                                    )
                                        .toErrorResponse()
                                )
                            )
                        )
                )
            }
        } catch (e: ToolException) {
            CallToolResult(content = listOf(TextContent(JsonUtils.toJsonString(e.toErrorResponse()))))
        } catch (e: Exception) {
            CallToolResult(
                content =
                    listOf(
                        TextContent(
                            JsonUtils.toJsonString(
                                ToolException(
                                    type = ToolException.INTERNAL_ERROR,
                                    message = "내부 오류가 발생했습니다: ${e.message}"
                                )
                                    .toErrorResponse()
                            )
                        )
                    )
            )
        }
    }
}
