package com.nm.story2mv.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nm.story2mv.data.model.AssetItem
import com.nm.story2mv.di.AppContainer
import com.nm.story2mv.ui.screen.AssetsScreen
import com.nm.story2mv.ui.screen.CreateScreen
import com.nm.story2mv.ui.screen.PreviewScreen
import com.nm.story2mv.ui.screen.ShotDetailScreen
import com.nm.story2mv.ui.screen.StoryboardScreen
import com.nm.story2mv.ui.viewmodel.ViewModelFactories

sealed class StoryRoute(val route: String) {
    data object Create : StoryRoute("create")
    data object Storyboard : StoryRoute("storyboard/{storyId}") {
        const val ARG = "storyId"
        fun build(storyId: Long) = "storyboard/$storyId"
    }


    data object ShotDetail : StoryRoute("shotDetail/{storyId}/{shotId}") {
        const val STORY_ID = "storyId"
        const val SHOT_ID = "shotId"
        fun build(storyId: Long, shotId: String) = "shotDetail/$storyId/$shotId"
    }

    data object Assets : StoryRoute("assets")

    data object Preview :
        StoryRoute("preview?storyId={storyId}&previewUri={previewUri}&previewTitle={previewTitle}") {
        const val STORY_ID = "storyId"
        const val PREVIEW_URI = "previewUri"
        const val PREVIEW_TITLE = "previewTitle"

        fun buildForStory(storyId: Long) = "preview?storyId=$storyId&previewUri=&previewTitle="

        fun buildForAsset(asset: AssetItem): String {
            val encodedUri = android.net.Uri.encode(asset.previewUri?.toString() ?: "")
            val encodedTitle = android.net.Uri.encode(asset.title)
            return "preview?storyId=-1&previewUri=$encodedUri&previewTitle=$encodedTitle"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryApp(container: AppContainer) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = {
            val currentRoute = currentBackStackEntry?.destination?.route
            val canNavigateBack = navController.previousBackStackEntry != null
            TopAppBar(
                title = { Text(titleForRoute(currentRoute)) },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            val currentRoute = currentBackStackEntry?.destination?.route
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    label = { Text("Create") },
                    selected = currentRoute == StoryRoute.Create.route,
                    onClick = {
                        navController.navigate(StoryRoute.Create.route) {
                            launchSingleTop = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Collections, contentDescription = null) },
                    label = { Text("Assets") },
                    selected = currentRoute == StoryRoute.Assets.route,
                    onClick = {
                        navController.navigate(StoryRoute.Assets.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = StoryRoute.Create.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(StoryRoute.Create.route) {
                val vm: com.nm.story2mv.ui.viewmodel.CreateViewModel = viewModel(
                    factory = ViewModelFactories.createStoryFactory(container.storyRepository)
                )
                val state = vm.uiState.collectAsStateWithLifecycle().value
                LaunchedEffect(state.createdStoryId) {
                    state.createdStoryId?.let { id ->
                        vm.consumeNavigation()
                        navController.navigate(StoryRoute.Storyboard.build(id))
                    }
                }
                CreateScreen(
                    state = state,
                    onSynopsisChanged = vm::onSynopsisChanged,
                    onStyleSelected = vm::onStyleSelected,
                    onGenerate = vm::generateStory
                )
            }

            composable(
                route = StoryRoute.Storyboard.route,
                arguments = listOf(navArgument(StoryRoute.Storyboard.ARG) { type = NavType.LongType })
            ) { backStackEntry ->
                val vm: com.nm.story2mv.ui.viewmodel.StoryboardViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = ViewModelFactories.storyboardFactory(container.storyRepository)
                )
                val state = vm.uiState.collectAsStateWithLifecycle().value
                LaunchedEffect(state.toastMessage) {
                    state.toastMessage?.let { message ->
                        snackbarHostState.showSnackbar(message)
                        vm.clearToast()
                    }
                }
                LaunchedEffect(state.autoPreviewStoryId) {
                    state.autoPreviewStoryId?.let { storyId ->
                        vm.consumeAutoPreview()
                        snackbarHostState.showSnackbar("合成完成，即将跳转至预览")
                        navController.navigate(StoryRoute.Preview.buildForStory(storyId))
                    }
                }
                StoryboardScreen(
                    state = state.project,
                    isLoading = state.isLoading,
                    isRefreshing = state.isRefreshing,
                    errorMessage = state.errorMessage,
                    onShotDetail = { shot ->
                        navController.navigate(
                            StoryRoute.ShotDetail.build(shot.storyId, shot.id)
                        )
                    },
                    onPreview = {
                        state.project?.id?.let {
                            navController.navigate(StoryRoute.Preview.buildForStory(it))
                        }
                    },
                    onGenerateVideo = vm::generateVideo,
                    onRetry = vm::refresh
                )
            }

            composable(
                route = StoryRoute.ShotDetail.route,
                arguments = listOf(
                    navArgument(StoryRoute.ShotDetail.STORY_ID) { type = NavType.LongType },
                    navArgument(StoryRoute.ShotDetail.SHOT_ID) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val vm: com.nm.story2mv.ui.viewmodel.ShotDetailViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = ViewModelFactories.shotDetailFactory(container.storyRepository)
                )
                val state = vm.uiState.collectAsStateWithLifecycle().value
                ShotDetailScreen(
                    state = state,
                    onPromptChanged = vm::updatePrompt,
                    onNarrationChanged = vm::updateNarration,
                    onTransitionChanged = vm::updateTransition,
                    onGenerateImage = vm::regenerateImage,
                    onSave = vm::saveShot,
                    onBack = { navController.popBackStack() },
                    onRetry = vm::reloadShot
                )
            }

            composable(StoryRoute.Assets.route) {
                val vm: com.nm.story2mv.ui.viewmodel.AssetsViewModel = viewModel(
                    factory = ViewModelFactories.assetsFactory(container.storyRepository)
                )
                AssetsScreen(
                    state = vm.uiState.collectAsStateWithLifecycle().value,
                    onQueryChanged = vm::updateQuery,
                    onAssetSelected = { asset ->
                        navController.navigate(StoryRoute.Preview.buildForAsset(asset))
                    },
                    onAssetPlay = { asset ->
                        navController.navigate(StoryRoute.Preview.buildForAsset(asset))
                    },
                    onAssetDelete = vm::requestDeleteAsset,
                    onConfirmDelete = vm::confirmDeleteAsset,
                    onCancelDelete = vm::cancelDeleteAsset
                )
            }

            composable(
                route = StoryRoute.Preview.route,
                arguments = listOf(
                    navArgument(StoryRoute.Preview.STORY_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(StoryRoute.Preview.PREVIEW_URI) {
                        type = NavType.StringType
                        nullable = true
                    },
                    navArgument(StoryRoute.Preview.PREVIEW_TITLE) {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) { backStackEntry ->
                val vm: com.nm.story2mv.ui.viewmodel.PreviewViewModel = viewModel(
                    viewModelStoreOwner = backStackEntry,
                    factory = ViewModelFactories.previewFactory(
                        repository = container.storyRepository,
                        exportManager = container.videoExportManager
                    )
                )
                PreviewScreen(
                    state = vm.uiState.collectAsStateWithLifecycle().value,
                    onSave = vm::saveVideo,
                    onExport = vm::exportVideo,
                    onExportResultConsumed = vm::consumeExportResult,
                    onSelectIndex = vm::playAt
                )
            }
        }
    }
}

private fun titleForRoute(route: String?): String = when {
    route == null -> "Story2MV"
    route.startsWith(StoryRoute.Create.route) -> "StoryFlow"
    route.startsWith("storyboard") -> "Storyboard"
    route.startsWith("shotDetail") -> "分镜详情"
    route.startsWith(StoryRoute.Assets.route) -> "资产库"
    route.startsWith("preview") -> "成品预览"
    else -> "Story2MV"
}
