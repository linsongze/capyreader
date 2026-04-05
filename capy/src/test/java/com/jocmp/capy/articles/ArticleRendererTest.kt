package com.jocmp.capy.articles

import android.content.Context
import android.content.res.Resources
import com.jocmp.capy.Article
import com.jocmp.capy.InMemoryPreference
import com.jocmp.capy.R
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
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

    private fun buildRenderer(template: String = defaultTemplate): ArticleRenderer {
        val resources = mockk<Resources>()
        val context = mockk<Context>()

        every { context.resources } returns resources
        every { resources.openRawResource(R.raw.template) } returns ByteArrayInputStream(
            template.toByteArray()
        )

        return ArticleRenderer(
            context = context,
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

        private val defaultTemplate = """
            <!DOCTYPE html>
            <html dir="auto">
              <head></head>
              <body>
                <article role="main">
                  <header>
                    <a class="article__header" href="{{external_link}}">
                      <h1 class="article__title article__title--font-{{title_font_family}}">{{title}}</h1>
                      <div>{{byline}}</div>
                      <div>{{feed_name}}</div>
                    </a>
                  </header>
                  <div class="article__body article__body--font-{{font_family}}">
                    <div id="article-body-content">{{body}}</div>
                  </div>
                </article>
              </body>
            </html>
        """.trimIndent()
    }
}
