fun main() {
//    val mazeGenerator = MazeGenerator("config.csv")
//    val maze = mazeGenerator.createMaze()

    val spawner = MonsterSpawner("mazeSpawnTable(1.0.1)_20250318_1653.csv",
        "monsterTable(1.0.1)_20250318_1653.csv")
    val targetGrade = 4
    val spawns = spawner.getSpawnsForGrade(targetGrade)

    println("스폰된 몬스터 리스트")
    spawns.forEach { spawn ->
        println("몬스터 이름 : ${spawn.monsterName}, 위치 : ${spawn.position}")
    }
}