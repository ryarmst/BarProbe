package dev.barcodeworkbench.feature.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.feature.learn.content.Article
import dev.barcodeworkbench.feature.learn.content.Concepts

private enum class LearnTab(val label: String) {
    GUIDE("Guide"),
    SYMBOLOGIES("Formats"),
    REFERENCE("Reference"),
}

/**
 * The reference section.
 *
 * Three tabs rather than one long document: a conceptual guide for understanding, a
 * per-format reference for choosing, and a cheat sheet for looking something up mid-task.
 * Those are genuinely different needs and mixing them serves none of them well.
 */
@Composable
fun LearnScreen(modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    var openArticle by remember { mutableStateOf<Article?>(null) }

    // Reading an article is a drill-down, so the back gesture should leave it rather
    // than the app.
    BackHandler(enabled = openArticle != null) { openArticle = null }

    Column(modifier = modifier.fillMaxSize()) {
        val article = openArticle
        if (article != null) {
            ArticleView(article = article, onBack = { openArticle = null })
            return@Column
        }

        PrimaryTabRow(selectedTabIndex = tab) {
            LearnTab.entries.forEachIndexed { index, entry ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(entry.label) },
                )
            }
        }

        val content = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)

        when (LearnTab.entries[tab]) {
            LearnTab.GUIDE -> ArticleList(
                articles = Concepts.all,
                onOpen = { openArticle = it },
                modifier = content,
            )
            LearnTab.SYMBOLOGIES -> SymbologyReference(modifier = content)
            LearnTab.REFERENCE -> QuickReference(modifier = content)
        }
    }
}

@Composable
private fun ArticleList(
    articles: List<Article>,
    onOpen: (Article) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        item {
            Text(
                text = "Short explanations of the things that most often cause trouble.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(articles, key = { it.id }) { article ->
            Card(
                onClick = { onOpen(article) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(article.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = article.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleView(article: Article, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(article.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = article.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        article.blocks.forEach { BlockView(it) }
    }
}
