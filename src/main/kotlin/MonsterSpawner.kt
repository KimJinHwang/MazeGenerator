//import java.io.*
//import kotlin.random.Random
//
//// Data classes
//data class MonsterGroup(
//    val index: Int,
//    val type: String,
//    val grade: Int,
//    val spawnXYZ: String,
//    val monsterGroups: String
//)
//
//data class Monster(
//    val index: Int,
//    val groupName: String,
//    val monsterOffsets: String
//)
//
//data class Spawn(
//    val monsterName: String,
//    val position: Triple<Float, Float, Float>?,
//)
//
//data class SpawnTrigger(
//    val triggerType: String?,
//    val position: Triple<Float, Float, Float>?,
//    val distance: Int?,
//    val monsterGroup: String,
//    val weight: Int
//)
//
//class MonsterSpawner(monsterGroupFile: File, monsterFile: File) {
//    private var monsterGroupList: List<MonsterGroup>
//    private var monsterList: List<Monster>
//
//    init {
//        monsterGroupList = parseMonsterGroupCSV(monsterGroupFile)
//        monsterList = parseMonsterCSV(monsterFile)
//    }
//
//    private fun parseMonsterGroupCSV(file: File): List<MonsterGroup> {
//        if (!file.exists()) {
//            throw FileNotFoundException("파일이 존재하지 않습니다: ${file.absolutePath}")
//        }
//        if (!file.canRead()) {
//            throw IOException("파일을 읽을 수 없습니다: ${file.absolutePath}")
//        }
//
//        return file.bufferedReader().useLines { lines ->
//            lines.drop(1) // 헤더 제거
//                .map { line ->
//                    val columns = line.split(",")
//                    MonsterGroup(
//                        index = columns[0].toInt(),
//                        type = columns[1],
//                        grade = columns[2].toInt(),
//                        spawnXYZ = columns[3],
//                        monsterGroups = columns[4]
//                    )
//                }.toList()
//        }
//    }
//
//    private fun parseMonsterCSV(file: File): List<Monster> {
//        // 파일 존재 및 읽기 가능 여부 확인
//        if (!file.exists()) {
//            throw FileNotFoundException("파일이 존재하지 않습니다: ${file.absolutePath}")
//        }
//        if (!file.canRead()) {
//            throw IOException("파일을 읽을 수 없습니다: ${file.absolutePath}")
//        }
//
//        return file.bufferedReader().useLines { lines ->
//            lines.drop(1) // 헤더 제거
//                .map { line ->
//                    val columns = line.split(",")
//                    Monster(
//                        index = columns[0].toInt(),
//                        groupName = columns[1],
//                        monsterOffsets = columns[2]
//                    )
//                }.toList()
//        }
//    }
//
//    fun getSpawnsForGrade(targetGrade: Int): List<Spawn> {
//        val spawns = mutableListOf<Spawn>()
//        val filteredList = monsterGroupList.filter { it.grade == targetGrade }
//
//        if (filteredList.isEmpty()) {
//            println("해당 grade 몬스터 그룹이 없습니다.")
//            return emptyList()
//        }
//
//        val selectedGroup = filteredList.random()
//        println("selectedGroup:${selectedGroup}")
//
//        val triggers = parseTriggerData(selectedGroup.monsterGroups)
//        val selectedSpawnTrigger = selectRandomSpawn(triggers)
//
//        val selectedMonsterGroup = monsterList.find { it.groupName == selectedSpawnTrigger.monsterGroup }
//
//        selectedMonsterGroup?.monsterOffsets?.split("/")?.forEach { offsetEntry ->
//            val (monsterName, offsetStr) = offsetEntry.split("@")
//            val offsetParts = offsetStr.split(".").map { it.toFloat() }
//            val spawnXYZParts = selectedGroup.spawnXYZ.split(".").map { it.toFloat() }
//
//            val x = spawnXYZParts[0] + offsetParts[0]
//            val y = spawnXYZParts[1] + offsetParts[1]
//            val z = spawnXYZParts[2] + offsetParts[2]
//
//            spawns.add(Spawn(monsterName, Triple(x, y, z)))
//        }
//
//        return spawns
//    }
//
//    private fun parseTriggerData(data: String): List<SpawnTrigger> {
//        return data.split("/").map { entry ->
//            val (triggerInfo, weightStr) = entry.split("*")
//            val weight = weightStr.toInt()
//
//            if ("@" in triggerInfo) {
//                val (triggerPart, monsterGroup) = triggerInfo.split("@")
//                val triggerComponents = triggerPart.split(":")
//
//                val triggerType = triggerComponents[0]
//                val positionParts = triggerComponents[1].split(".").map { it.toFloat() }
//                val position = Triple(positionParts[0], positionParts[1], positionParts[2])
//                val distance = triggerComponents[2].removePrefix("D").toInt()
//
//                SpawnTrigger(triggerType, position, distance, monsterGroup, weight)
//            } else {
//                SpawnTrigger(null, null, null, triggerInfo, weight)
//            }
//        }
//    }
//
//    private fun selectRandomSpawn(triggers: List<SpawnTrigger>): SpawnTrigger {
//        val totalWeight = triggers.sumOf { it.weight }
//        val randomValue = Random.nextInt(totalWeight)
//
//        var cumulativeWeight = 0
//        for (trigger in triggers) {
//            cumulativeWeight += trigger.weight
//            if (randomValue < cumulativeWeight) {
//                println("randvalue:${randomValue}, cumulativeWeight:${cumulativeWeight}")
//                println(trigger.monsterGroup)
//                return trigger
//            }
//        }
//        throw IllegalStateException("No spawn trigger selected, check weights.")
//    }
//}
