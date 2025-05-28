package com.example.tetrisprojem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.tetrisprojem.data.Level
import com.example.tetrisprojem.data.Mission
import com.example.tetrisprojem.data.MissionType
import com.example.tetrisprojem.data.Theme
import com.example.tetrisprojem.repository.GameRepository
import com.example.tetrisprojem.screens.LoginScreen
import com.example.tetrisprojem.screens.MultiplayerGameLobby
import com.example.tetrisprojem.screens.GameRoomScreen
import com.example.tetrisprojem.ui.theme.TetrisGameScreen
import com.example.tetrisprojem.ui.theme.TetrisProjemTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.graphics.toArgb
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

// GameLevels objesini import etmeyi unutma
import com.example.tetrisprojem.data.GameLevels
// GameResult sealed class'ını import etmeyi unutma
import com.example.tetrisprojem.data.GameResult


// Uygulama ekranlarını temsil eden sealed class
sealed class Screen {
    object Login : Screen()
    object Lobby : Screen()
    data class GameRoom(val roomId: String, val isPlayer1: Boolean) : Screen()
    data class SinglePlayer(val level: Level) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            TetrisProjemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenContent()
                }
            }
        }
    }
}

@Composable
fun MainScreenContent() {
    val auth = FirebaseAuth.getInstance()
    var currentUserState by remember { mutableStateOf<FirebaseUser?>(null) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }
    var currentRoomId by remember { mutableStateOf<String?>(null) }
    var isPlayer1InRoom by remember { mutableStateOf<Boolean?>(null) }

    var currentSinglePlayerLevel by remember { mutableStateOf(GameLevels.BEGINNER_LEVEL) }

    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUserState = firebaseAuth.currentUser
            if (currentUserState != null && currentScreen == Screen.Login) {
                currentScreen = Screen.Lobby
            } else if (currentUserState == null && currentScreen != Screen.Login) {
                currentScreen = Screen.Login
            }
        }
        auth.addAuthStateListener(authStateListener)
        onDispose {
            auth.removeAuthStateListener(authStateListener)
        }
    }

    val firestore = remember { FirebaseFirestore.getInstance() }
    val gameRepository = remember { GameRepository(firestore) }

    when (currentScreen) {
        Screen.Login -> {
            LoginScreen { userId ->
                // Bu callback kullanılmıyor çünkü authStateListener zaten geçişi yönetecek.
            }
        }
        Screen.Lobby -> {
            currentUserState?.let { firebaseUser ->
                MultiplayerGameLobby(
                    userId = firebaseUser.uid,
                    gameRepository = gameRepository,
                    coroutineScope = coroutineScope,
                    onRoomCreated = { roomId ->
                        currentRoomId = roomId
                        isPlayer1InRoom = true
                        currentScreen = Screen.GameRoom(roomId, true)
                    },
                    onRoomJoined = { roomId, isPlayer1 ->
                        currentRoomId = roomId
                        isPlayer1InRoom = isPlayer1
                        currentScreen = Screen.GameRoom(roomId, isPlayer1)
                    },
                    onSinglePlayerClick = { level ->
                        currentSinglePlayerLevel = level
                        currentScreen = Screen.SinglePlayer(level)
                    }
                )
            } ?: run {
                currentScreen = Screen.Login
            }
        }
        is Screen.GameRoom -> {
            val roomId = (currentScreen as Screen.GameRoom).roomId
            val isPlayer1 = (currentScreen as Screen.GameRoom).isPlayer1
            val userId = currentUserState?.uid ?: "unknown"

            GameRoomScreen(
                roomId = roomId,
                currentUserId = userId,
                isPlayer1 = isPlayer1,
                gameRepository = gameRepository,
                onGameEnd = {
                    currentRoomId = null
                    isPlayer1InRoom = null
                    currentScreen = Screen.Lobby // Multiplayer oyunu bittiğinde lobiye dön
                }
            )
        }
        is Screen.SinglePlayer -> {
            val level = (currentScreen as Screen.SinglePlayer).level
            TetrisGameScreen(
                level = level,
                initialPlayerState = null,
                onPlayerStateChange = null,
                onGameEnd = { gameResult ->
                    when (gameResult) {
                        is GameResult.PlayAgain -> {
                            currentScreen = Screen.SinglePlayer(level)
                        }
                        is GameResult.NextLevel -> {
                            val currentLevelIndex = GameLevels.ALL_LEVELS.indexOfFirst { it.id == level.id }
                            if (currentLevelIndex != -1 && currentLevelIndex + 1 < GameLevels.ALL_LEVELS.size) {
                                val nextLevel = GameLevels.ALL_LEVELS[currentLevelIndex + 1]
                                currentSinglePlayerLevel = nextLevel
                                currentScreen = Screen.SinglePlayer(nextLevel)
                            } else {
                                currentScreen = Screen.Lobby
                            }
                        }
                        is GameResult.MainMenu -> {
                            currentScreen = Screen.Lobby
                        }
                        is GameResult.Failed, is GameResult.Completed -> {
                            // Oyun sona erdi (başarısız veya tamamlandı).
                            // TetrisGameScreen'in kendi Game Over/Completed ekranını göstermesine izin veriyoruz.
                            // Kullanıcı oradaki butonlara basana kadar ekran değişmeyecek.
                            // Bu blokta herhangi bir ekran geçişi yapmıyoruz.
                        }
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TetrisProjemTheme {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("MainScreenContent Preview")
        }
    }
}