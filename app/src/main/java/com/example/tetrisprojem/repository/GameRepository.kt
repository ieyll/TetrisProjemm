package com.example.tetrisprojem.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.example.tetrisprojem.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FieldValue
import com.example.tetrisprojem.logic.generateRandomBlockSequence // Yeni import

class GameRepository(private val firestore: FirebaseFirestore) {

    private val roomsCollection = firestore.collection("gameRooms")

    suspend fun createGameRoom(player1Id: String, level: Level): Result<String> { // level parametresi Level tipinde
        return try {
            val roomId = roomsCollection.document().id
            // Yeni: Oyun odası için rastgele blok sırası oluştur
            val blockSequence = generateRandomBlockSequence(200) // Örneğin 200 blokluk bir sıra
            val newRoom = GameRoom(
                roomId = roomId,
                player1Id = player1Id,
                player1State = PlayerState(
                    userId = player1Id,
                    level = level.id, // PlayerState içinde Level'ın id'sini tut
                    currentBlockSequenceIndex = 0 // Başlangıç indeksi
                ),
                status = "waiting",
                level = level, // Odaya seçilen Level objesini kaydet
                blockSequence = blockSequence // Oluşturulan blok sırasını odaya kaydet
            )
            roomsCollection.document(roomId).set(newRoom).await()
            Result.success(roomId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGameRoom(roomId: String, player2Id: String): Result<Unit> {
        return try {
            val roomRef = roomsCollection.document(roomId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(roomRef)
                val room = snapshot.toObject(GameRoom::class.java)

                if (room == null) {
                    throw Exception("Oda bulunamadı.")
                }
                if (room.player2Id != null && room.player2Id != player2Id) {
                    throw Exception("Oda dolu.")
                }
                if (room.player1Id == player2Id) {
                    throw Exception("Aynı oyuncu iki kez katılamaz.")
                }

                // Odaya katılırken, odaya kaydedilmiş olan seviye bilgisini kullanmalıyız.
                // Bu sayede player2'nin başlangıç seviyesi de player1'in seçtiği seviye olacaktır.
                val player2InitialLevelId = room.level.id
                val updatedRoom = room.copy(
                    player2Id = player2Id,
                    player2State = PlayerState(
                        userId = player2Id,
                        level = player2InitialLevelId,
                        currentBlockSequenceIndex = 0 // Başlangıç indeksi
                    )
                )
                transaction.set(roomRef, updatedRoom)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeGameRoom(roomId: String): Flow<GameRoom?> = callbackFlow {
        val listenerRegistration = roomsCollection.document(roomId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val gameRoom = snapshot.toObject(GameRoom::class.java)
                        trySend(gameRoom)
                    } catch (parseException: Exception) {
                        println("GameRoom parse hatası: ${parseException.message}")
                        close(parseException)
                    }
                } else {
                    trySend(null)
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun updatePlayerState(
        roomId: String,
        isPlayer1: Boolean,
        playerState: PlayerState
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val playerField = if (isPlayer1) "player1State" else "player2State"
            roomsCollection.document(roomId)
                .update(playerField, playerState)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGameRoomStatus(roomId: String, newStatus: String, winnerId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val updates = mutableMapOf<String, Any>("status" to newStatus)
            winnerId?.let { updates["winnerId"] = it }
            roomsCollection.document(roomId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeWaitingRooms(): Flow<List<GameRoom>> = callbackFlow {
        val listenerRegistration = roomsCollection
            .whereEqualTo("status", "waiting")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    println("Bekleyen odalar dinlenirken hata: ${e.message}")
                    return@addSnapshotListener
                }
                val rooms = snapshot?.documents?.mapNotNull { it.toObject(GameRoom::class.java) } ?: emptyList()
                trySend(rooms)
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun deleteGameRoom(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            roomsCollection.document(roomId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            println("Oda silinirken hata: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun leaveGameRoom(roomId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val roomRef = roomsCollection.document(roomId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(roomRef)
                val room = snapshot.toObject(GameRoom::class.java)

                if (room == null) {
                    throw Exception("Oda bulunamadı veya silindi.")
                }

                val updates = mutableMapOf<String, Any?>()
                var otherPlayerExists = false

                if (room.player1Id == userId) {
                    updates["player1Id"] = null
                    updates["player1State"] = null
                    if (room.player2Id != null) {
                        otherPlayerExists = true
                    }
                } else if (room.player2Id == userId) {
                    updates["player2Id"] = null
                    updates["player2State"] = null
                    if (room.player1Id != null) {
                        otherPlayerExists = true
                    }
                } else {
                    return@runTransaction // Oyuncu bu odada değil, işlem yapma
                }

                if (updates.isNotEmpty()) {
                    if (!otherPlayerExists) {
                        transaction.delete(roomRef)
                        println("Oda tamamen boşaldığı için silindi: $roomId")
                    } else {
                        transaction.update(roomRef, updates)
                        println("Oyuncu $userId odadan ayrıldı, state güncellendi: $roomId")
                    }
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            println("Odadan ayrılırken hata: ${e.message}")
            Result.failure(e)
        }
    }
}