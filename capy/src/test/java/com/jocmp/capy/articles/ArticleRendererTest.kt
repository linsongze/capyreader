package com.jocmp.capy.articles

import com.jocmp.capy.Article
import com.jocmp.capy.InMemoryPreference
import java.io.File
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ArticleRendererTest {
    @Test
    fun `it does not render the feed name in the article header`() {
        val renderer = buildRenderer()
        val article = article(
            title = "Long article title",
            feedName = "Example Feed"
        )

        val html = renderer.render(
            article = article,
            byline = "January 1, 2026",
            colors = templateColors,
            hideImages = false,
        )

        assertFalse(html.contains(">Example Feed<"))
    }

    @Test
    fun `it preserves the full article title when rendering the header`() {
        val renderer = buildRenderer()
        val title =
            "This is the full article title with the ending words kept intact for the detail page"

        val html = renderer.render(
            article = article(
                title = title,
                feedName = "Example Feed"
            ),
            byline = "January 1, 2026",
            colors = templateColors,
            hideImages = false,
        )

        assertContains(html, title)
    }

    private fun buildRenderer(template: String = templateFile().readText()): ArticleRenderer {
        return ArticleRenderer(
            template = template,
            textSize = preference(16),
            fontOption = preference(FontOption.SYSTEM_DEFAULT),
            titleFontSize = preference(24),
            textAlignment = preference(TextAlignment.LEFT),
            titleFollowsBodyFont = preference(false),
            enableHorizontalScroll = preference(false),
        )
    }

    private fun article(
        title: String,
        feedName: String,
    ) = Article(
        id = "article-id",
        feedID = "feed-id",
        title = title,
        author = "Author",
        contentHTML = "<p>Body</p>",
        url = null,
        summary = "Summary",
        imageURL = null,
        updatedAt = now,
        publishedAt = now,
        read = false,
        starred = false,
        feedName = feedName,
    )

    private fun <T> preference(value: T) = InMemoryPreference(
        key = "key-$value",
        defaultValue = value,
        store = mutableMapOf()
    )

    private fun templateFile(): File {
        val candidates = listOf(
            File("../app/src/main/res/raw/template.html"),
            File("app/src/main/res/raw/template.html"),
            File("../capy/src/main/res/raw/template.html"),
            File("capy/src/main/res/raw/template.html"),
            File("src/main/res/raw/template.html"),
        )

        return candidates.firstOrNull(File::exists)
            ?: error("Unable to locate template.html for article detail tests")
    }

    companion object {
        private val now = ZonedDateTime.parse("2026-01-01T00:00:00Z")

        private val templateColors = mapOf(
            "color_primary" to "#000000",
            "color_surface" to "#111111",
            "color_surface_container_highest" to "#222222",
            "color_on_surface" to "#ffffff",
            "color_on_surface_variant" to "#dddddd",
            "color_surface_variant" to "#333333",
            "color_primary_container" to "#444444",
            "color_on_primary_container" to "#eeeeee",
            "color_secondary" to "#555555",
            "color_surface_container" to "#666666",
            "color_surface_tint" to "#777777",
        )
    }
}
