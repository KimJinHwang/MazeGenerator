import java.io.File
import kotlin.random.Random

private val CSV_SPLIT_REGEX = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*\$)".toRegex()

// — 데이터 모델 —
data class LevelDesign(val floor: Int, val grade: Int, val fileName: String, val spawnCoordsRaw: String, val chestCoordsRaw: String)
data class MonsterSpawn(val fullKey: String, val dropOffsetsRaw: String)
data class MonsterDetail(val monsterType: String, val level: Int, val location: String)
data class TriggerInfo(val repeatType: String, val shape: String, val size: Int, val condition: String)
data class SpawnDetail(val triggerLocation: String, val triggerInfo: TriggerInfo, val monsterDetails: List<MonsterDetail>)
data class ChestSpawn(val fullKey: String, val dropValue: Double, val spawnObjectName: String, val insideItemsRaw: String)
data class ChestSpawnDetail(val direction: String, val location: String, val appears: Boolean, val spawnObjectName: String, val spawnedItemKey: String?)
data class FinalResult(val fileName: String, val spawnDetails: List<SpawnDetail>, val chestSpawnDetails: List<ChestSpawnDetail>)

class MonsterItemSpawner(levelDesignFile: File, monsterSpawnFile: File, chestSpawnFile: File) {
    private val levelDesignList = mutableListOf<LevelDesign>()
    private val monsterSpawnMap = mutableMapOf<String, MonsterSpawn>()
    private val chestSpawnMap = mutableMapOf<String, ChestSpawn>()

    init {
        loadLevelDesign(levelDesignFile)
        loadMonsterSpawns(monsterSpawnFile)
        loadChestSpawns(chestSpawnFile)
    }

    private fun readCsvWithHeader(file: File): Pair<List<String>, List<List<String>>> {
        val lines = file.readLines()
        if (lines.size < 2) return emptyList<String>() to emptyList()
        val header = lines[0].split(CSV_SPLIT_REGEX)
        val rows = lines.drop(1).map { it.split(CSV_SPLIT_REGEX) }
        return header to rows
    }

    private fun loadLevelDesign(file: File) {
        val (header, rows) = readCsvWithHeader(file)
        val floorIdx = header.indexOf("층")
        val gradeIdx = header.indexOf("등급")
        val fileIdx = header.indexOf("파일명")
        val spawnIdx = header.indexOf("몬스터 등장좌표")
        val chestIdx = header.indexOf("상자 등장좌표")

        rows.forEach { tokens ->
            val floor = tokens.getOrNull(floorIdx)?.toIntOrNull() ?: return@forEach
            val grade = tokens.getOrNull(gradeIdx)?.toIntOrNull() ?: return@forEach
            val fileName = tokens.getOrNull(fileIdx) ?: ""
            val spawnRaw = tokens.getOrNull(spawnIdx) ?: ""
            val chestRaw = tokens.getOrNull(chestIdx) ?: ""
            levelDesignList.add(LevelDesign(floor, grade, fileName, spawnRaw, chestRaw))
        }
    }

    private fun loadMonsterSpawns(file: File) {
        val (header, rows) = readCsvWithHeader(file)
        val keyIdx = header.indexOf("fullKey")
        val dropIdx = header.indexOf("dropOffsets")

        rows.forEach { tokens ->
            val key = tokens.getOrNull(keyIdx) ?: return@forEach
            val dropRaw = tokens.getOrNull(dropIdx) ?: return@forEach
            monsterSpawnMap[key] = MonsterSpawn(key, dropRaw)
        }
    }

    private fun loadChestSpawns(file: File) {
        val (header, rows) = readCsvWithHeader(file)
        val keyIdx = header.indexOf("fullKey")
        val dropIdx = header.indexOf("dropValue")
        val spawnObjectIdx = header.indexOf("spawnObjectName")
        val itemsIdx = header.indexOf("insideItems")

        rows.forEach { tokens ->
            val key = tokens.getOrNull(keyIdx) ?: return@forEach
            val dropStr = tokens.getOrNull(dropIdx)?.removeSuffix("%") ?: return@forEach
            val dropValue = dropStr.toDoubleOrNull() ?: return@forEach
            val spawnObjectName = tokens.getOrNull(spawnObjectIdx) ?: return@forEach
            val itemsRaw = tokens.getOrNull(itemsIdx) ?: return@forEach
            chestSpawnMap[key] = ChestSpawn(key, dropValue, spawnObjectName, itemsRaw)
        }
    }

    fun getCombinedResult(targetFloor: Int, targetGrade: Int): FinalResult? {
        val design = levelDesignList.find { it.floor == targetFloor && it.grade == targetGrade } ?: return null

        val spawnDetails = getFinalSpawnResults(targetFloor, targetGrade)
        val chestSpawnDetails = getChestSpawnResults(targetFloor, targetGrade)

        return FinalResult(
            fileName = design.fileName,
            spawnDetails = spawnDetails,
            chestSpawnDetails = chestSpawnDetails
        )
    }

    fun getFinalSpawnResults(targetFloor: Int, targetGrade: Int): List<SpawnDetail> {
        val design = levelDesignList.find { it.floor == targetFloor && it.grade == targetGrade } ?: return emptyList()

        return design.spawnCoordsRaw.split(",").mapNotNull { raw ->
            // 양끝 쌍따옴표 제거
            val coordRaw = raw.trim().trim('"')

            val parts = coordRaw.split(":")
            if (parts.size != 3) return@mapNotNull null
            val (loc, infoRaw, key) = parts
            val infoParts = infoRaw.split('.')
            if (infoParts.size != 4) return@mapNotNull null

            val triggerInfo = try {
                val (repeatType, shape, sizeStr, condition) = infoParts
                TriggerInfo(repeatType, shape, sizeStr.toInt(), condition)
            } catch (e: Exception) {
                return@mapNotNull null
            }

            val spawn = monsterSpawnMap[key] ?: return@mapNotNull null
            val dropClean = spawn.dropOffsetsRaw.trim().trim('"')
            val details = dropClean.split("/").mapNotNull { offset ->
                val seg = offset.split("@")
                if (seg.size != 3) return@mapNotNull null
                val (type, lvlStr, position) = seg
                val lvl = lvlStr.toIntOrNull() ?: return@mapNotNull null
                MonsterDetail(type, lvl, position)
            }

            SpawnDetail(loc, triggerInfo, details)
        }
    }

    fun getChestSpawnResults(targetFloor: Int, targetGrade: Int): List<ChestSpawnDetail> {
        val design = levelDesignList.find { it.floor == targetFloor && it.grade == targetGrade } ?: return emptyList()

        return design.chestCoordsRaw.split(",").mapNotNull { raw ->
            // 양끝 쌍따옴표 제거
            val chestRaw = raw.trim().trim('"')
            val (direction, loc, key) = chestRaw.split(":").takeIf { it.size == 3 } ?: return@mapNotNull null
            val chest = chestSpawnMap[key] ?: return@mapNotNull null
            val appears = Random.nextDouble(0.0, 100.0) <= chest.dropValue
            val spawnObjectName = chest.spawnObjectName

            if (!appears) return@mapNotNull ChestSpawnDetail(
                direction = direction,
                location = loc,
                appears = false,
                spawnObjectName = spawnObjectName,
                spawnedItemKey = null
            )

            val weightedList = parseWeightedItems(chest.insideItemsRaw)
            val selected = pickWeightedItem(weightedList)
            ChestSpawnDetail(
                direction = direction,
                location = loc,
                appears = true,
                spawnObjectName = spawnObjectName,
                spawnedItemKey = selected
            )
        }
    }

    private fun parseWeightedItems(raw: String): List<Pair<String, Int>> {
        return raw.trim().removeSurrounding("\"").split(",").mapNotNull { entry ->
            val (itemKey, weightStr) = entry.split(":").map { it.trim('"', ' ') }
            val weight = weightStr.toIntOrNull() ?: return@mapNotNull null
            itemKey to weight
        }
    }

    private fun pickWeightedItem(weightedList: List<Pair<String, Int>>): String? {
        val totalWeight = weightedList.sumOf { it.second }
        val pick = Random.nextInt(totalWeight)
        var cumulative = 0
        return weightedList.firstOrNull { (_, weight) ->
            cumulative += weight
            pick < cumulative
        }?.first
    }
}
