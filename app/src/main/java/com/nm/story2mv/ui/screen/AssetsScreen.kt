package com.nm.story2mv.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nm.story2mv.data.model.AssetItem
import com.nm.story2mv.ui.screen.components.EmptyStateCard
import com.nm.story2mv.ui.viewmodel.AssetsUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetsScreen(
    state: AssetsUiState,
    onQueryChanged: (String) -> Unit,
    onAssetSelected: (AssetItem) -> Unit,
    onAssetPlay: (AssetItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "资产库", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.query,
            onValueChange = onQueryChanged,
            placeholder = { Text("搜索故事或生成时间") }
        )

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (state.assets.isEmpty() && !state.isLoading) {
            EmptyAssets()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(state.assets, key = { it.id }) { asset ->
                    AssetCard(
                        asset = asset,
                        onClick = { onAssetSelected(asset) },
                        onPlay = { onAssetPlay(asset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetCard(
    asset: AssetItem,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .height(200.dp)
            .clickable { onClick() }
    ) {
         Column {
            AsyncImage(
                model = asset.thumbnailUrl,
                contentDescription = asset.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = asset.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = asset.createdAt.toString(),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "播放预览",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onPlay() }
                )
            }
        }
    }
}

@Composable
private fun EmptyAssets() {
    EmptyStateCard(
        title = "还没有成片资产",
        description = "当你在 Storyboard 中完成视频合成后，这里会自动出现可预览与导出的记录。"
    )
}
