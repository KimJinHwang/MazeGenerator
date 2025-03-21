import kotlin.random.Random
import java.io.BufferedReader
import java.io.InputStreamReader

// Data classes
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
    val position: Triple<Float, Float, Float>?,
)

data class SpawnTrigger(
    val triggerType: String?,
    val position: Triple<Float, Float, Float>?,
    val distance: Int?,
    val monsterGroup: String,
    val weight: Int
)

class MonsterSpawner(monsterGroupFilePath: String, monsterFilePath: String) {
    private lateinit var monsterGroupList: List<MonsterGroup>
    private lateinit var monsterList: List<Monster>

    init {
        parseMonsterGroupCSV(monsterGroupFilePath)
        parseMonsterCSV(monsterFilePath)
    }

    private fun parseMonsterGroupCSV(filePath: String) {
        val inputStream = this::class.java.getResourceAsStream("/$filePath")
            ?: throw IllegalArgumentException("파일을 찾을 수 없습니다: $filePath")

        monsterGroupList = BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.lineSequence()
                .drop(1) // Remove header
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

    private fun parseMonsterCSV(filePath: String) {
        val inputStream = this::class.java.getResourceAsStream("/$filePath")
            ?: throw IllegalArgumentException("파일을 찾을 수 없습니다: $filePath")

        monsterList = BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.lineSequence()
                .drop(1) // Remove header
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

    fun getSpawnsForGrade(targetGrade: Int): List<Spawn> {
        val spawns = mutableListOf<Spawn>()
        val filteredList = monsterGroupList.filter { it.grade == targetGrade }

        if (filteredList.isEmpty()) {
            println("해당 grade 몬스터 그룹이 없습니다.")
            return emptyList()
        }

        val selectedGroup = filteredList.random()
        println("selectedGroup:${selectedGroup}")

        val triggers = parseTriggerData(selectedGroup.monsterGroups)
        val selectedSpawnTrigger = selectRandomSpawn(triggers)

        val selectedMonsterGroup = monsterList.find { it.groupName == selectedSpawnTrigger.monsterGroup }

        selectedMonsterGroup?.monsterOffsets?.split("/")?.forEach { offsetEntry ->
            val (monsterName, offsetStr) = offsetEntry.split("@")
            val offsetParts = offsetStr.split(".").map { it.toFloat() }
            val spawnXYZParts = selectedGroup.spawnXYZ.split(".").map { it.toFloat() }

            val x = spawnXYZParts[0] + offsetParts[0]
            val y = spawnXYZParts[1] + offsetParts[1]
            val z = spawnXYZParts[2] + offsetParts[2]

            spawns.add(Spawn(monsterName, Triple(x, y, z)))
        }

        return spawns
    }

    private fun parseTriggerData(data: String): List<SpawnTrigger> {
        return data.split("/").map { entry ->
            val (triggerInfo, weightStr) = entry.split("*")
            val weight = weightStr.toInt()

            if ("@" in triggerInfo) {
                val (triggerPart, monsterGroup) = triggerInfo.split("@")
                val triggerComponents = triggerPart.split(":")

                val triggerType = triggerComponents[0]
                val positionParts = triggerComponents[1].split(".").map { it.toFloat() }
                val position = Triple(positionParts[0], positionParts[1], positionParts[2])
                val distance = triggerComponents[2].removePrefix("D").toInt()

                SpawnTrigger(triggerType, position, distance, monsterGroup, weight)
            } else {
                SpawnTrigger(null, null, null, triggerInfo, weight)
            }
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
}