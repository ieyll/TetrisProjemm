package com.example.tetrisprojem.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.tetrisprojem.data.GameRoom
import com.example.tetrisprojem.repository.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.tetrisprojem.data.GameLevels // GameLevels objesine erişim için
import com.example.tetrisprojem.data.Level // Level sınıfını import et

@Composable
fun MultiplayerGameLobby(
    userId: String,
    gameRepository: GameRepository,
    coroutineScope: CoroutineScope,
    onRoomCreated: (roomId: String) -> Unit,
    onRoomJoined: (roomId: String, isPlayer1: Boolean) -> Unit,
    onSinglePlayerClick: (Level) -> Unit
) {
    val waitingRooms by gameRepository.observeWaitingRooms().collectAsState(initial = emptyList())

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Tek bir seviye seçimi state'i tanımla
    var selectedLevel by remember { mutableStateOf(GameLevels.BEGINNER_LEVEL) } // Varsayılan olarak başlangıç seviyesi

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Oyun Lobisi", style = MaterialTheme.typography.headlineMedium) // Başlığı daha genel yaptım
        Spacer(modifier = Modifier.height(32.dp))

        // Ortak Seviye Seçimi UI'ı
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Seviye Seçimi:", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameLevels.ALL_LEVELS.forEach { levelOption ->
                    Button(
                        onClick = { selectedLevel = levelOption }, // Ortak state'i güncelle
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = if (selectedLevel.id == levelOption.id)
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        else
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(levelOption.name)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Yeni Oda Oluştur Butonu (Ortak selectedLevel'ı kullanır)
        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                coroutineScope.launch {
                    val result = gameRepository.createGameRoom(userId, selectedLevel) // Seçilen seviyeyi gönder
                    result.onSuccess { roomId ->
                        onRoomCreated(roomId)
                    }.onFailure { e ->
                        errorMessage = "Oda oluşturulamadı: ${e.message}"
                        println("Oda oluşturma hatası: ${e.message}")
                    }
                    isLoading = false
                }
            },
            enabled = !isLoading
        ) {
            Text("Yeni Oda Oluştur (Çok Oyunculu)") // Buton metnini açıklaştırdım
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Mevcut Odalar Listesi
        Text("Katılabileceğin Odalar", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else if (errorMessage != null) {
            Text("Hata: $errorMessage", color = Color.Red)
        } else if (waitingRooms.isEmpty()) {
            Text("Şu an katılabilecek boş oda yok. Bir tane oluştur!")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(waitingRooms) { room ->
                    RoomItem(room = room, userId = userId) { selectedRoom ->
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            val result = gameRepository.joinGameRoom(selectedRoom.roomId, userId)
                            result.onSuccess {
                                onRoomJoined(selectedRoom.roomId, false)
                            }.onFailure { e ->
                                errorMessage = "Odaya katılamadı: ${e.message}"
                                println("Odaya katılma hatası: ${e.message}")
                            }
                            isLoading = false
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        // Tek Oyunculu Mod Butonu (Ortak selectedLevel'ı kullanır)
        Button(
            onClick = {
                onSinglePlayerClick(selectedLevel) // Seçilen seviyeyi callback'e gönder
            },
            enabled = !isLoading
        ) {
            Text("Misafir Olarak Oyna (Tek Oyunculu)")
        }
    }
}

@Composable
fun RoomItem(room: GameRoom, userId: String, onJoinClick: (GameRoom) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                if (room.player1Id != userId && room.player2Id == null) {
                    onJoinClick(room)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Oda ID: ${room.roomId.substring(0, 6)}...")
            Text("Oluşturan: ${room.player1Id?.substring(0, 5)}...")
            Text("Seviye: ${room.level.name}") // Odanın seviyesini göster
            if (room.player2Id == null) {
                Text("Durum: Oyuncu bekleniyor...", color = Color.Gray)
            } else {
                Text("Durum: Başlamış veya Dolu", color = Color.Gray)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MultiplayerGameLobbyPreview() {
    val previewCoroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Oyun Lobisi Önizlemesi", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {}) { Text("Yeni Oda Oluştur (Çok Oyunculu)") }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Katılabileceğin Odalar", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        RoomItem(
            room = GameRoom(roomId = "testRoom1", player1Id = "playerA", status = "waiting", level = GameLevels.BEGINNER_LEVEL),
            userId = "testUserB"
        ) {}
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {}) { Text("Misafir Olarak Oyna (Tek Oyunculu)") }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Seviye Seçimi Alanı", color = Color.White) // Preview için placeholder
    }
}