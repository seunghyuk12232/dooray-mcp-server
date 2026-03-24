package com.bifos.dooray.mcp.utils

import com.bifos.dooray.mcp.constants.EnvVariableConst.DOORAY_WEB_HOST
import com.bifos.dooray.mcp.exception.ToolException
import java.net.URI

data class DoorayPostReference(
    val postId: String,
    val projectId: String? = null
)

data class DoorayWikiPageReference(
    val pageId: String,
    val wikiId: String? = null
)

object DoorayWebInputUtils {

    private const val DEFAULT_WEB_HOST = "nhnent.dooray.com"

    fun normalizePostReference(input: String?): DoorayPostReference? {
        val normalizedInput = input?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!looksLikeUrl(normalizedInput)) {
            return DoorayPostReference(postId = normalizedInput)
        }

        val uri = parseDoorayUri(normalizedInput)
        val pathSegments = uri.path.trim('/').split('/').filter { it.isNotEmpty() }

        return when {
            pathSegments.size == 3 &&
                    pathSegments[0] == "project" &&
                    pathSegments[1] == "tasks" ->
                DoorayPostReference(postId = pathSegments[2])

            pathSegments.size == 3 &&
                    pathSegments[0] == "task" &&
                    pathSegments[1] == "to" ->
                DoorayPostReference(postId = pathSegments[2])

            else ->
                throw ToolException(
                    type = ToolException.VALIDATION_ERROR,
                    message =
                        "지원하지 않는 Dooray 업무 URL 형식입니다. 예: https://${allowedWebHost()}/project/tasks/{postId} 또는 https://${allowedWebHost()}/task/to/{postId}",
                    code = "UNSUPPORTED_POST_URL"
                )
        }
    }

    fun normalizeWikiPageReference(input: String?): DoorayWikiPageReference? {
        val normalizedInput = input?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!looksLikeUrl(normalizedInput)) {
            return DoorayWikiPageReference(pageId = normalizedInput)
        }

        val uri = parseDoorayUri(normalizedInput)
        val pathSegments = uri.path.trim('/').split('/').filter { it.isNotEmpty() }

        return when {
            pathSegments.size == 3 &&
                    pathSegments[0] == "project" &&
                    pathSegments[1] == "pages" ->
                DoorayWikiPageReference(pageId = pathSegments[2])

            pathSegments.size == 4 &&
                    pathSegments[0] == "wiki" &&
                    (pathSegments[1] == "from" || pathSegments[1] == "important") ->
                DoorayWikiPageReference(
                    pageId = pathSegments[3],
                    wikiId = pathSegments[2]
                )

            else ->
                throw ToolException(
                    type = ToolException.VALIDATION_ERROR,
                    message =
                        "지원하지 않는 Dooray 위키 URL 형식입니다. 예: https://${allowedWebHost()}/project/pages/{pageId}, https://${allowedWebHost()}/wiki/from/{wikiId}/{pageId}",
                    code = "UNSUPPORTED_WIKI_URL"
                )
        }
    }

    fun requireRawId(parameterName: String, input: String?): String? {
        val normalizedInput = input?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (looksLikeUrl(normalizedInput)) {
            throw ToolException(
                type = ToolException.VALIDATION_ERROR,
                message =
                    "$parameterName 파라미터에는 Dooray 웹 URL을 사용할 수 없습니다. 순수 ID를 입력하세요.",
                code = "URL_NOT_ALLOWED_FOR_${parameterName.uppercase()}"
            )
        }
        return normalizedInput
    }

    private fun looksLikeUrl(input: String): Boolean {
        return input.startsWith("http://") || input.startsWith("https://")
    }

    private fun parseDoorayUri(input: String): URI {
        val uri =
            try {
                URI(input)
            } catch (_: Exception) {
                throw ToolException(
                    type = ToolException.VALIDATION_ERROR,
                    message = "유효한 URL 형식이 아닙니다: $input",
                    code = "INVALID_URL"
                )
            }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            throw ToolException(
                type = ToolException.VALIDATION_ERROR,
                message = "Dooray 웹 URL은 http 또는 https 형식이어야 합니다.",
                code = "INVALID_URL_SCHEME"
            )
        }

        val host = uri.host?.lowercase()
        val allowedHost = allowedWebHost().lowercase()
        if (host == null || host != allowedHost) {
            throw ToolException(
                type = ToolException.VALIDATION_ERROR,
                message = "허용되지 않은 Dooray 웹 호스트입니다. 허용 호스트: $allowedHost",
                code = "UNSUPPORTED_DOORAY_HOST"
            )
        }

        return uri
    }

    private fun allowedWebHost(): String {
        return System.getenv(DOORAY_WEB_HOST)?.trim()?.ifEmpty { DEFAULT_WEB_HOST }
            ?: DEFAULT_WEB_HOST
    }
}
