package com.jocmp.capy.accounts.miniflux

import com.jocmp.capy.AccountDelegate
import com.jocmp.capy.AccountPreferences
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.Feed
import com.jocmp.capy.accounts.AddFeedResult
import com.jocmp.capy.accounts.withErrorHandling
import com.jocmp.capy.common.ContentFormatter
import com.jocmp.capy.common.TimeHelpers
import com.jocmp.capy.common.UnauthorizedError
import com.jocmp.capy.common.toDateTime
import com.jocmp.capy.common.transactionWithErrorHandling
import com.jocmp.capy.db.Database
import com.jocmp.capy.logging.CapyLog
import com.jocmp.capy.persistence.ArticleRecords
import com.jocmp.capy.persistence.EnclosureRecords
import com.jocmp.capy.persistence.FeedRecords
import com.jocmp.capy.persistence.TaggingRecords
import com.jocmp.minifluxclient.CreateCategoryRequest
import com.jocmp.minifluxclient.CreateFeedRequest
import com.jocmp.minifluxclient.Entry
import com.jocmp.minifluxclient.EntryResultSet
import com.jocmp.minifluxclient.EntryStatus
import com.jocmp.minifluxclient.Miniflux
import com.jocmp.minifluxclient.UpdateCategoryRequest
import com.jocmp.minifluxclient.UpdateEntriesRequest
import com.jocmp.minifluxclient.UpdateFeedRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.IOException
import org.jsoup.Jsoup
import retrofit2.Response
import java.time.ZonedDateTime
import com.jocmp.minifluxclient.Feed as MinifluxFeed

internal class MinifluxAccountDelegate(
    private val database: Database,
    private val miniflux: Miniflux,
    private val preferences: AccountPreferences,
) : AccountDelegate {
    private val articleRecords = ArticleRecords(database)
    private val enclosureRecords = EnclosureRecords(database)
    private val feedRecords = FeedRecords(database)
    private val taggingRecords = TaggingRecords(database)

    override suspend fun refresh(filter: ArticleFilter, cutoffDate: ZonedDateTime?): Result<Unit> {
        return try {
            refreshIntegrationStatus()
            refreshFeeds()
            refreshArticles()
            preferences.touchLastRefreshedAt()

            Result.success(Unit)
        } catch (exception: IOException) {
            Result.failure(exception)
        } catch (e: UnauthorizedError) {
            Result.failure(e)
        }
    }

    override suspend fun markRead(articleIDs: List<String>): Result<Unit> {
        val entryIDs = articleIDs.map { it.toLong() }

        return withErrorHandling {
            requireSuccess(
                miniflux.updateEntries(
                    UpdateEntriesRequest(
                        entry_ids = entryIDs,
                        status = EntryStatus.READ
                    )
                ),
                action = "mark entries as read",
            )
            Unit
        }
    }

    override suspend fun markUnread(articleIDs: List<String>): Result<Unit> {
        val entryIDs = articleIDs.map { it.toLong() }

        return withErrorHandling {
            requireSuccess(
                miniflux.updateEntries(
                    UpdateEntriesRequest(
                        entry_ids = entryIDs,
                        status = EntryStatus.UNREAD
                    )
                ),
                action = "mark entries as unread",
            )
            Unit
        }
    }

    override suspend fun addStar(articleIDs: List<String>): Result<Unit> {
        val entryIDs = articleIDs.map { it.toLong() }

        return withErrorHandling {
            entryIDs.forEach { entryID ->
                syncBookmark(entryID, starred = true)
            }
            Unit
        }
    }

    override suspend fun removeStar(articleIDs: List<String>): Result<Unit> {
        val entryIDs = articleIDs.map { it.toLong() }

        return withErrorHandling {
            entryIDs.forEach { entryID ->
                syncBookmark(entryID, starred = false)
            }
            Unit
        }
    }

    override suspend fun addSavedSearch(articleID: String, savedSearchID: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Labels not supported"))
    }

    override suspend fun removeSavedSearch(articleID: String, savedSearchID: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Labels not supported"))
    }

    override suspend fun createSavedSearch(name: String): Result<String> {
        return Result.failure(UnsupportedOperationException("Labels not supported"))
    }

    override suspend fun createPage(url: String) =
        Result.failure<Unit>(UnsupportedOperationException("Pages not supported"))

    override suspend fun saveArticleExternally(articleID: String): Result<Unit> {
        return try {
            requireSuccess(
                miniflux.saveEntry(articleID.toLong()),
                action = "save entry externally",
            )
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: UnauthorizedError) {
            Result.failure(e)
        }
    }

    override suspend fun addFeed(
        url: String,
        title: String?,
        folderTitles: List<String>?
    ): AddFeedResult {
        return try {
            val categoryId = folderTitles?.firstOrNull()?.let { folderTitle ->
                findOrCreateCategory(folderTitle)
            }

            val response = miniflux.createFeed(
                CreateFeedRequest(feed_url = url, category_id = categoryId)
            )
            if (response.code() == 401) {
                return AddFeedResult.saveFailure()
            }
            val createResponse = response.body()

            if (response.code() > 300 || createResponse == null) {
                return AddFeedResult.Failure(AddFeedResult.Error.FeedNotFound())
            }

            val feedResponse = miniflux.feed(createResponse.feed_id)
            if (feedResponse.code() == 401) {
                return AddFeedResult.saveFailure()
            }
            val feed = feedResponse.body()

            return if (feed != null) {
                val icons = fetchIcons(listOf(feed))
                upsertFeed(feed, icons)

                val localFeed = feedRecords.find(feed.id.toString())

                if (localFeed != null) {
                    coroutineScope {
                        launch { refreshArticles() }
                    }

                    AddFeedResult.Success(localFeed)
                } else {
                    AddFeedResult.Failure(AddFeedResult.Error.SaveFailure())
                }
            } else {
                AddFeedResult.Failure(AddFeedResult.Error.FeedNotFound())
            }
        } catch (e: IOException) {
            AddFeedResult.networkError()
        }
    }

    override suspend fun updateFeed(
        feed: Feed,
        title: String,
        folderTitles: List<String>,
    ): Result<Feed> = withErrorHandling {
        val categoryId = folderTitles.firstOrNull()?.let { folderTitle ->
            findOrCreateCategory(folderTitle)
        }

        requireSuccess(
            miniflux.updateFeed(
                feedID = feed.id.toLong(),
                request = UpdateFeedRequest(title = title, category_id = categoryId)
            ),
            action = "update feed",
        )

        database.transactionWithErrorHandling {
            feedRecords.update(
                feedID = feed.id,
                title = title,
            )

            if (categoryId != null) {
                folderTitles.forEach { folderTitle ->
                    taggingRecords.upsert(
                        id = taggingID(feed.id, categoryId),
                        feedID = feed.id,
                        name = folderTitle,
                    )
                }
            }

            val taggingIDsToDelete = taggingRecords.findFeedTaggingsToDelete(
                feed = feed,
                excludedTaggingNames = folderTitles
            )

            taggingIDsToDelete.forEach { taggingID ->
                taggingRecords.deleteTagging(taggingID = taggingID)
            }
        }

        feedRecords.find(feed.id)
    }

    override suspend fun updateFolder(
        oldTitle: String,
        newTitle: String
    ): Result<Unit> = withErrorHandling {
        val categories = requireBody(
            miniflux.categories(),
            action = "load categories",
        )
        val category = categories.find { it.title == oldTitle }

        if (category != null) {
            requireSuccess(
                miniflux.updateCategory(
                    categoryID = category.id,
                    request = UpdateCategoryRequest(title = newTitle)
                ),
                action = "update category",
            )

            taggingRecords.updateTitle(previousTitle = oldTitle, title = newTitle)
        }

        Unit
    }

    override suspend fun removeFeed(feed: Feed): Result<Unit> = withErrorHandling {
        requireSuccess(
            miniflux.deleteFeed(feedID = feed.id.toLong()),
            action = "delete feed",
        )

        Unit
    }

    override suspend fun removeFolder(folderTitle: String): Result<Unit> = withErrorHandling {
        val categories = requireBody(
            miniflux.categories(),
            action = "load categories",
        )
        val category = categories.find { it.title == folderTitle }

        if (category != null) {
            requireSuccess(
                miniflux.deleteCategory(categoryID = category.id),
                action = "delete category",
            )
            taggingRecords.deleteByFolderName(folderTitle)
        }

        Unit
    }

    private suspend fun refreshIntegrationStatus() {
        try {
            val response = miniflux.integrationStatus()
            val status = response.body()
            if (response.isSuccessful && status != null) {
                preferences.canSaveArticleExternally.set(status.has_integrations)
            }
        } catch (e: Exception) {
            CapyLog.warn("refresh_integration_status", mapOf("error" to e.message))
        }
    }

    private suspend fun refreshFeeds() {
        val feeds = requireBody(
            miniflux.feeds(),
            action = "load feeds",
        )
        val feedIDsWithIcons = database.feedsQueries.all()
            .executeAsList()
            .filter { it.favicon_url != null }
            .map { it.id }
            .toSet()

        val icons = fetchIcons(feeds.filterNot { it.id.toString() in feedIDsWithIcons })

        database.transactionWithErrorHandling {
            feeds.forEach { feed ->
                upsertFeed(feed, icons)
            }
        }

        val feedsToKeep = feeds.map { it.id.toString() }
        database.feedsQueries.deleteAllExcept(feedsToKeep)
    }

    private suspend fun refreshArticles() = coroutineScope {
        val starred = async { refreshStarredEntries() }
        val unread = async { refreshUnreadEntries() }
        starred.await()
        unread.await()
        fetchAllEntries()
    }

    private suspend fun refreshStarredEntries() {
        val ids = fetchAllEntryIDs { offset ->
            miniflux.entries(starred = true, limit = MAX_ENTRY_LIMIT, offset = offset)
        }

        articleRecords.markAllStarred(articleIDs = ids)
    }

    private suspend fun refreshUnreadEntries() {
        val ids = fetchAllEntryIDs { offset ->
            miniflux.entries(
                status = EntryStatus.UNREAD.value,
                limit = MAX_ENTRY_LIMIT,
                offset = offset
            )
        }

        articleRecords.markAllUnread(articleIDs = ids)
    }

    private suspend fun fetchAllEntryIDs(
        fetch: suspend (offset: Int) -> Response<EntryResultSet>
    ): List<String> {
        val firstPage = requireBody(
            fetch(0),
            action = "load entry ids",
        )
        val ids = firstPage.entries.map { it.id.toString() }.toMutableList()

        var offset = MAX_ENTRY_LIMIT
        while (ids.size < firstPage.total) {
            val page = requireBody(
                fetch(offset),
                action = "load entry ids",
            )
            if (page.entries.isEmpty()) break
            ids.addAll(page.entries.map { it.id.toString() })
            offset += MAX_ENTRY_LIMIT
        }

        return ids
    }

    private suspend fun fetchAllEntries() = coroutineScope {
        val changedAfter = preferences.lastRefreshedAt.get().takeIf { it > 0 }

        val firstResult = requireBody(
            miniflux.entries(
                limit = MAX_ENTRY_LIMIT,
                offset = 0,
                order = "published_at",
                direction = "desc",
                changedAfter = changedAfter,
            ),
            action = "load entries",
        )

        val total = firstResult.total

        val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)

        val remainingPages = (MAX_ENTRY_LIMIT until total step MAX_ENTRY_LIMIT)
            .map { offset ->
                async {
                    semaphore.withPermit {
                        requireBody(
                            miniflux.entries(
                                limit = MAX_ENTRY_LIMIT,
                                offset = offset,
                                order = "published_at",
                                direction = "desc",
                                changedAfter = changedAfter,
                            ),
                            action = "load entries",
                        ).entries
                    }
                }
            }
            .awaitAll()

        saveEntries(firstResult.entries)
        remainingPages.forEach { entries ->
            saveEntries(entries)
        }
    }

    private fun saveEntries(entries: List<Entry>) {
        database.transactionWithErrorHandling {
            entries.forEach { entry ->
                val updated = TimeHelpers.nowUTC()
                val articleID = entry.id.toString()
                val imageURL = MinifluxEnclosureParsing.parsedImageURL(entry)
                val enclosures = entry.enclosures.orEmpty()

                database.articlesQueries.create(
                    id = articleID,
                    feed_id = entry.feed_id.toString(),
                    title = Jsoup.parse(entry.title).text(),
                    author = entry.author,
                    content_html = entry.content,
                    extracted_content_url = null,
                    url = entry.url,
                    summary = ContentFormatter.summary(entry.content),
                    image_url = imageURL,
                    published_at = entry.published_at.toDateTime?.toEpochSecond(),
                    enclosure_type = enclosures.firstOrNull()?.mime_type,
                )

                articleRecords.createStatus(
                    articleID = articleID,
                    updatedAt = updated,
                    read = entry.status == EntryStatus.READ
                )

                enclosures.forEach { enclosure ->
                    enclosureRecords.create(
                        url = enclosure.url,
                        type = enclosure.mime_type,
                        articleID = articleID,
                        itunesDurationSeconds = null,
                        itunesImage = null,
                    )
                }
            }
        }
    }

    private suspend fun syncBookmark(entryID: Long, starred: Boolean) {
        val entry = requireBody(
            miniflux.entry(entryID),
            action = "load entry",
        )

        if (entry.starred != starred) {
            requireSuccess(
                miniflux.toggleBookmark(entryID),
                action = "toggle entry bookmark",
            )
        }
    }

    private fun upsertFeed(feed: MinifluxFeed, icons: Map<Long, String>) {
        val icon = feed.icon?.icon_id?.let { icons[it] }

        database.feedsQueries.upsert(
            id = feed.id.toString(),
            subscription_id = feed.id.toString(),
            title = feed.title,
            feed_url = feed.feed_url,
            site_url = feed.site_url,
            favicon_url = icon,
            priority = null,
            itunes_image_url = null,
        )

        feed.category?.let { category ->
            database.taggingsQueries.upsert(
                id = taggingID(feed.id.toString(), category.id),
                feed_id = feed.id.toString(),
                name = category.title
            )
        }
    }

    private suspend fun findOrCreateCategory(title: String): Long {
        val categories = requireBody(
            miniflux.categories(),
            action = "load categories",
        )
        val existing = categories.find { it.title == title }

        return if (existing != null) {
            existing.id
        } else {
            requireBody(
                miniflux.createCategory(CreateCategoryRequest(title = title)),
                action = "create category",
            ).id
        }
    }

    private fun taggingID(feedID: String, categoryId: Long) = "${feedID}-${categoryId}"

    private suspend fun fetchIcons(feeds: List<MinifluxFeed>): Map<Long, String> = coroutineScope {
        val iconIds = feeds.mapNotNull { it.icon?.icon_id }.filter { it > 0 }.distinct()

        iconIds.map { iconId ->
            async {
                try {
                    val response = miniflux.icon(iconId)
                    val iconData = response.body()

                    if (response.isSuccessful && iconData != null) {
                        iconId to "data:${iconData.data}"
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    CapyLog.warn("fetch_icon", mapOf("icon_id" to iconId.toString()))
                    null
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private fun <T> requireBody(response: Response<T>, action: String): T {
        if (response.code() == 401) {
            throw UnauthorizedError()
        }

        if (!response.isSuccessful) {
            throw IOException("Failed to $action: HTTP ${response.code()}")
        }

        return response.body() ?: throw IOException("Failed to $action: empty response")
    }

    private fun requireSuccess(response: Response<*>, action: String) {
        if (response.code() == 401) {
            throw UnauthorizedError()
        }

        if (!response.isSuccessful) {
            throw IOException("Failed to $action: HTTP ${response.code()}")
        }
    }

    companion object {
        const val MAX_ENTRY_LIMIT = 250
        private const val MAX_CONCURRENT_FETCHES = 8
    }
}
