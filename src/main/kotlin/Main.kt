fun main() {
//    val mazeGenerator = MazeGenerator("config.csv")
//    val maze = mazeGenerator.createMaze()

    val test = MonsterSpawner("mazeSpawnTable(1.0.1)_20250318_1653.csv",
        "monsterTable(1.0.1)_20250318_1653.csv", 0)
    test.getSpawnsForGrade()
}