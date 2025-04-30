/*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.random.Random

// CSV 매핑용 데이터 클래스

data class LevelDesignData(
    val floor: Int,
    val grade: Int,
    val concept: String,
    val name: String,
    val monsterSpawnCoordinate: List<MonsterSpawnData>,
    val boxSpawnCoordinate: String
)

data class MonsterSpawnData(
    val position: Triple<Float, Float, Float>,
    val triggerInfo: TriggerInfo,
    val monsterGroups: List<MonsterGroupSpawn>
)

data class TriggerInfo(
    val spawnType: String,
    val triggerShape: String,
    val triggerRange: String,
    val triggerAction: String
)

data class MonsterGroupSpawn(
    val fullKey: String
)

data class SpawnGroup(
    val group1: String,
    val group2: String,
    val group3: String,
    val group4: String,
    val fullKey: String,
    val dropOffset: String
)

data class DropOffsetEntry(
    val monsterName: String,
    val level: Int,
    val offset: Triple<Float, Float, Float>
)

data class FinalSpawnResult(
    val triggerPosition: Triple<Float, Float, Float>,
    val triggerInfo: TriggerInfo,
    val selectedFullKey: String,
    val dropOffsets: List<DropOffsetEntry>
)

class MonsterSpawner(
    levelDesignFile: File,
    spawnInfoFile: File
) {
    private val levelData: List<LevelDesignData>
    private val spawnGroups: List<SpawnGroup>

    init {
        levelData = parseLevelDesignFile(levelDesignFile)
        spawnGroups = parseSpawnGroupFile(spawnInfoFile)
    }

    fun getFinalSpawnResults(targetFloor: Int, targetGrade: Int): List<FinalSpawnResult> {
        val filtered = levelData.filter { it.floor == targetFloor && it.grade == targetGrade }
        if (filtered.isEmpty()) return emptyList()

        val results = mutableListOf<FinalSpawnResult>()

        for (level in filtered) {
            for (spawn in level.monsterSpawnCoordinate) {
                val totalWeight = spawn.monsterGroups.sumOf { it.probability.toDouble() }
                if (totalWeight <= 0.0) continue

                val rand = Random.nextDouble(totalWeight)
                var cumulative = 0.0
                var selectedKey: String? = null

                for (group in spawn.monsterGroups) {
                    cumulative += group.probability
                    if (rand <= cumulative) {
                        selectedKey = group.fullKey
                        break
                    }
                }

                selectedKey?.let { key ->
                    val matchedGroup = spawnGroups.find { it.fullKey == key }
                    val dropOffsets = matchedGroup?.let { parseDropOffset(it.dropOffset) } ?: emptyList()

                    results.add(
                        FinalSpawnResult(
                            position = spawn.position,
                            triggerInfo = spawn.triggerInfo,
                            selectedFullKey = key,
                            dropOffsets = dropOffsets
                        )
                    )
                }
            }
        }

        return results
    }

    fun parseDropOffset(dropOffset: String): List<DropOffsetEntry> {
        return dropOffset.split("/").mapNotNull { entry ->
            val parts = entry.split("@").map { it.trim() }
            if (parts.size != 2) return@mapNotNull null
            try {
                val coords = parts[1].split(".").map { it.toFloat() }
                if (coords.size != 3) return@mapNotNull null
                DropOffsetEntry(
                    monsterName = parts[0],
                    offset = Triple(coords[0], coords[1], coords[2])
                )
            } catch (e: Exception) {
                println("DropOffset 파싱 오류: $entry, 오류: ${e.message}")
                null
            }
        }
    }

    private fun parseLevelDesignFile(file: File): List<LevelDesignData> {
        return file.bufferedReader().useLines { lines ->
            lines.drop(1)
                .mapNotNull { line -> parseLevelLine(line) }
                .toList()
        }
    }

    private fun parseSpawnGroupFile(spawnFile: File): List<SpawnGroup> {
        return spawnFile.bufferedReader().useLines { lines ->
            lines.drop(1)
                .mapNotNull { line ->
                    val columns = parseCSVLine(line)
                    if (columns.size < 6) {
                        println("잘못된 SpawnGroup 행: $line")
                        null
                    } else {
                        try {
                            SpawnGroup(
                                group1 = columns[0],
                                group2 = columns[1],
                                group3 = columns[2],
                                group4 = columns[3],
                                fullKey = columns[4],
                                dropOffset = columns[5]
                            )
                        } catch (e: Exception) {
                            println("SpawnGroup 파싱 오류: $line, 오류: ${e.message}")
                            null
                        }
                    }
                }.toList()
        }
    }

    private fun parseLevelLine(line: String): LevelDesignData? {
        val columns = parseCSVLine(line)
        if (columns.size < 7) {
            println("잘못된 행: $line")
            return null
        }

        return try {
            val monsterDataList = parseMonsterSpawnCoordinate(columns[4])
            LevelDesignData(
                number = columns[0].toInt(),
                floor = columns[1].toInt(),
                grade = columns[2].toInt(),
                concept = columns[3],
                monsterSpawnCoordinate = monsterDataList,
                boxSpawnCoordinate = columns[5]
            )
        } catch (e: Exception) {
            println("행 파싱 실패: $line, 오류: ${e.message}")
            null
        }
    }

    private fun parseMonsterSpawnCoordinate(data: String): List<MonsterSpawnData> {
        return data.split(",").mapNotNull { part ->
            val pieces = part.split(":").map { it.trim() }
            if (pieces.size < 3) {
                println("monsterSpawnCoordinate 파싱 오류: $part")
                return@mapNotNull null
            }

            try {
                val positionParts = pieces[0].split(".").map { it.toFloat() }
                val position = Triple(positionParts[0], positionParts[1], positionParts[2])

                val triggerParts = pieces[1].split(".")
                val trigger = TriggerInfo(
                    spawnType = triggerParts.getOrNull(0) ?: "",
                    triggerShape = triggerParts.getOrNull(1) ?: "",
                    triggerRange = triggerParts.getOrNull(2) ?: "",
                    triggerAction = triggerParts.getOrNull(3) ?: ""
                )

                val monsterGroups = pieces[2].split("/").mapNotNull { entry ->
                    val subparts = entry.split("*").map { it.trim() }
                    if (subparts.size != 2) return@mapNotNull null
                    try {
                        MonsterGroupSpawn(
                            fullKey = subparts[0],
                            probability = subparts[1].toFloat()
                        )
                    } catch (e: Exception) {
                        println("몬스터 그룹 파싱 오류: $entry")
                        null
                    }
                }

                MonsterSpawnData(
                    position = position,
                    triggerInfo = trigger,
                    monsterGroups = monsterGroups
                )
            } catch (e: Exception) {
                println("좌표 파싱 오류: $part, 오류: ${e.message}")
                null
            }
        }
    }

    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result.map { it.trim() }
    }
}
*/
