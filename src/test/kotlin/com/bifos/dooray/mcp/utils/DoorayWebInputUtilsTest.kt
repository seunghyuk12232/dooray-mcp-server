package com.bifos.dooray.mcp.utils

import com.bifos.dooray.mcp.exception.ToolException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DoorayWebInputUtilsTest {

    @Test
    fun normalizePostReferenceFromTaskUrl() {
        val result =
            DoorayWebInputUtils.normalizePostReference(
                "https://nhnent.dooray.com/task/to/4285609060273170515?workflowClass=all"
            )

        assertEquals("4285609060273170515", result?.postId)
    }

    @Test
    fun normalizePostReferenceFromProjectTaskUrl() {
        val result =
            DoorayWebInputUtils.normalizePostReference(
                "https://nhnent.dooray.com/project/tasks/4294538452368557412"
            )

        assertEquals("4294538452368557412", result?.postId)
    }

    @Test
    fun normalizeWikiPageReferenceFromProjectPageUrl() {
        val result =
            DoorayWebInputUtils.normalizeWikiPageReference(
                "https://nhnent.dooray.com/project/pages/4292177885602109439"
            )

        assertEquals("4292177885602109439", result?.pageId)
        assertEquals(null, result?.wikiId)
    }

    @Test
    fun normalizeWikiPageReferenceFromWikiUrl() {
        val result =
            DoorayWebInputUtils.normalizeWikiPageReference(
                "https://nhnent.dooray.com/wiki/from/2330620387081104285/4290981083461281802"
            )

        assertEquals("4290981083461281802", result?.pageId)
        assertEquals("2330620387081104285", result?.wikiId)
    }

    @Test
    fun rejectUnsupportedHost() {
        val exception =
            assertFailsWith<ToolException> {
                DoorayWebInputUtils.normalizePostReference(
                    "https://example.com/project/tasks/4294538452368557412"
                )
            }

        assertEquals("UNSUPPORTED_DOORAY_HOST", exception.code)
    }

    @Test
    fun rejectUrlForRawProjectId() {
        val exception =
            assertFailsWith<ToolException> {
                DoorayWebInputUtils.requireRawId(
                    parameterName = "project_id",
                    input = "https://nhnent.dooray.com/project/tasks/4294538452368557412"
                )
            }

        assertEquals("URL_NOT_ALLOWED_FOR_PROJECT_ID", exception.code)
    }
}
