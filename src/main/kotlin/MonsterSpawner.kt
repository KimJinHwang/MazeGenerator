import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

data class MonsterData(
    val index: Int,
    val type: String,
    val grade: Int,
    val spawnXYZ: String,
    val monsterGroups: String
)

class MonsterSpawner {
    fun selectMonsterGroupIndex(targetGrade: Int) : Int {
        // CSV 파일 경로를 지정
        val filePath = "mazeSpawnTable(1.0.1)_20250318_1653.csv"

        val inputStream = this::class.java.getResourceAsStream("/$filePath")
            ?: throw IllegalArgumentException("파일을 찾을 수 없습니다: $filePath")

        val monsterList = BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.lineSequence()
                .drop(1) // 헤더를 제거
                .map { line ->
                    val columns = line.split(",")
                    MonsterData(
                        index = columns[0].toInt(),
                        type = columns[1],
                        grade = columns[2].toInt(),
                        spawnXYZ = columns[3],
                        monsterGroups = columns[4]
                    )
                }.toList()
        }

        // 해당 grade의 MonsterData 리스트를 필터링합니다.
        val filteredList = monsterList.filter { it.grade == targetGrade }

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
}