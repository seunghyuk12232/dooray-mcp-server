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

fun getPostByIdTool(): Tool {
    return Tool(
        name = "dooray_project_get_post_by_id",
        description = "프로젝트 ID 없이 업무 ID(post_id)만으로 업무 상세를 조회합니다. (project_id 자동 조회가 불필요하여 dooray_project_get_post보다 빠름)",
        inputSchema =
            Tool.Input(
                properties =
                    buildJsonObject {
                        putJsonObject("post_id") {
                            put("type", "string")
                            put(
                                "description",
                                "업무 ID 또는 Dooray 웹 URL (예: /project/tasks/{postId}, /task/to/{postId}) (필수)"
                            )
                        }
                    },
                required = listOf("post_id")
            ),
        outputSchema = null,
        annotations = null
    )
}

fun getPostByIdHandler(
    doorayClient: DoorayClient
): suspend (CallToolRequest) -> CallToolResult {
    return handler@{ request ->
        try {
            val postRef =
                DoorayWebInputUtils.normalizePostReference(
                    request.arguments["post_id"]?.jsonPrimitive?.content
                )

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

            val response = doorayClient.getPostById(postRef.postId)

            if (response.header.isSuccessful) {
                val post = response.result
                val successResponse =
                    ToolSuccessResponse(
                        data = post,
                        message = "📋 업무 상세 정보를 성공적으로 조회했습니다 (업무번호: ${post.taskNumber})"
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
