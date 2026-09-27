package com.parlo.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.parlo.app.ui.history.SessionDetailScreen
import com.parlo.app.ui.history.SessionHistoryScreen
import com.parlo.app.ui.main.MainScreen
import com.parlo.app.ui.vocab.VocabListScreen

object Routes {
    const val MAIN = "main"
    const val VOCAB = "vocab"
    const val HISTORY = "history"
    const val SESSION = "session/{id}"
    fun session(id: Long) = "session/$id"
}

@Composable
fun ParloNavHost(viewModel: MainViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                viewModel = viewModel,
                onOpenVocab = { nav.navigate(Routes.VOCAB) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.VOCAB) { VocabListScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HISTORY) {
            SessionHistoryScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.session(it)) })
        }
        composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            SessionDetailScreen(sessionId = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
    }
}
