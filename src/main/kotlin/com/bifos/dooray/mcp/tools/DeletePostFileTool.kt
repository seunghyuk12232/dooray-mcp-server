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

fun deletePostFileTool(): Tool {
    return Tool(
        name = "dooray_project_delete_post_file",
        description = "두레이 프로젝트 업무의 첨부파일을 삭제합니다.",
        inputSchema =
            Tool.Input(
                properties =
                    buildJsonObject {
                        putJsonObject("project_id") {
                            put("type", "string")
                            put("description", "프로젝트 ID (선택사항, 생략 시 post_id로 자동 조회)")
                        }
                        putJsonObject("post_id") {
                            put("type", "string")
                            put(
                                "description",
                                "업무 ID 또는 Dooray 웹 URL (예: /project/tasks/{postId}, /task/to/{postId}) (필수)"
                            )
                        }
                        putJsonObject("file_id") {
                            put("type", "string")
                            put(
                                "description",
                                "삭제할 첨부파일 ID (dooray_project_get_post_files로 조회 가능) (필수)"
                            )
                        }
                    },
                required = listOf("post_id", "file_id")
            ),
        outputSchema = null,
        annotations = null
    )
}

fun deletePostFileHandler(
    doorayClient: DoorayClient
): suspend (CallToolRequest) -> CallToolResult {
    return handler@{ request ->
        try {
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val postRef =
                DoorayWebInputUtils.normalizePostReference(
                    request.arguments["post_id"]?.jsonPrimitive?.content
                )
            val fileId = request.arguments["file_id"]?.jsonPrimitive?.content

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

            if (fileId.isNullOrBlank()) {
                return@handler CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                JsonUtils.toJsonString(
                                    ToolException(
                                        type = ToolException.PARAMETER_MISSING,
                                        message = "file_id 파라미터가 필요합니다.",
                                        code = "MISSING_FILE_ID"
                                    )
                                        .toErrorResponse()
                                )
                            )
                        )
                )
            }

            val resolvedProjectId =
                projectId ?: postRef.projectId ?: doorayClient.resolveProjectIdForPost(postRef.postId)

            val response = doorayClient.deletePostFile(resolvedProjectId, postRef.postId, fileId)

            if (response.header.isSuccessful) {
                val successResponse =
                    ToolSuccessResponse(data = null, message = "🗑️ 첨부파일이 성공적으로 삭제되었습니다.")
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
