import java.io.*
import kotlin.random.Random

// LevelDesignData: CSV의 각 행은 번호, 층, 등급, 컨셉, 몬스터 등장좌표, 상자 등장좌표 순서로 저장됨.
data class LevelDesignData(
    val number: Int,                      // 번호
    val floor: Int,                       // 층
    val grade: Int,                       // 등급
    val concept: String,                  // 컨셉
    val monsterSpawnCoordinate: String,   // 몬스터 등장좌표 (기본 좌표)
    val boxSpawnCoordinate: String        // 상자 등장좌표 (트리거 데이터 문자열)
)

data class Monster(
    val index: Int,
    val groupName: String,
    val monsterOffsets: String
)

data class Spawn(
    val monsterName: String,
    val position: Triple<Float, Float, Float>?
)

// TriggerEntry는 상자 등장좌표 문자열을 파싱한 결과로, 트리거 좌표, spawnType, triggerType, distance, 이벤트, 그리고
// "몬스터그룹*등장확률" 조건 리스트를 포함한다.
data class TriggerEntry(
    val triggerCoordinate: Triple<Float, Float, Float>,
    val spawnType: String,
    val triggerType: String,
    val distance: Int,
    val event: String,
    val triggerConditions: List<Pair<String, Int>> // (몬스터 그룹, 등장 확률)
)

// 최종적으로 반환될 결과는 트리거 정보와 선택된 triggerCondition 및 소환할 몬스터의 정보(상대 좌표 목록)를 포함한다.
data class SpawnResult(
    val triggerCoordinate: Triple<Float, Float, Float>,
    val spawnType: String,
    val triggerType: String,
    val distance: Int,
    val event: String,
    val triggerCondition: Pair<String, Int>,
    val spawns: List<Spawn>
)

class MonsterSpawner_latest(levelDesignFile: File, monsterFile: File) {
    private var levelDesignDataList: List<LevelDesignData>
    private var monsterList: List<Monster>

    init {
        levelDesignDataList = parseLevelDesignDataCSV(levelDesignFile)
        monsterList = parseMonsterCSV(monsterFile)
    }

    // CSV 한 줄을 파싱하여, 큰따옴표로 묶인 필드를 하나의 값으로 처리하는 함수
    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    // 연속된 큰따옴표(")는 이스케이프 처리
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++  // 다음 따옴표는 건너뛰기
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    // CSV 파일을 읽어, 각 행을 번호, 층, 등급, 컨셉, 몬스터 등장좌표, 상자 등장좌표 순서로 파싱한다.
    private fun parseLevelDesignDataCSV(file: File): List<LevelDesignData> {
        if (!file.exists()) {
            throw FileNotFoundException("파일이 존재하지 않습니다: ${file.absolutePath}")
        }
        if (!file.canRead()) {
            throw IOException("파일을 읽을 수 없습니다: ${file.absolutePath}")
        }
        return file.bufferedReader().useLines { lines ->
            lines.drop(1)
                .mapNotNull { line ->
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

    private fun parseMonsterCSV(file: File): List<Monster> {
        if (!file.exists()) {
            throw FileNotFoundException("파일이 존재하지 않습니다: ${file.absolutePath}")
        }
        if (!file.canRead()) {
            throw IOException("파일을 읽을 수 없습니다: ${file.absolutePath}")
        }
        return file.bufferedReader().useLines { lines ->
            lines.drop(1)
                .mapNotNull { line ->
                    val columns = line.split(",").map { it.trim() }
                    if (columns.size < 3) {
                        println("잘못된 형식의 행: $line")
                        null
                    } else {
                        try {
                            Monster(
                                index = columns[0].toInt(),
                                groupName = columns[1],
                                monsterOffsets = columns[2]
                            )
                        } catch (e: Exception) {
                            println("행 파싱 오류: $line, 오류: ${e.message}")
                            null
                        }
                    }
                }.toList()
        }
    }

    // boxSpawnCoordinate 문자열을 콤마(,) 단위로 분리한 후, 각 항목을 파싱하여 TriggerEntry 객체 리스트로 변환
    private fun parseTriggerEntries(data: String): List<TriggerEntry> {
        return data.split(",").mapNotNull { entry ->
            val trimmedEntry = entry.trim()
            if (trimmedEntry.isEmpty()) return@mapNotNull null
            val parts = trimmedEntry.split(":")
            if (parts.size != 3) {
                println("잘못된 트리거 형식: $entry")
                return@mapNotNull null
            }
            try {
                val coordParts = parts[0].split(".").map { it.trim().toFloat() }
                if (coordParts.size < 3) {
                    println("잘못된 좌표 형식: ${parts[0]}")
                    return@mapNotNull null
                }
                val triggerCoordinate = Triple(coordParts[0], coordParts[1], coordParts[2])

                val detailParts = parts[1].split(".").map { it.trim() }
                if (detailParts.size < 4) {
                    println("잘못된 세부 형식: ${parts[1]}")
                    return@mapNotNull null
                }
                val spawnType = detailParts[0]
                val triggerType = detailParts[1]
                val distance = detailParts[2].toInt()
                val event = detailParts[3]

                val conditionEntries = parts[2].split("/").mapNotNull { cond ->
                    val condParts = cond.split("*").map { it.trim() }
                    if (condParts.size != 2) {
                        println("잘못된 조건 형식: $cond")
                        null
                    } else {
                        val monsterGroup = condParts[0]
                        val probability = condParts[1].toInt()
                        Pair(monsterGroup, probability)
                    }
                }
                TriggerEntry(triggerCoordinate, spawnType, triggerType, distance, event, conditionEntries)
            } catch(e: Exception) {
                println("트리거 파싱 오류: $entry, 오류: ${e.message}")
                null
            }
        }
    }

    fun getSpawnDetails(targetGrade: Int, targetFloor: Int): List<SpawnResult> {
        val results = mutableListOf<SpawnResult>()
        // 목표 등급과 층에 해당하는 LevelDesignData 필터링
        val filteredGroups = levelDesignDataList.filter { it.grade == targetGrade && it.floor == targetFloor }
        if (filteredGroups.isEmpty()) {
            println("해당 등급($targetGrade)과 층($targetFloor)의 레벨 디자인 데이터가 없습니다.")
            return emptyList()
        }
        for (group in filteredGroups) {
            // boxSpawnCoordinate를 파싱하여 트리거 항목들로 변환
            val triggerEntries = parseTriggerEntries(group.boxSpawnCoordinate)
            for (triggerEntry in triggerEntries) {
                // triggerConditions의 가중치를 바탕으로 랜덤하게 조건 선택
                val totalWeight = triggerEntry.triggerConditions.sumOf { it.second }
                val randVal = Random.nextInt(totalWeight)
                var cumulative = 0
                var selectedCondition: Pair<String, Int>? = null
                for (cond in triggerEntry.triggerConditions) {
                    cumulative += cond.second
                    if (randVal < cumulative) {
                        selectedCondition = cond
                        break
                    }
                }
                if (selectedCondition == null) {
                    println("조건 선택 실패 (가중치 문제)")
                    continue
                }
                // 선택된 조건의 몬스터 그룹 이름과 일치하는 Monster 검색
                val monster = monsterList.find { it.groupName == selectedCondition.first }
                if (monster == null) {
                    println("해당 monster group을 찾을 수 없습니다: ${selectedCondition.first}")
                    continue
                }
                // monsterSpawnCoordinate(기본 좌표)와 Monster의 monsterOffsets를 결합해 소환 좌표 계산
                val spawns = mutableListOf<Spawn>()
                val baseCoordinateParts = group.monsterSpawnCoordinate.split(".").map { it.trim().toFloat() }
                if (baseCoordinateParts.size < 3) {
                    println("잘못된 monsterSpawnCoordinate 형식: ${group.monsterSpawnCoordinate}")
                    continue
                }
                monster.monsterOffsets.split("/").forEach { offsetEntry ->
                    val parts = offsetEntry.split("@").map { it.trim() }
                    if (parts.size != 2) {
                        println("잘못된 offset 형식: $offsetEntry")
                        return@forEach
                    }
                    val monsterName = parts[0]
                    try {
                        val offsetParts = parts[1].split(".").map { it.trim().toFloat() }
                        if (offsetParts.size < 3) {
                            println("잘못된 좌표 형식: $offsetEntry")
                            return@forEach
                        }
                        val x = baseCoordinateParts[0] + offsetParts[0]
                        val y = baseCoordinateParts[1] + offsetParts[1]
                        val z = baseCoordinateParts[2] + offsetParts[2]
                        spawns.add(Spawn(monsterName, Triple(x, y, z)))
                    } catch(e: Exception) {
                        println("좌표 파싱 오류: $offsetEntry, 오류: ${e.message}")
                    }
                }
                results.add(
                    SpawnResult(
                        triggerCoordinate = triggerEntry.triggerCoordinate,
                        spawnType = triggerEntry.spawnType,
                        triggerType = triggerEntry.triggerType,
                        distance = triggerEntry.distance,
                        event = triggerEntry.event,
                        triggerCondition = selectedCondition,
                        spawns = spawns
                    )
                )
            }
        }
        return results
    }
}
