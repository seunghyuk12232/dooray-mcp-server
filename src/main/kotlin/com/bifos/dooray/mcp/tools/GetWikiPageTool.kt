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

fun getWikiPageTool(): Tool {
    return Tool(
        name = "dooray_wiki_get_page",
        description =
            "특정 두레이 위키 페이지의 상세 정보를 조회합니다. 페이지 제목, 내용, 작성자, 수정 이력 등 모든 정보를 확인할 수 있습니다.",
        inputSchema =
            Tool.Input(
                properties =
                    buildJsonObject {
                        putJsonObject("project_id") {
                            put("type", "string")
                            put(
                                "description",
                                "위키 프로젝트 ID (선택사항, 생략 시 page_id로 자동 조회)"
                            )
                        }
                        putJsonObject("page_id") {
                            put("type", "string")
                            put(
                                "description",
                                "위키 페이지 ID 또는 Dooray 웹 URL (예: /project/pages/{pageId}, /wiki/from/{wikiId}/{pageId})"
                            )
                        }
                    },
                required = listOf("page_id")
            ),
        outputSchema = null,
        annotations = null
    )
}

fun getWikiPageHandler(doorayClient: DoorayClient): suspend (CallToolRequest) -> CallToolResult {
    return { request ->
        try {
            val projectId = request.arguments["project_id"]?.jsonPrimitive?.content
            val pageRef =
                DoorayWebInputUtils.normalizeWikiPageReference(
                    request.arguments["page_id"]?.jsonPrimitive?.content
                )

            when {
                pageRef == null -> {
                    val errorResponse =
                        ToolException(
                            type = ToolException.PARAMETER_MISSING,
                            message =
                                "page_id 파라미터가 필요합니다. dooray_wiki_list_pages를 사용해서 페이지 ID를 먼저 조회하세요.",
                            code = "MISSING_PAGE_ID"
                        )
                            .toErrorResponse()

                    CallToolResult(
                        content = listOf(TextContent(JsonUtils.toJsonString(errorResponse)))
                    )
                }

                else -> {
                    val resolvedProjectId =
                        projectId ?: pageRef.wikiId ?: doorayClient.resolveWikiIdForPage(pageRef.pageId)
                    val response = doorayClient.getWikiPage(resolvedProjectId, pageRef.pageId)

                    if (response.header.isSuccessful) {
                        val successResponse =
                            ToolSuccessResponse(
                                data = response.result,
                                message =
                                    "📖 위키 페이지 '${response.result.subject}'의 상세 정보를 성공적으로 조회했습니다"
                            )

                        CallToolResult(
                            content =
                                listOf(TextContent(JsonUtils.toJsonString(successResponse)))
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
