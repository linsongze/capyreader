package com.jocmp.capy.articles

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class ArticleStylesheetTest {
    @Test
    fun `it keeps article detail top padding compact`() {
        val stylesheet = stylesheetFile().readText()

        assertContains(stylesheet, "body{font-family:sans-serif;word-wrap:break-word;margin:var(--article-top-margin) 0 0 0;padding:1rem 1rem;")
        assertContains(stylesheet, "@media only screen and (min-width: 769px){body{padding:1rem 4rem}}")
    }

    private fun stylesheetFile(): File {
        val candidates = listOf(
            File("src/main/assets/stylesheet.css"),
            File("capy/src/main/assets/stylesheet.css"),
        )

        return candidates.firstOrNull(File::exists)
            ?: error("Unable to locate stylesheet.css for article detail tests")
    }
}
