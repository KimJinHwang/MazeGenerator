import java.io.File
import kotlin.random.Random

// CSV 파싱용 정규식 (raw string 사용)
private val CSV_SPLIT_REGEX =
    """,(?=(?:[^"]*"[^"]*")*[^"]*$)""".toRegex()

// — 데이터 모델 —
// 층별 레벨 디자인 정보
data class LevelDesign(
    val floor: Int,
    val grade: Int,
    val spawnCoordsRaw: String,
    val chestCoordsRaw: String
)

// 몬스터 스폰 정보
data class MonsterSpawn(
    val fullKey: String,
    val dropOffsetsRaw: String
)

// 개별 몬스터 상세 정보
data class MonsterDetail(
    val monsterType: String,
    val level: Int,
    val location: String
)

// 트리거 정보 구조
data class TriggerInfo(
    val repeatType: String,    // single / respawn
    val shape: String,         // box 등
    val size: Int,             // 모양 크기
    val condition: String      // enter / exit
)

// 최종 스폰 결과 구조
data class SpawnDetail(
    val triggerLocation: String,
    val triggerInfo: TriggerInfo,
    val monsterDetails: List<MonsterDetail>
)

data class ChestSpawn(
    val fullKey: String,
    val dropValue: Double,
    val insideItemsRaw: String
)

data class ChestSpawnDetail(
    val direction: String,        // 바라보는 방향 벡터
    val location: String,         // 등장 위치
    val appears: Boolean,         // 등장 여부
    val spawnedItemKey: String?   // 등장 아이템 fullKey (등장 시)
)

/**
 * MonsterSpawner 클래스
 * 생성자에 두 CSV 파일을 전달하면 자동으로 데이터를 파싱합니다.
 */
class MonsterItemSpawner(
    levelDesignFile: File,
    monsterSpawnFile: File,
    chestSpawnFile: File
) {
    private val levelDesignList = mutableListOf<LevelDesign>()
    private val monsterSpawnMap = mutableMapOf<String, MonsterSpawn>()
    private val chestSpawnMap = mutableMapOf<String, ChestSpawn>()

    init {
        loadLevelDesign(levelDesignFile)
        loadMonsterSpawns(monsterSpawnFile)
        loadChestSpawns(chestSpawnFile)
    }

    /**
     * 층별 레벨 디자인 CSV를 파싱하여 levelDesignList에 저장
     */
    private fun loadLevelDesign(file: File) {
        val lines = file.readLines()
        if (lines.size < 2) return
        val header = lines[0].split(CSV_SPLIT_REGEX)
        val floorIdx = header.indexOf("층")
        val gradeIdx = header.indexOf("등급")
        val spawnIdx = header.indexOf("몬스터 등장좌표")
        val chestIdx = header.indexOf("상자 등장좌표")

        lines.drop(1).forEach { line ->
            val tokens = line.split(CSV_SPLIT_REGEX)
            val floor = tokens.getOrNull(floorIdx)?.toIntOrNull() ?: return@forEach
            val grade = tokens.getOrNull(gradeIdx)?.toIntOrNull() ?: return@forEach
            val spawnRaw = tokens.getOrNull(spawnIdx) ?: ""
            val chestRaw = tokens.getOrNull(chestIdx) ?: ""
            levelDesignList.add(LevelDesign(floor, grade, spawnRaw, chestRaw))
        }
    }

    /**
     * 몬스터 스폰 CSV를 파싱하여 fullKey → MonsterSpawn 맵에 저장
     */
    private fun loadMonsterSpawns(file: File) {
        val lines = file.readLines()
        if (lines.size < 2) return
        val header = lines[0].split(CSV_SPLIT_REGEX)
        val keyIdx = header.indexOf("fullKey")
        val dropIdx = header.indexOf("dropOffsets")

        lines.drop(1).forEach { line ->
            val tokens = line.split(CSV_SPLIT_REGEX)
            val key = tokens.getOrNull(keyIdx) ?: return@forEach
            val dropRaw = tokens.getOrNull(dropIdx) ?: return@forEach
            monsterSpawnMap[key] = MonsterSpawn(key, dropRaw)
        }
    }

    private fun loadChestSpawns(file: File) {
        val lines = file.readLines()
        if (lines.size < 2) return
        val header = lines[0].split(CSV_SPLIT_REGEX)
        val keyIdx = header.indexOf("fullKey")
        val dropIdx = header.indexOf("dropValue")
        val itemsIdx = header.indexOf("insideItems")

        lines.drop(1).forEach { line ->
            val tokens = line.split(CSV_SPLIT_REGEX)
            val key = tokens.getOrNull(keyIdx) ?: return@forEach
            val dropStr = tokens.getOrNull(dropIdx)?.removeSuffix("%") ?: return@forEach
            val dropValue = dropStr.toDoubleOrNull() ?: return@forEach
            val itemsRaw = tokens.getOrNull(itemsIdx) ?: return@forEach
            chestSpawnMap[key] = ChestSpawn(key, dropValue, itemsRaw)
        }
    }

    /**
     * targetFloor, targetGrade에 맞는 SpawnDetail 리스트를 반환
     */
    fun getFinalSpawnResults(targetFloor: Int, targetGrade: Int): List<SpawnDetail> {
        val design = levelDesignList.find { it.floor == targetFloor && it.grade == targetGrade }
            ?: return emptyList()

        return design.spawnCoordsRaw.split(",")
            .mapNotNull { coordRaw ->
                val parts = coordRaw.split(":")
                if (parts.size != 3) return@mapNotNull null
                val (loc, infoRaw, key) = parts

                // 트리거 정보 파싱
                val infoParts = infoRaw.split('.')
                if (infoParts.size != 4) return@mapNotNull null
                val triggerInfo = try {
                    val repeatType = infoParts[0]
                    val shape = infoParts[1]
                    val size = infoParts[2].toIntOrNull() ?: return@mapNotNull null
                    val condition = infoParts[3]
                    TriggerInfo(repeatType, shape, size, condition)
                } catch (e: Exception) {
                    return@mapNotNull null
                }

                // 몬스터 상세 정보 파싱
                val spawn = monsterSpawnMap[key] ?: return@mapNotNull null
                val details = spawn.dropOffsetsRaw.split("/")
                    .mapNotNull { offset ->
                        val seg = offset.split("@")
                        if (seg.size != 3) return@mapNotNull null
                        val type = seg[0]
                        val lvl = seg[1].toIntOrNull() ?: return@mapNotNull null
                        val position = seg[2]
                        MonsterDetail(type, lvl, position)
                    }

                SpawnDetail(loc, triggerInfo, details)
            }
    }

    fun getChestSpawnResults(targetFloor: Int, targetGrade: Int): List<ChestSpawnDetail> {
        val design = levelDesignList.find { it.floor == targetFloor && it.grade == targetGrade }
            ?: return emptyList()

        return design.chestCoordsRaw.split(",").mapNotNull { chestRaw ->
            val parts = chestRaw.split(":")
            if (parts.size != 3) return@mapNotNull null
            val (direction, loc, key) = parts
            val chest = chestSpawnMap[key] ?: return@mapNotNull null

            // 드롭 확률 판단
            val randVal = Random.nextDouble(0.0, 100.0)
            if (randVal > chest.dropValue) {
                // 등장하지 않음
                return@mapNotNull ChestSpawnDetail(direction, loc, appears = false, spawnedItemKey = null)
            }

            // 등장 시, 아이템 가중치 계산 전 "" trimming
            val rawItems = chest.insideItemsRaw.trim().removeSurrounding("\"")
            var totalWeight = 0
            val weightedList = rawItems.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size != 2) return@mapNotNull null
                val itemKey = parts[0].trim().trim('"')
                val weight = parts[1].trim().trim('"').toIntOrNull() ?: return@mapNotNull null
                totalWeight += weight
                itemKey to weight
            }
            val pick = Random.nextInt(totalWeight)
            println("Chest Item Pick Index: $pick (0 until $totalWeight)")
            var cumulative = 0
            val selected = weightedList.firstOrNull { (_, weight) ->
                cumulative += weight
                pick < cumulative
            }?.first

            ChestSpawnDetail(direction, loc, appears = true, spawnedItemKey = selected)
        }
    }
}