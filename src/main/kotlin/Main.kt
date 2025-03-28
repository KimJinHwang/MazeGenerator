import java.io.File

fun main() {
//    val configFile = File("config.csv")
//    val mazeGenerator = MazeGenerator(configFile)
//    val maze = mazeGenerator.createMaze()

    val mazeSpawnFile = File("mazeSpawnTable(1.0.1)_20250318_1653.csv")
    val monsterFile = File("monsterTable(1.0.1)_20250318_1653.csv")
    val spawner = MonsterSpawner(mazeSpawnFile, monsterFile)
    val targetGrade = 4
    val spawns = spawner.getSpawnsForGrade(targetGrade)

    println("스폰된 몬스터 리스트")
    spawns.forEach { spawn ->
        println("몬스터 이름 : ${spawn.monsterName}, 위치 : ${spawn.position}")
    }
}