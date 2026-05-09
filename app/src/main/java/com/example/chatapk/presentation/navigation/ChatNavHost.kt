package com.example.chatapk.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.chatapk.di.AppContainer
import com.example.chatapk.presentation.auth.AuthScreen
import com.example.chatapk.presentation.auth.AuthViewModel
import com.example.chatapk.presentation.chat.ChatScreen
import com.example.chatapk.presentation.chat.ChatViewModel
import com.example.chatapk.presentation.chatlist.ChatListScreen
import com.example.chatapk.presentation.chatlist.ChatListViewModel

private object Routes {
    const val AUTH = "auth"
    const val CHATS = "chats"
    const val CHAT = "chat/{chatId}/{receiverId}"

    fun chat(chatId: String, receiverId: String) = "chat/$chatId/$receiverId"
}

@Composable
fun ChatNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val authUserId by container.authRepository.authState().collectAsState(initial = container.authRepository.currentUserId)
    val startRoute = if (authUserId == null) Routes.AUTH else Routes.CHATS

    LaunchedEffect(authUserId) {
        if (authUserId == null) {
            navController.navigate(Routes.AUTH) {
                popUpTo(0)
            }
        } else {
            navController.navigate(Routes.CHATS) {
                popUpTo(0)
            }
        }
    }

    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.AUTH) {
            val vm: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(container.authRepository)
            )
            AuthScreen(viewModel = vm)
        }
        composable(Routes.CHATS) {
            val vm: ChatListViewModel = viewModel(
                factory = ChatListViewModel.Factory(
                    container.authRepository,
                    container.chatRepository,
                    container.userRepository,
                    container.preferenceRepository,
                    container.updateRepository
                )
            )
            ChatListScreen(
                viewModel = vm,
                onOpenChat = { chatId, receiverId ->
                    navController.navigate(Routes.chat(chatId, receiverId))
                }
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("receiverId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatId = requireNotNull(backStackEntry.arguments?.getString("chatId"))
            val receiverId = requireNotNull(backStackEntry.arguments?.getString("receiverId"))
            val vm: ChatViewModel = viewModel(
                key = "$chatId-$receiverId",
                factory = ChatViewModel.Factory(
                    chatId = chatId,
                    receiverId = receiverId,
                    authRepository = container.authRepository,
                    chatRepository = container.chatRepository,
                    userRepository = container.userRepository,
                    preferenceRepository = container.preferenceRepository,
                    externalScope = container.applicationScope
                )
            )
            ChatScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
