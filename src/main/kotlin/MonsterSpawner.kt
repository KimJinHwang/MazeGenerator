import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

data class MonsterGroup(
    val index: Int,
    val type: String,
    val grade: Int,
    val spawnXYZ: String,
    val monsterGroups: String
)

data class Monster(
    val index: Int,
    val groupName: String,
    val monsterOffsets: String
)

data class Spawn(
    val monsterName: String,
    val x: Int,
    val y: Int,
    val z: Int
)

class MonsterSpawner(private val monsterGroupFilePath: String, private val monsterFilePath: String, private val targetGrade: Int) {
    private lateinit var monsterGroupList: List<MonsterGroup>
    private lateinit var monsterList: List<Monster>

    init{
        parsingMonsterGroupCSV(monsterGroupFilePath)
        parsingMonsterCSV(monsterFilePath)
    }

    private fun parsingMonsterGroupCSV(filePath: String) {
        val inputStream = this::class.java.getResourceAsStream("/$filePath")
            ?: throw IllegalArgumentException("파일을 찾을 수 없습니다: $filePath")

        monsterGroupList = BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.lineSequence()
                .drop(1) // 헤더를 제거
                .map { line ->
                    val columns = line.split(",")
                    MonsterGroup(
                        index = columns[0].toInt(),
                        type = columns[1],
                        grade = columns[2].toInt(),
                        spawnXYZ = columns[3],
                        monsterGroups = columns[4]
                    )
                }.toList()
        }
    }

    private fun parsingMonsterCSV(filePath: String) {
        val inputStream = this::class.java.getResourceAsStream("/$filePath")
            ?: throw IllegalArgumentException("파일을 찾을 수 없습니다: $filePath")

        monsterList = BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.lineSequence()
                .drop(1) // 헤더를 제거
                .map { line ->
                    val columns = line.split(",")
                    Monster(
                        index = columns[0].toInt(),
                        groupName = columns[1],
                        monsterOffsets = columns[2]
                    )
                }.toList()
        }
    }

    fun selectMonsterGroupIndex(targetGrade: Int) : Int {

        // 해당 grade의 MonsterGroup 리스트를 필터링합니다.
        val filteredList = monsterGroupList.filter { it.grade == targetGrade }

        // 필터링된 리스트에서 랜덤하게 하나의 index를 선택합니다.
        if (filteredList.isNotEmpty()) {
            val randomIndex = Random.nextInt(filteredList.size)
            val selectedIndex = filteredList[randomIndex].index
            println("선택된 index: $selectedIndex")
            return selectedIndex
        } else {
            println("grade $targetGrade 에 해당하는 데이터가 없습니다.")
        }

        return -1
    }

    fun getSpawnsForGrade(): List<Spawn> {
        val spawns = mutableListOf<Spawn>()

        // 1. targetGrade에 해당하는 몬스터 그룹 필터링
        val filteredGroups = monsterGroupList.filter { it.grade == targetGrade }

        for (group in filteredGroups) {
            val spawnXYZ = group.spawnXYZ
            val monsterGroupEntries = group.monsterGroups.split("/")

            for (entry in monsterGroupEntries) {
                val groupName = entry.split("*")[0] // 예: "goblins_lv0*50" -> "goblins_lv0"
                val matchingMonsters = monsterList.filter { it.groupName == groupName }

                for (monster in matchingMonsters) {
                    val offsets = monster.monsterOffsets.split("/")
                    for (offset in offsets) {
                        val parts = offset.split("@")
                        val monsterName = parts[0]
                        val coords = parts[1].split(".")
                        if (coords.size == 3) {
                            spawns.add(
                                Spawn(
                                    monsterName = monsterName,
                                    x = coords[0].toInt(),
                                    y = coords[1].toInt(),
                                    z = coords[2].toInt()
                                )
                            )
                        }
                    }
                }
            }
        }

        printSpawnsForGrade(spawns)

        return spawns
    }

    fun printSpawnsForGrade(spawns: List<Spawn>) {
        if (spawns.isEmpty()) {
            println("해당 등급의 몬스터가 없습니다.")
        } else {
            println("스폰된 몬스터 리스트:")
            spawns.forEach { spawn ->
                println("몬스터 이름: ${spawn.monsterName}, 위치: (${spawn.x}, ${spawn.y}, ${spawn.z})")
            }
        }
    }
}