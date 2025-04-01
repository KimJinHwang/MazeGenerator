import java.io.*
import kotlin.random.Random

// LevelDesignData: CSV의 각 행은 번호, 층, 등급, 컨셉, monsterSpawnCoordinate, boxSpawnCoordinate 순서로 저장됨.
data class LevelDesignData(
    val number: Int,                      // 번호
    val floor: Int,                       // 층
    val grade: Int,                       // 등급
    val concept: String,                  // 컨셉
    val monsterSpawnCoordinate: String,   // 예: "0.0.0:FULLKEY"
    val boxSpawnCoordinate: String        // 기존 용도 (여기서는 사용하지 않음)
)

// SpawnGroup: CSV의 각 행은 [group1, group2, group3, group4, fullKey, dropOffset] 순서로 저장됨.
data class SpawnGroup(
    val group1: String,
    val group2: String,
    val group3: String,
    val group4: String,
    val fullKey: String,
    val dropOffset: String    // 예: "goblin@1.0.0/goblin@0.5.0" 등, 여러 항목은 슬래시('/')로 구분
)

data class Spawn(
    val monsterName: String,
    val position: Triple<Float, Float, Float>?
)

// 최종 반환 결과: 기본 좌표, fullKey, 그리고 계산된 Spawn 목록
data class SpawnResult(
    val baseCoordinate: Triple<Float, Float, Float>,
    val fullKey: String,
    val spawns: List<Spawn>
)

class MonsterSpawnerLatest(levelDesignFile: File, spawnGroupFile: File) {
    private var levelDesignDataList: List<LevelDesignData>
    private var spawnGroupList: List<SpawnGroup>

    init {
        levelDesignDataList = parseLevelDesignDataCSV(levelDesignFile)
        spawnGroupList = parseSpawnGroupCSV(spawnGroupFile)
    }

    // CSV 한 줄을 파싱하여, 큰따옴표로 묶인 필드는 하나의 값으로 처리하는 헬퍼 함수
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
                        i++ // 다음 따옴표 건너뛰기
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

    // LevelDesignData CSV 파싱: 번호, 층, 등급, 컨셉, monsterSpawnCoordinate, boxSpawnCoordinate
    private fun parseLevelDesignDataCSV(file: File): List<LevelDesignData> {
        if (!file.exists()) throw FileNotFoundException("파일이 존재하지 않습니다: ${file.absolutePath}")
        if (!file.canRead()) throw IOException("파일을 읽을 수 없습니다: ${file.absolutePath}")
        return file.bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val columns = parseCSVLine(line)
                if (columns.size < 6) {
                    println("잘못된 형식의 행: $line")
                    null
                } else {
                    try {
                        LevelDesignData(
                            number = columns[0].toInt(),
                            floor = columns[1].toInt(),
                            grade = columns[2].toInt(),
                            concept = columns[3],
                            monsterSpawnCoordinate = columns[4],
                            boxSpawnCoordinate = columns[5]
                        )
                    } catch (e: Exception) {
                        println("행 파싱 오류: $line, 오류: ${e.message}")
                        null
                    }
                }
            }.toList()
        }
    }

    // SpawnGroup CSV 파싱: group1, group2, group3, group4, fullKey, dropOffset
    private fun parseSpawnGroupCSV(file: File): List<SpawnGroup> {
        if (!file.exists()) throw FileNotFoundException("파일이 존재하지 않습니다: ${file.absolutePath}")
        if (!file.canRead()) throw IOException("파일을 읽을 수 없습니다: ${file.absolutePath}")
        return file.bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val columns = parseCSVLine(line)
                if (columns.size < 6) {
                    println("잘못된 형식의 행: $line")
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
                        println("SpawnGroup 행 파싱 오류: $line, 오류: ${e.message}")
                        null
                    }
                }
            }.toList()
        }
    }

    private fun selectRandomSpawn(triggers: List<SpawnTrigger>): SpawnTrigger {
        val totalWeight = triggers.sumOf { it.weight }
        val randomValue = Random.nextInt(totalWeight)

        var cumulativeWeight = 0
        for (trigger in triggers) {
            cumulativeWeight += trigger.weight
            if (randomValue < cumulativeWeight) {
                println("randvalue:${randomValue}, cumulativeWeight:${cumulativeWeight}")
                println(trigger.monsterGroup)
                return trigger
            }
        }
        throw IllegalStateException("No spawn trigger selected, check weights.")
    }

    // monsterSpawnCoordinate 파싱: "x.y.z:FULLKEY" 형식으로 가정
    private fun parseMonsterSpawnCoordinate(data: String): Pair<Triple<Float, Float, Float>, String>? {
        val parts = data.split(",").map { it.trim() }
        // parts의 수만큼 (스폰 지역수)
        for (part in parts) {
            try {
                val spawnPoints = part.split(":").map { it.trim() }
                if (spawnPoints.size < 3) {
                    println("잘못된 소환데이터 형식: $spawnPoints")
                    return null
                }
                return Pair(Triple(coordParts[0], coordParts[1], coordParts[2]), parts[1])
            } catch (e: Exception) {
                println("monsterSpawnCoordinate 파싱 오류: $data, 오류: ${e.message}")
                return null
            }
        }
    }

    // targetGrade와 targetFloor에 해당하는 LevelDesignData의 monsterSpawnCoordinate를 파싱하고,
    // fullKey에 맞는 SpawnGroup의 dropOffset을 사용하여 spawn 정보를 구성한다.
    fun getSpawnDetails(targetGrade: Int, targetFloor: Int): List<SpawnResult> {
        val results = mutableListOf<SpawnResult>()
        // 목표 등급과 층에 해당하는 LevelDesignData 필터링
        val filteredLevels = levelDesignDataList.filter { it.grade == targetGrade && it.floor == targetFloor }
        if (filteredLevels.isEmpty()) {
            println("해당 등급($targetGrade)과 층($targetFloor)의 레벨 디자인 데이터가 없습니다.")
            return emptyList()
        }
        for (level in filteredLevels) {
            // monsterSpawnCoordinate는 "x.y.z:FULLKEY" 형식임
            val spawnInfo = parseMonsterSpawnCoordinate(level.monsterSpawnCoordinate) ?: continue
            val (baseCoordinate, fullKey) = spawnInfo
            // fullKey에 해당하는 SpawnGroup 검색
            val spawnGroup = spawnGroupList.find { it.fullKey == fullKey }
            if (spawnGroup == null) {
                println("fullKey가 '$fullKey'인 SpawnGroup을 찾을 수 없습니다.")
                continue
            }
            // dropOffset을 파싱하여 Spawn 목록 생성
            // dropOffset 형식: "monsterName@dx.dy.dz/monsterName@dx.dy.dz/..."
            val spawns = mutableListOf<Spawn>()
            spawnGroup.dropOffset.split("/")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { offsetEntry ->
                    val parts = offsetEntry.split("@").map { it.trim() }
                    if (parts.size != 2) {
                        println("잘못된 dropOffset 형식: $offsetEntry")
                        return@forEach
                    }
                    val monsterName = parts[0]
                    try {
                        val offsetParts = parts[1].split(".").map { it.toFloat() }
                        if (offsetParts.size < 3) {
                            println("잘못된 오프셋 좌표 형식: $offsetEntry")
                            return@forEach
                        }
                        val x = baseCoordinate.first + offsetParts[0]
                        val y = baseCoordinate.second + offsetParts[1]
                        val z = baseCoordinate.third + offsetParts[2]
                        spawns.add(Spawn(monsterName, Triple(x, y, z)))
                    } catch (e: Exception) {
                        println("dropOffset 좌표 파싱 오류: $offsetEntry, 오류: ${e.message}")
                    }
                }
            results.add(
                SpawnResult(
                    baseCoordinate = baseCoordinate,
                    fullKey = fullKey,
                    spawns = spawns
                )
            )
        }
        return results
    }
}
