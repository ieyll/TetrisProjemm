package com.example.tetrisprojem.logic

import com.example.tetrisprojem.data.FallingPiece
import com.example.tetrisprojem.data.Point
import com.example.tetrisprojem.data.Mission
import com.example.tetrisprojem.data.MissionType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random

// Oyun tahtasının boyutları
const val BOARD_COLS = 10
const val BOARD_ROWS = 20

// Tetris bloklarının şekillerini döndüren yardımcı fonksiyon
fun getBlockShapeById(shapeId: String): List<Point> {
    return when (shapeId) {
        "I" -> listOf(Point(0, 1), Point(1, 1), Point(2, 1), Point(3, 1))
        "O" -> listOf(Point(0, 0), Point(1, 0), Point(0, 1), Point(1, 1))
        "T" -> listOf(Point(0, 0), Point(1, 0), Point(2, 0), Point(1, 1))
        "S" -> listOf(Point(1, 0), Point(2, 0), Point(0, 1), Point(1, 1))
        "Z" -> listOf(Point(0, 0), Point(1, 0), Point(1, 1), Point(2, 1))
        "J" -> listOf(Point(0, 0), Point(0, 1), Point(1, 1), Point(2, 1))
        "L" -> listOf(Point(2, 0), Point(0, 1), Point(1, 1), Point(2, 1))
        else -> throw IllegalArgumentException("Bilinmeyen blok şekli ID'si: $shapeId")
    }
}

// Her blok tipine özgü rengi döndüren yeni fonksiyon
fun getBlockColorByShapeId(shapeId: String): Int {
    return when (shapeId) {
        "I" -> Color(0xFF00FFFF).toArgb() // Cyan
        "O" -> Color(0xFFFFFF00).toArgb() // Yellow
        "T" -> Color(0xFF800080).toArgb() // Purple
        "S" -> Color(0xFF00FF00).toArgb() // Green
        "Z" -> Color(0xFFFF0000).toArgb() // Red
        "J" -> Color(0xFF0000FF).toArgb() // Blue
        "L" -> Color(0xFFFFA500).toArgb() // Orange
        else -> Color.Gray.toArgb() // Bilinmeyen durumlar için varsayılan gri
    }
}

// YENİ EKLENDİ: Rastgele bir blok sırası oluşturan fonksiyon (multiplayer başlangıcı için)
fun generateRandomBlockSequence(length: Int): List<String> {
    val shapes = listOf("I", "O", "T", "S", "Z", "J", "L")
    return List(length) { shapes.random() }
}

// GÜNCELLENDİ: Yeni bir düşen parça oluşturur.
// Artık oyuncunun kendi sırasını takip etmesi için blockSequence ve o anki indeksi alır.
fun generateNextFallingPiece(
    blockSequence: List<String>? = null, // Tek oyunculu modda null olabilir
    playerBlockSequenceIndex: Int = 0 // Oyuncunun kendi sırasındaki mevcut indeksi
): FallingPiece {
    val shapes = listOf("I", "O", "T", "S", "Z", "J", "L") // Fallback için

    val shapeIdToUse: String
    if (blockSequence != null && blockSequence.isNotEmpty() && playerBlockSequenceIndex < blockSequence.size) {
        shapeIdToUse = blockSequence[playerBlockSequenceIndex] // Ortak sıradan oyuncunun kendi indeksiyle al
    } else {
        // Eğer blockSequence null (tek oyunculu mod) veya biterse (çok uzun bir oyun), rastgele blok üretmeye devam et.
        shapeIdToUse = shapes.random()
        if (blockSequence != null && playerBlockSequenceIndex >= blockSequence.size) {
            println("Uyarı: Ortak blok sırası tükendi, rastgele blok üretiliyor.")
        }
    }

    val shape = getBlockShapeById(shapeIdToUse)
    val pieceColor = getBlockColorByShapeId(shapeIdToUse)
    val startX = (BOARD_COLS / 2) - 2

    return FallingPiece(
        shapeId = shapeIdToUse,
        shape = shape,
        color = pieceColor,
        position = Point(startX, 0)
    )
}

// Tahtada çarpışma kontrolü yapar
fun checkCollision(board: List<List<Int>>, piece: FallingPiece, deltaX: Int, deltaY: Int): Boolean {
    piece.shape.forEach { point ->
        val newX = piece.position.x + point.x + deltaX
        val newY = piece.position.y + point.y + deltaY

        if (newX < 0 || newX >= BOARD_COLS || newY >= BOARD_ROWS) {
            return true // Yan veya alt sınırı ihlal etti
        }
        if (newY >= 0 && board[newY][newX] != 0) {
            return true // Dolu hücreye çarpışma
        }
    }
    return false
}

// Bloğu hareket ettirir
fun moveBlock(piece: FallingPiece, deltaX: Int, deltaY: Int): FallingPiece {
    return piece.copy(position = Point(piece.position.x + deltaX, piece.position.y + deltaY))
}

// Bloğu döndürür
fun rotateBlock(piece: FallingPiece): FallingPiece {
    if (piece.shapeId == "O") {
        return piece
    }

    val pivot = when (piece.shapeId) {
        "I" -> Point(1, 1)
        else -> piece.shape[1]
    }

    val rotatedShape = piece.shape.map { point ->
        val relativeX = point.x - pivot.x
        val relativeY = point.y - pivot.y

        val newRelativeX = -relativeY
        val newRelativeY = relativeX

        Point(pivot.x + newRelativeX, pivot.y + newRelativeY)
    }
    return piece.copy(shape = rotatedShape)
}

// Bloğu tahtaya sabitler ve yeni bir tahta döndürür
fun addToBoard(currentBoard: List<List<Int>>, piece: FallingPiece): MutableList<MutableList<Int>> {
    val newBoard = currentBoard.map { it.toMutableList() }.toMutableList()
    piece.shape.forEach { point ->
        val x = piece.position.x + point.x
        val y = piece.position.y + point.y
        if (y >= 0 && y < BOARD_ROWS && x >= 0 && x < BOARD_COLS) {
            newBoard[y][x] = piece.color
        }
    }
    return newBoard
}

// Dolu satırları temizler ve yukarı kaydırır, temizlenen satır sayısını döndürür
fun clearLines(board: MutableList<MutableList<Int>>): Int {
    var clearedLines = 0
    val rowsToKeep = mutableListOf<MutableList<Int>>()

    board.forEach { row ->
        if (row.any { it == 0 }) {
            rowsToKeep.add(row)
        } else {
            clearedLines++
        }
    }

    repeat(clearedLines) {
        rowsToKeep.add(0, MutableList(BOARD_COLS) { 0 })
    }

    board.clear()
    board.addAll(rowsToKeep)
    return clearedLines
}

// Skoru hesaplar
fun calculateScore(clearedLines: Int): Int {
    return when (clearedLines) {
        1 -> 100
        2 -> 300
        3 -> 500
        4 -> 800 // Tetris!
        else -> 0
    }
}

// Tahtayı 2D'den 1D'ye dönüştürür (Firebase için)
fun convert2DTo1D(board: List<List<Int>>): List<Int> {
    return board.flatten()
}

// Tahtayı 1D'den 2D'ye dönüştürür
fun convert1DTo2D(grid: List<Int>, rows: Int, cols: Int): MutableList<MutableList<Int>> {
    val board = MutableList(rows) { MutableList(cols) { 0 } }
    grid.forEachIndexed { index, value ->
        val row = index / cols
        val col = index % cols
        if (row < rows && col < cols) {
            board[row][col] = value
        }
    }
    return board
}

// Başlangıç tahtasına engeller ekler
fun initializeBoardWithObstacles(board: MutableList<MutableList<Int>>, numObstacles: Int): MutableList<MutableList<Int>> {
    val obstacleBoard = board.map { it.toMutableList() }.toMutableList()
    val random = Random(System.currentTimeMillis())

    repeat(numObstacles) {
        val row = random.nextInt(BOARD_ROWS / 2, BOARD_ROWS - 1)
        val col = random.nextInt(0, BOARD_COLS)
        obstacleBoard[row][col] = Color.DarkGray.toArgb()
    }
    return obstacleBoard
}

// Görevleri güncelleme fonksiyonları (Bu fonksiyonlar aynı kalabilir)
fun updateMissionsOnLineClear(clearedLines: Int, currentMissions: MutableList<Mission>, totalClearedLines: Int) {
    currentMissions.forEach { mission ->
        if (!mission.isCompleted) {
            when (mission.type) {
                MissionType.CLEAR_LINES -> {
                    if (totalClearedLines >= mission.targetValue) {
                        mission.isCompleted = true
                    }
                }
                else -> { /* Diğer görev tipleri burada işlenmez */ }
            }
        }
    }
}

fun updateMissionsOnScore(currentScore: Int, currentMissions: MutableList<Mission>) {
    currentMissions.forEach { mission ->
        if (!mission.isCompleted) {
            when (mission.type) {
                MissionType.REACH_SCORE -> {
                    if (currentScore >= mission.targetValue) {
                        mission.isCompleted = true
                    }
                }
                else -> { /* Diğer görev tipleri burada işlenmez */ }
            }
        }
    }
}