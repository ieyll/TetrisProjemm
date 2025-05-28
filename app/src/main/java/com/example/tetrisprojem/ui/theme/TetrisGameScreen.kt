package com.example.tetrisprojem.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.tetrisprojem.data.*
import com.example.tetrisprojem.logic.*
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.material3.Button
import androidx.compose.ui.unit.times
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import com.example.tetrisprojem.data.GameLevels
import kotlinx.coroutines.Job // Job import'ı
import kotlinx.coroutines.launch

@Composable
fun TetrisGameScreen(
    level: Level,
    initialPlayerState: PlayerState?,
    onPlayerStateChange: ((PlayerState) -> Unit)?,
    onGameEnd: (GameResult) -> Unit,
    blockSequence: List<String>? = null // Multiplayer için opsiyonel blok sırası
) {
    var currentBoard by remember { mutableStateOf(MutableList(BOARD_ROWS) { MutableList(BOARD_COLS) { 0 } }) }
    var currentPiece by remember { mutableStateOf<FallingPiece?>(null) }
    var score by remember { mutableStateOf(0) }
    var currentLevelSpeed by remember { mutableStateOf(level.startingSpeed) }
    var isGameOver by remember { mutableStateOf(false) } // Bu sadece o oyuncunun Game Over olup olmadığını belirtir
    var isGamePaused by remember { mutableStateOf(false) }
    var totalClearedLines by remember { mutableStateOf(0) }
    val currentMissions = remember { mutableStateListOf(*level.missions.toTypedArray()) }

    // ... diğer state'ler


// ...
    // Multiplayer için ek state'ler
    var playerBlockSequenceIndex by remember { mutableStateOf(initialPlayerState?.currentBlockSequenceIndex ?: 0) }
    var dynamicMissionTimeLeft by remember { mutableStateOf(0) } // SURVIVE_TIME görevi için sayaç
    var dynamicMissionJob by remember { mutableStateOf<Job?>(null) } // Sayaç coroutine'ini kontrol etmek için

    // Oyunu sıfırlama fonksiyonu
    val resetGame: () -> Unit = remember(level, blockSequence) {
        {
            currentBoard = initializeBoardWithObstacles(
                MutableList(BOARD_ROWS) { MutableList(BOARD_COLS) { 0 } },
                level.initialBoardObstacles
            )
            currentLevelSpeed = level.startingSpeed
            score = 0
            totalClearedLines = 0
            isGameOver = false
            currentMissions.clear()
            currentMissions.addAll(level.missions.map { it.copy(isCompleted = false) }) // Görevleri de sıfırla

            // Eğer multiplayer ise blockSequence indexini sıfırla ve ilk bloğu ona göre al
            if (initialPlayerState != null && blockSequence != null) {
                playerBlockSequenceIndex = 0 // Sıfırlıyoruz
                // Yeni bir parça oluştururken çarpışma kontrolü yapalım
                val newPiece = generateNextFallingPiece(blockSequence, playerBlockSequenceIndex)
                if (checkCollision(currentBoard, newPiece, 0, 0)) {
                    isGameOver = true // Oyun zaten başlamadan bitti
                    // Multiplayer ise GameRoomScreen bu durumu Firestore'a yansıtabilir.
                    // Tek oyunculu ise, Game Over ekranı hemen gösterilir.
                    onGameEnd(GameResult.Failed(score, level.id)) // Oyun bittiğini bildir
                } else {
                    currentPiece = newPiece
                    playerBlockSequenceIndex++
                }
            } else {
                // Tek oyunculu için rastgele parça oluştur ve çarpışma kontrolü yap
                val newPiece = generateNextFallingPiece()
                if (checkCollision(currentBoard, newPiece, 0, 0)) {
                    isGameOver = true // Oyun zaten başlamadan bitti
                    onGameEnd(GameResult.Failed(score, level.id)) // Oyun bittiğini bildir
                } else {
                    currentPiece = newPiece
                }
            }

            dynamicMissionTimeLeft = 0 // Sayaç sıfırla
            dynamicMissionJob?.cancel() // Varsa önceki sayacı durdur
            dynamicMissionJob = null

            println("Oyun sıfırlandı: Level: ${level.name}, Multiplayer: ${initialPlayerState != null}")
        }
    }

    // İlk başlatmada ve level değiştiğinde oyunu başlat
    LaunchedEffect(level) {
        resetGame()
    }

    // Multiplayer modda initialPlayerState değiştiğinde yerel durumu güncelle
    LaunchedEffect(initialPlayerState) {
        initialPlayerState?.let { playerState ->
            currentBoard = convert1DTo2D(playerState.board.grid, BOARD_ROWS, BOARD_COLS)
            score = playerState.score
            isGameOver = playerState.isGameOver // Oyuncunun Firebase'deki isGameOver durumunu al
            playerBlockSequenceIndex = playerState.currentBlockSequenceIndex // Oyuncunun blok sırası indeksini güncelle

            // Multiplayer modda parça senkronizasyonu
            val firebasePiece = playerState.currentBlock?.let { block ->
                FallingPiece(
                    shapeId = block.shapeId,
                    shape = getBlockShapeById(block.shapeId),
                    color = block.color,
                    position = Point(block.col, block.row) // Düzeltme burada! block.x yerine block.col, block.y yerine block.row
                )
            }

            // Sadece currentPiece null ise veya Firebase'den gelen parça ile eşleşmiyorsa
            // (yani Firebase'den yeni bir parça gelmiş veya bir senkronizasyon hatası oluşmuşsa)
            // Firebase'den gelenle güncelleriz.
            // Aksi takdirde, kullanıcının yerel olarak yaptığı hareketi koruruz.
            // ÖNEMLİ: currentPiece ile firebasePiece'i doğrudan karşılaştırmak, FallingPiece içinde equals metodu tanımlanmamışsa
            // referans karşılaştırması yapacaktır. Bu nedenle, içeriksel bir eşitlik kontrolü yapmalıyız.
            if (currentPiece == null || currentPiece?.shapeId != firebasePiece?.shapeId ||
                currentPiece?.position != firebasePiece?.position ||
                currentPiece?.color != firebasePiece?.color) { // Basit bir içeriksel kontrol
                currentPiece = firebasePiece
            }


            // Multiplayer görevleri için: Eğer ilk aşama görevleri tamamlandıysa ikinci aşama görevi ekle
            if (level.id == GameLevels.MULTIPLAYER_LEVEL.id &&
                currentMissions.none { it.id == "mm3_stage2_time" } && // Henüz eklenmediyse
                currentMissions.all { it.isCompleted } // Ve ilk aşama görevleri tamamlandıysa
            ) {
                currentMissions.add(Mission(id = "mm3_stage2_time", type = MissionType.SURVIVE_TIME, targetValue = 40, description = "40 saniye hayatta kal", isCompleted = false))
            }
        }
    }


    // Oyun döngüsü (blok düşürme)
    LaunchedEffect(isGameOver) { // isGameOver değiştiğinde bu LaunchedEffect yeniden tetiklenmeli
        if (!isGameOver) { // Oyun bitmemişse döngüyü çalıştır
            val gameLoopInterval = currentLevelSpeed // Blok düşme hızı

            // Eğer multiplayer seviyedeysek ve zaman görevimiz varsa sayacı başlat
            // Eğer multiplayer seviyedeysek ve zaman görevimiz varsa sayacı başlat
            if (level.id == GameLevels.MULTIPLAYER_LEVEL.id) {
                val surviveMission = currentMissions.find { it.type == MissionType.SURVIVE_TIME && !it.isCompleted }
                if (surviveMission != null && dynamicMissionJob == null) {
                    dynamicMissionTimeLeft = surviveMission.targetValue // Sayacı başlat
                    dynamicMissionJob = launch {
                        while (dynamicMissionTimeLeft > 0 && isActive && !isGameOver && !surviveMission.isCompleted && !isGamePaused) { // isGamePaused eklendi
                            delay(1000L)
                            if (!isGameOver && !isGamePaused) { // Ekstra kontrol, oyun döngüsü sırasında Game Over veya Pause olursa sayacı durdur
                                dynamicMissionTimeLeft--
                            }
                            if (dynamicMissionTimeLeft <= 0) {
                                surviveMission.isCompleted = true // Görev tamamlandı
                                // Görev tamamlandığında oyun bitmesin, skorla devam etsin
                                // Multiplayer ise durumu güncelle
                                onPlayerStateChange?.invoke(
                                    (initialPlayerState ?: PlayerState(userId = "guest")).copy(
                                        score = score,
                                        isGameOver = false, // Görev tamamlandığında Game Over olmuyor
                                        currentBlockSequenceIndex = playerBlockSequenceIndex,
                                        // Eğer survive görevi tamamlandıysa, oyuncunun hala oyunda olduğunu varsayarız.
                                        // Ancak diğer oyuncu game over olduysa, oyun yine de bitebilir.
                                    )
                                )
                            }
                        }
                    }
                }
            }


            while (isActive && !isGameOver && !isGamePaused) { // isGameOver olduğunda döngü durur
                delay(gameLoopInterval)

                if (currentPiece == null) {
                    val newPiece = if (blockSequence != null) {
                        generateNextFallingPiece(blockSequence, playerBlockSequenceIndex)
                    } else {
                        generateNextFallingPiece()
                    }

                    // *** GELİŞTİRİLMİŞ GAME OVER KONTROLÜ (Başlangıç Hizasına Geldiğinde) ***
                    // Yeni blok oluşturulduğu anda mevcut bloklarla veya tahtanın üst sınırı ile çarpışıyor mu?
                    if (checkCollision(currentBoard, newPiece, 0, 0)) {
                        isGameOver = true
                        onGameEnd(GameResult.Failed(score, level.id))
                        break // Oyun bitti, döngüyü sonlandır
                    }

                    currentPiece = newPiece

                    // Yeni bir parça oluşturulduğunda indeksi artır (sadece multiplayer ise)
                    if (blockSequence != null) {
                        playerBlockSequenceIndex++
                    }
                } else {
                    val movedPiece = moveBlock(currentPiece!!, 0, 1)
                    // Normal düşme çarpışma kontrolü
                    if (checkCollision(currentBoard, movedPiece, 0, 0)) {
                        currentBoard = addToBoard(currentBoard, currentPiece!!)
                        val clearedLines = clearLines(currentBoard)
                        currentBoard = currentBoard.map { it.toMutableList() }.toMutableList() // Force recompose

                        if (clearedLines > 0) {
                            score += calculateScore(clearedLines)
                            totalClearedLines += clearedLines

                            updateMissionsOnLineClear(clearedLines, currentMissions, totalClearedLines)

                            currentLevelSpeed = (currentLevelSpeed * 0.95f).toLong().coerceAtLeast(100L)
                        }
                        currentPiece = null // Taşı sabitledikten sonra yeni bir taşı tetikler

                        // Taş yerleştiğinde, bir sonraki taşın oluşması için null'a ayarlandı.
                        // Yeni taşın oluşumu sırasında Game Over kontrolü yapılacak.
                    } else {
                        currentPiece = movedPiece
                    }
                }

                updateMissionsOnScore(score, currentMissions)

                // Oyuncu durumu Firebase'e gönder (eğer multiplayer ise)
                if (initialPlayerState != null) {
                    onPlayerStateChange?.invoke(
                        initialPlayerState.copy(
                            board = Board(convert2DTo1D(currentBoard)),
                            currentBlock = currentPiece?.let { BlockState(it.shapeId, it.color, it.position.y, it.position.x) }, // BlockState'e y ve x gönderiliyor
                            score = score,
                            isGameOver = isGameOver, // Eğer isGameOver true ise Firebase'e gönder
                            level = level.id,
                            currentBlockSequenceIndex = playerBlockSequenceIndex // En son bloğun indeksini kaydet
                        )
                    )
                }

                // Tek oyunculu modda görevler bitince oyun sonu ekranı görünsün (multiplayer hariç)
                if (level.id != GameLevels.MULTIPLAYER_LEVEL.id && currentMissions.all { it.isCompleted }) {
                    if (!isGameOver) { // Zaten Game Over değilse tetikle
                        isGameOver = true
                        onGameEnd(GameResult.Completed(score, level.id))
                    }
                    break // Görevler tamamlandı, döngüyü sonlandır
                }

                // Eğer multiplayer seviyesindeysek ve ilk aşama görevleri tamamlandıysa ikinci aşama görevini ekle
                if (level.id == GameLevels.MULTIPLAYER_LEVEL.id &&
                    currentMissions.any { it.id == "mm1_stage1_lines" && it.isCompleted } &&
                    currentMissions.any { it.id == "mm2_stage1_score" && it.isCompleted } &&
                    currentMissions.none { it.id == "mm3_stage2_time" } // Henüz eklenmediyse
                ) {
                    currentMissions.add(Mission(id = "mm3_stage2_time", type = MissionType.SURVIVE_TIME, targetValue = 40, description = "40 saniye hayatta kal", isCompleted = false))
                }
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(level.theme.backgroundColor))
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && !isGameOver && currentPiece != null) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            moveBlock(currentPiece!!, -1, 0).let { moved ->
                                if (!checkCollision(currentBoard, moved, 0, 0)) currentPiece = moved
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            moveBlock(currentPiece!!, 1, 0).let { moved ->
                                if (!checkCollision(currentBoard, moved, 0, 0)) currentPiece = moved
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            moveBlock(currentPiece!!, 0, 1).let { moved ->
                                if (!checkCollision(currentBoard, moved, 0, 0)) {
                                    currentPiece = moved
                                }
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            rotateBlock(currentPiece!!).let { rotated ->
                                if (!checkCollision(currentBoard, rotated, 0, 0)) currentPiece = rotated
                            }
                            true
                        }
                        Key.Spacebar -> {
                            var tempPiece = currentPiece!!
                            while (!checkCollision(currentBoard, moveBlock(tempPiece, 0, 1), 0, 0)) {
                                tempPiece = moveBlock(tempPiece, 0, 1)
                            }
                            currentPiece = tempPiece
                            currentBoard = addToBoard(currentBoard, currentPiece!!)
                            currentBoard = currentBoard.map { it.toMutableList() }.toMutableList()
                            val clearedLines = clearLines(currentBoard)
                            if (clearedLines > 0) {
                                score += calculateScore(clearedLines)
                                totalClearedLines += clearedLines
                                updateMissionsOnLineClear(clearedLines, currentMissions, totalClearedLines)
                            }
                            currentPiece = null // Taşı sabitledikten sonra yeni bir taşı tetikler
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!isGameOver && currentPiece != null) {
                            rotateBlock(currentPiece!!).let { rotated ->
                                if (!checkCollision(currentBoard, rotated, 0, 0)) currentPiece = rotated
                            }
                        }
                    },
                    onLongPress = { /* İsteğe bağlı */ }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (!isGameOver && currentPiece != null) {
                        val deltaX = if (dragAmount > 20) 1 else if (dragAmount < -20) -1 else 0
                        if (deltaX != 0) {
                            moveBlock(currentPiece!!, deltaX, 0).let { moved ->
                                if (!checkCollision(currentBoard, moved, 0, 0)) currentPiece = moved
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (!isGameOver && currentPiece != null) {
                        if (dragAmount > 20) {
                            moveBlock(currentPiece!!, 0, 1).let { moved ->
                                if (!checkCollision(currentBoard, moved, 0, 0)) {
                                    currentPiece = moved
                                }
                            }
                        } else if (dragAmount < -20) {
                            var tempPiece = currentPiece!!
                            while (!checkCollision(currentBoard, moveBlock(tempPiece, 0, 1), 0, 0)) {
                                tempPiece = moveBlock(tempPiece, 0, 1)
                            }
                            currentPiece = tempPiece
                            currentBoard = addToBoard(currentBoard, currentPiece!!)
                            currentBoard = currentBoard.map { it.toMutableList() }.toMutableList()
                            val clearedLines = clearLines(currentBoard)
                            if (clearedLines > 0) {
                                score += calculateScore(clearedLines)
                                totalClearedLines += clearedLines
                                updateMissionsOnLineClear(clearedLines, currentMissions, totalClearedLines)
                            }
                            currentPiece = null // Taşı sabitledikten sonra yeni bir taşı tetikler
                        }
                    }
                }
            }
            .focusable()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Skor: $score", fontSize = 20.sp, color = Color.White)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Görevler:", fontSize = 16.sp, color = Color.White)
                    currentMissions.forEach { mission ->
                        Text(
                            text = "${mission.description} ${if (mission.isCompleted) "(Tamamlandı!)" else ""}",
                            fontSize = 14.sp,
                            color = if (mission.isCompleted) Color.Green else Color.White
                        )
                    }
                    // Eğer zaman görevi aktifse sayacı göster
                    if (dynamicMissionTimeLeft > 0) {
                        Text(
                            text = "Kalan Süre: $dynamicMissionTimeLeft saniye",
                            fontSize = 14.sp,
                            color = if (dynamicMissionTimeLeft <= 10) Color.Red else Color.Yellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .size(BOARD_COLS * 30.dp, BOARD_ROWS * 30.dp)
            ) {
                val cellWidth = size.width / BOARD_COLS.toFloat()
                val cellHeight = size.height / BOARD_ROWS.toFloat()

                // Tahtayı çiz
                currentBoard.forEachIndexed { y, row ->
                    row.forEachIndexed { x, cellValue ->
                        val blockColor = if (cellValue != 0) Color(cellValue) else Color.Transparent
                        drawRect(
                            color = blockColor,
                            topLeft = Offset(x * cellWidth, y * cellHeight),
                            size = Size(cellWidth, cellHeight)
                        )
                        drawRect(
                            color = Color.Gray.copy(alpha = 0.2f),
                            topLeft = Offset(x * cellWidth, y * cellHeight),
                            size = Size(cellWidth, cellHeight),
                            style = Stroke(1f)
                        )
                    }
                }

                // Aktif taşı çiz
                currentPiece?.let { piece ->
                    val blockColor = Color(piece.color)
                    piece.shape.forEach { point ->
                        val px = piece.position.x + point.x
                        val py = piece.position.y + point.y
                        if (py in 0 until BOARD_ROWS && px in 0 until BOARD_COLS) {
                            drawRect(
                                color = blockColor,
                                topLeft = Offset(px * cellWidth, py * cellHeight),
                                size = Size(cellWidth, cellHeight)
                            )
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.2f),
                                topLeft = Offset(px * cellWidth, py * cellHeight),
                                size = Size(cellWidth, cellHeight),
                                style = Stroke(1f)
                            )
                        }
                    }
                }
            }

        }
// ... Canvas
// ... isGameOver kontrolü (tek oyunculu)

// Pause Butonu (sağ üst köşede)
        if (!isGameOver && !isGamePaused) { // Oyun bitmemiş ve duraklatılmamışsa butonu göster
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { isGamePaused = true },
                    modifier = Modifier
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                ) {
                    Text("Duraklat")
                }
            }
        }

// Pause Ekranı (oyun duraklatıldığında)
        if (isGamePaused) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .shadow(8.dp, shape = RoundedCornerShape(12.dp))
                    .background(Color.DarkGray, shape = RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = "OYUN DURAKLATILDI",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { isGamePaused = false }) {
                    Text("Devam Et")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    // Ana menüye dönmek için onGameEnd callback'ini kullanıyoruz
                    onGameEnd(GameResult.MainMenu)
                    isGamePaused = false // Ekran değiştikten sonra pause durumunu sıfırla
                }) {
                    Text("Ana Menüye Dön")
                }
            }
        }
        // *** TEK OYUNCULU GAME OVER EKRANI BURADA ***
        // Bu blok, initialPlayerState null (yani tek oyunculu modda) ve isGameOver true olduğunda çalışır.
        if (isGameOver && initialPlayerState == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .shadow(8.dp, shape = RoundedCornerShape(12.dp))
                    .background(Color.DarkGray, shape = RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Text(
                    text = if (currentMissions.all { it.isCompleted }) "GÖREV TAMAMLANDI!" else "OYUN BİTTİ!",
                    color = if (currentMissions.all { it.isCompleted }) Color.Green else Color.Red,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Skor: $score",
                    color = Color.Yellow,
                    fontSize = 24.sp
                )
                Text(
                    text = "Seviye: ${level.name}",
                    color = Color.Cyan,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(onClick = {
                    resetGame() // Oyunu sıfırla
                    onGameEnd(GameResult.PlayAgain) // Tekrar oyna çağrısı
                }) {
                    Text("Tekrar Oyna")
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (currentMissions.all { it.isCompleted }) {
                    val currentLevelIndex = GameLevels.ALL_LEVELS.indexOfFirst { it.id == level.id }
                    if (currentLevelIndex != -1 && currentLevelIndex + 1 < GameLevels.ALL_LEVELS.size) {
                        Button(onClick = {
                            onGameEnd(GameResult.NextLevel(level.id)) // Sonraki seviyeye geç çağrısı
                        }) {
                            Text("Bir Sonraki Seviyeye Geç")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            text = "Tebrikler, tüm seviyeleri tamamladınız!",
                            color = Color.Green,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Button(onClick = {
                    onGameEnd(GameResult.MainMenu) // Ana menüye dön çağrısı
                }) {
                    Text("Ana Menüye Dön")
                }
            }
        }
    }
}