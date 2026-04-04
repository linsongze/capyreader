package com.capyreader.app.ui.articles.list

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.jocmp.capy.Article
import me.saket.swipe.SwipeableActionsBox

@Composable
fun ArticleRowSwipeBox(
    article: Article,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val swipeState = rememberArticleRowSwipeState(article = article)

    if (!enabled || swipeState.disabled) {
        content()
    } else {
        SwipeableActionsBox(
            startActions = swipeState.start,
            endActions = swipeState.end,
            backgroundUntilSwipeThreshold = MaterialTheme.colorScheme.surface
        ) {
            content()
        }
    }
}
