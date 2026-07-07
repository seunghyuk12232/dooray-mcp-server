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
import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

fun uploadPostFileTool(): Tool {
    return Tool(
        name = "dooray_project_upload_post_file",
        description = "두레이 프로젝트 업무에 첨부파일을 업로드합니다. 로컬 파일 경로(file_path)를 지정하면 해당 파일을 업무에 첨부합니다.",
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
                        putJsonObject("file_path") {
                            put("type", "string")
                            put("description", "업로드할 로컬 파일의 절대 경로 (필수)")
                        }
                        putJsonObject("file_name") {
                            put("type", "string")
                            put("description", "첨부파일 이름 (선택사항, 생략 시 file_path의 파일명 사용)")
                        }
                        putJsonObject("mime_type") {
                            put("type", "string")
                            put(
                                "description",
                                "파일 MIME 타입 (선택사항, 생략 시 application/octet-stream)"
                            )
                        }
                    },
                required = listOf("post_id", "file_path")
            ),
        outputSchema = null,
        annotations = null
    )
}

fun uploadPostFileHandler(
    doorayClient: DoorayClient
): suspend (CallToolRequest) -> CallToolResult {
    return handler@{ request ->
        try {
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val postRef =
                DoorayWebInputUtils.normalizePostReference(
                    request.arguments["post_id"]?.jsonPrimitive?.content
                )
            val filePath = request.arguments["file_path"]?.jsonPrimitive?.content

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

            if (filePath.isNullOrBlank()) {
                return@handler CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                JsonUtils.toJsonString(
                                    ToolException(
                                        type = ToolException.PARAMETER_MISSING,
                                        message = "file_path 파라미터가 필요합니다.",
                                        code = "MISSING_FILE_PATH"
                                    )
                                        .toErrorResponse()
                                )
                            )
                        )
                )
            }

            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                return@handler CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                JsonUtils.toJsonString(
                                    ToolException(
                                        type = ToolException.VALIDATION_ERROR,
                                        message = "파일을 찾을 수 없거나 일반 파일이 아닙니다: $filePath",
                                        code = "FILE_NOT_FOUND"
                                    )
                                        .toErrorResponse()
                                )
                            )
                        )
                )
            }

            val fileName = request.arguments["file_name"]?.jsonPrimitive?.content ?: file.name
            val mimeType = request.arguments["mime_type"]?.jsonPrimitive?.content

            val resolvedProjectId =
                projectId ?: postRef.projectId ?: doorayClient.resolveProjectIdForPost(postRef.postId)

            val response =
                doorayClient.uploadPostFile(
                    resolvedProjectId,
                    postRef.postId,
                    fileName,
                    file.readBytes(),
                    mimeType
                )

            if (response.header.isSuccessful) {
                val nextStepHint =
                    "\n\n💡 다음 가능한 작업:\n" +
                        "- dooray_project_get_post_files: 첨부파일 목록 조회\n" +
                        "- dooray_project_delete_post_file: 첨부파일 삭제"
                val successResponse =
                    ToolSuccessResponse(
                        data = response.result,
                        message =
                            "📎 첨부파일이 성공적으로 업로드되었습니다 (파일명: $fileName, fileId: ${response.result.id})$nextStepHint"
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
