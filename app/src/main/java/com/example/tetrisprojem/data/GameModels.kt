package com.example.tetrisprojem.data

import androidx.compose.ui.graphics.Color // Color için import
import androidx.compose.ui.graphics.toArgb // toArgb() uzantı fonksiyonu için import

sealed class GameResult {
    data class Completed(val score: Int, val currentLevelId: String) : GameResult()
    data class Failed(val score: Int, val currentLevelId: String) : GameResult()
    object MainMenu : GameResult()
    object PlayAgain : GameResult()
    data class NextLevel(val currentLevelId: String) : GameResult()
}

// Nokta tanımı (blok pozisyonları için)
data class Point(val x: Int, val y: Int) {
    // Firebase için boş constructor
    constructor() : this(0, 0)
}

// Oyun tahtasının state'i (tek boyutlu liste olarak)
data class Board(
    val grid: List<Int> = emptyList() // 2D değil, 1D liste olarak tutulur
) {
    // Firebase için boş constructor
    constructor() : this(emptyList())
}

// Düşen bir blokun anlık state'i
data class FallingPiece(
    val shapeId: String = "", // Bloğun ID'si (örn: "I", "O", "T")
    val shape: List<Point> = emptyList(), // Bloğun şeklini oluşturan noktalar
    val color: Int = 0, // Bloğun rengi (ARGB Int değeri)
    val position: Point = Point(0, 0) // Bloğun tahtadaki sol üst konumu
) {
    // Firebase için boş constructor
    constructor() : this("", emptyList(), 0, Point())
}

// Firestore'a kaydedilecek blok state'i (FallingPiece'den basitleştirilmiş)
data class BlockState(
    val shapeId: String = "",
    val color: Int = 0,
    val row: Int = 0,
    val col: Int = 0
) {
    // Firebase için boş constructor
    constructor() : this("", 0, 0, 0)
}

// Oyuncu state'i (multiplayer için)
data class PlayerState(
    val userId: String = "", // Oyuncu ID'si
    val board: Board = Board(), // Oyuncunun tahtası
    val currentBlock: BlockState? = null, // Oyuncunun düşen bloğu
    val score: Int = 0,
    val isGameOver: Boolean = false,
    val level: String = "beginner", // Hangi seviyede oynadığını belirtir. (String olarak güncellendi)
    val currentBlockSequenceIndex: Int = 0 // YENİ EKLENDİ: Oyuncunun kendi taş sırası indeksi
) {
    // Firebase için boş constructor
    constructor() : this("")
}

// Görev tipleri
enum class MissionType {
    CLEAR_LINES, // Belirli sayıda satır temizleme
    REACH_SCORE, // Belirli bir skora ulaşma
    SURVIVE_TIME // Belirli bir süre hayatta kalma
}

// Oyun içi görev tanımı
data class Mission(
    val id: String = "",
    val type: MissionType = MissionType.CLEAR_LINES,
    val targetValue: Int = 0, // Hedef değer (örn: 10 satır, 500 skor, 60 saniye)
    val description: String = "", // Görevin açıklaması
    var isCompleted: Boolean = false // Görevin tamamlanıp tamamlanmadığı
) {
    // Firebase için boş constructor
    constructor() : this("", MissionType.CLEAR_LINES, 0, "", false)
}

// Oyun teması (arka plan, blok renkleri vb.)
data class Theme(
    val backgroundColor: Int = 0, // Arka plan rengi (ARGB Int)
    val blockColors: List<Int> = emptyList() // Blok renkleri listesi (ARGB Int)
) {
    // Firebase için boş constructor
    constructor() : this(0, emptyList())
}

// Oyun seviyesi tanımı
data class Level(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val startingSpeed: Long = 0L, // Başlangıç düşme hızı (ms cinsinden)
    val initialBoardObstacles: Int = 0, // Tahtadaki başlangıç engelleri
    val theme: Theme = Theme(), // Bu seviyenin teması
    val missions: List<Mission> = emptyList() // Bu seviyenin görevleri
) {
    // Firebase için boş constructor
    constructor() : this("", "", "", 0L, 0, Theme(), emptyList())
}

// Oyun odası tanımı (multiplayer için)
data class GameRoom(
    val roomId: String = "",
    val player1Id: String? = null,
    val player1State: PlayerState? = null,
    val player2Id: String? = null,
    val player2State: PlayerState? = null,
    val status: String = "waiting", // waiting, in_game, finished
    val winnerId: String? = null, // Oyun bittiğinde kazananın ID'si
    val level: Level = GameLevels.BEGINNER_LEVEL, // Odanın seviyesi
    val blockSequence: List<String> = emptyList() // YENİ EKLENDİ: Ortak blok sırası
) {
    // Boş constructor'ı da güncelle
    constructor() : this(
        roomId = "",
        player1Id = null,
        player1State = null,
        player2Id = null,
        player2State = null,
        status = "waiting",
        winnerId = null,
        level = GameLevels.BEGINNER_LEVEL, // Boş constructor'da da varsayılan bir seviye atadık
        blockSequence = emptyList()
    )
}

object GameLevels {
    val BEGINNER_LEVEL = Level(
        id = "beginner",
        name = "Başlangıç Seviyesi",
        description = "Temel Tetris deneyimi, daha yavaş düşme hızı.",
        startingSpeed = 800L,
        initialBoardObstacles = 0,
        theme = Theme(
            backgroundColor = Color(0xFF212121).toArgb(),
            blockColors = listOf(
                Color(0xFF00FFFF).toArgb(), // Cyan
                Color(0xFFFFFF00).toArgb(), // Yellow
                Color(0xFF800080).toArgb(), // Purple
                Color(0xFF00FF00).toArgb(), // Green
                Color(0xFFFF0000).toArgb(), // Red
                Color(0xFF0000FF).toArgb(), // Blue
                Color(0xFFFFA500).toArgb()  // Orange
            )
        ),
        missions = listOf(
            Mission(id = "bm1", type = MissionType.CLEAR_LINES, targetValue = 5, description = "5 satır temizle"),
            Mission(id = "bm2", type = MissionType.REACH_SCORE, targetValue = 200, description = "200 puana ulaş")
        )
    )

    val INTERMEDIATE_LEVEL = Level(
        id = "intermediate",
        name = "Orta Seviye",
        description = "Daha hızlı düşme ve bazı başlangıç engelleri.",
        startingSpeed = 600L,
        initialBoardObstacles = 5,
        theme = Theme(
            backgroundColor = Color(0xFF303030).toArgb(),
            blockColors = listOf(
                Color(0xFF00E0E0).toArgb(), // Slightly darker Cyan
                Color(0xFFE0E000).toArgb(), // Slightly darker Yellow
                Color(0xFF600060).toArgb(), // Slightly darker Purple
                Color(0xFF00E000).toArgb(), // Slightly darker Green
                Color(0xFFE00000).toArgb(), // Slightly darker Red
                Color(0xFF0000E0).toArgb(), // Slightly darker Blue
                Color(0xFFE08000).toArgb()  // Slightly darker Orange
            )
        ),
        missions = listOf(
            Mission(id = "im1", type = MissionType.CLEAR_LINES, targetValue = 15, description = "15 satır temizle"),
            Mission(id = "im2", type = MissionType.REACH_SCORE, targetValue = 750, description = "750 puana ulaş")
        )
    )

    val MULTIPLAYER_LEVEL = Level(
        id = "multiplayer",
        name = "Rekabetçi",
        description = "Özel görevlerle rekabetçi Tetris. Görevler bitse dahi oyun skorla devam eder.",
        startingSpeed = 550L, // Biraz daha hızlı
        initialBoardObstacles = 0,
        theme = Theme(
            backgroundColor = Color(0xFF1A1A1A).toArgb(), // Biraz daha koyu arka plan
            blockColors = listOf(
                Color(0xFF00C0C0).toArgb(), // Cyan
                Color(0xFFC0C000).toArgb(), // Yellow
                Color(0xFF804080).toArgb(), // Purple
                Color(0xFF00C000).toArgb(), // Green
                Color(0xFFC00000).toArgb(), // Red
                Color(0xFF0000C0).toArgb(), // Blue
                Color(0xFFC08000).toArgb()  // Orange
            )
        ),
        missions = listOf(
            Mission(id = "mm1_stage1_lines", type = MissionType.CLEAR_LINES, targetValue = 7, description = "7 satır temizle"),
            Mission(id = "mm2_stage1_score", type = MissionType.REACH_SCORE, targetValue = 700, description = "700 puana ulaş")
            // İkinci aşama görevi (SURVIVE_TIME) dinamik olarak eklenecek, Level objesine doğrudan eklemiyoruz
        )
    )

    val ALL_LEVELS = listOf(BEGINNER_LEVEL, INTERMEDIATE_LEVEL, MULTIPLAYER_LEVEL)
}