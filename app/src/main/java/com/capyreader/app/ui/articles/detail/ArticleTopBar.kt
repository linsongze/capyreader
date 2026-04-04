package com.capyreader.app.ui.articles.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import com.capyreader.app.R
import com.capyreader.app.common.shareArticle
import com.capyreader.app.ui.articles.DeletePageDialog
import com.capyreader.app.ui.articles.LocalArticleActions
import com.capyreader.app.ui.articles.LocalLabelsActions
import com.capyreader.app.ui.LocalLinkOpener
import com.capyreader.app.ui.components.buildCopyToClipboard
import com.capyreader.app.ui.components.ToolbarTooltip
import com.capyreader.app.ui.fixtures.PreviewKoinApplication
import com.capyreader.app.ui.settings.LocalSnackbarHost
import com.jocmp.capy.Article
import kotlinx.coroutines.launch

private val sizeSpec = spring<IntSize>(stiffness = 700f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleTopBar(
    show: Boolean,
    isScrolled: Boolean,
    article: Article,
    canDeletePage: Boolean = false,
    canSaveExternally: Boolean = false,
    onDeletePage: () -> Unit = {},
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onClose: () -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surface
    val labelsActions = LocalLabelsActions.current
    val linkOpener = LocalLinkOpener.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val articleUrl = article.url?.toString()
    val copyLink = articleUrl?.let { buildCopyToClipboard(it) }
    val (isStyleSheetOpen, setStyleSheetOpen) = rememberSaveable { mutableStateOf(false) }
    val (isDeletePageDialogOpen, setDeletePageDialogOpen) = rememberSaveable { mutableStateOf(false) }
    val (isActionMenuOpen, setActionMenuOpen) = rememberSaveable(article.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier.drawBehind { drawRect(containerColor) }) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            )
            AnimatedVisibility(
                visible = show,
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = sizeSpec
                ),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = sizeSpec
                ),
            ) {
                TopAppBar(
                    navigationIcon = {
                        ArticleNavigationIcon(
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = onToggleFullscreen,
                            onClose = onClose,
                        )
                    },
                    title = {},
                    actions = {
                        if (canSaveExternally) {
                            val articleActions = LocalArticleActions.current
                            val snackbar = LocalSnackbarHost.current
                            val scope = rememberCoroutineScope()
                            val savedMessage = stringResource(R.string.article_actions_save_externally_success)
                            val failedMessage = stringResource(R.string.article_actions_save_externally_failure)

                            ToolbarTooltip(
                                message = stringResource(R.string.article_actions_save_externally)
                            ) {
                                IconButton(onClick = {
                                    articleActions.saveExternally(article.id) { result ->
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                if (result.isSuccess) savedMessage else failedMessage
                                            )
                                        }
                                    }
                                }) {
                                    Icon(
                                        Icons.Outlined.Save,
                                        contentDescription = stringResource(R.string.article_actions_save_externally),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        if (canDeletePage) {
                            ToolbarTooltip(
                                message = stringResource(R.string.article_actions_delete_page)
                            ) {
                                IconButton(
                                    onClick = { setDeletePageDialogOpen(true) },
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.article_actions_delete_page),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        if (labelsActions.showLabels) {
                            ToolbarTooltip(
                                message = stringResource(R.string.freshrss_article_actions_label)
                            ) {
                                IconButton(
                                    onClick = { labelsActions.openSheet(article.id) },
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Label,
                                        contentDescription = stringResource(R.string.freshrss_article_actions_label),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        if (articleUrl != null && copyLink != null) {
                            Box {
                                ToolbarTooltip(
                                    message = stringResource(R.string.article_actions_open_menu)
                                ) {
                                    IconButton(
                                        onClick = { setActionMenuOpen(true) },
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.article_actions_open_menu),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isActionMenuOpen,
                                    onDismissRequest = { setActionMenuOpen(false) },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.article_vertical_open_article_in_browser)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            setActionMenuOpen(false)
                                            linkOpener.open(articleUrl.toUri())
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.article_actions_copy_link)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            setActionMenuOpen(false)
                                            copyLink()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.article_share)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Share,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            setActionMenuOpen(false)
                                            context.shareArticle(article)
                                        },
                                    )
                                }
                            }
                        }
                        ToolbarTooltip(
                            message = stringResource(R.string.article_style_options)
                        ) {
                            IconButton(
                                onClick = { setStyleSheetOpen(true) },
                            ) {
                                Icon(
                                    Icons.Outlined.FormatSize,
                                    contentDescription = stringResource(R.string.article_style_options),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets(0.dp),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
            if (isScrolled) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    thickness = 0.5f.dp,
                )
            }
        }
    }

    if (isStyleSheetOpen) {
        ModalBottomSheet(onDismissRequest = { setStyleSheetOpen(false) }) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                ArticleStylePicker()
            }
        }
    }

    if (isDeletePageDialogOpen) {
        DeletePageDialog(
            onConfirm = {
                setDeletePageDialogOpen(false)
                onDeletePage()
            },
            onDismissRequest = { setDeletePageDialogOpen(false) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ArticleTopBarPreview() {
    PreviewKoinApplication {
        ArticleTopBar(
            show = true,
            isScrolled = false,
            article = Article(
                id = "1",
                feedID = "feed",
                title = "Preview article",
                author = null,
                contentHTML = "",
                url = null,
                summary = "",
                imageURL = null,
                updatedAt = java.time.ZonedDateTime.now(),
                publishedAt = java.time.ZonedDateTime.now(),
                read = false,
                starred = false,
                feedName = "Preview feed"
            ),
            onClose = {}
        )
    }
}
