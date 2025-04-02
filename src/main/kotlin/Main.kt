import java.io.File

fun main() {
//    MazeGenerator 사용예제
//    val configFile = File("config.csv")
//    val mazeGenerator = MazeGenerator(configFile)
//    val maze = mazeGenerator.createMaze()

//    Monsterspawner 사용예제
//    val mazeSpawnFile = File("mazeSpawnTable(1.0.1)_20250318_1653.csv")
//    val monsterFile = File("monsterTable(1.0.1)_20250318_1653.csv")
//    val spawner = MonsterSpawner(mazeSpawnFile, monsterFile)
//    val targetGrade = 4
//    val spawns = spawner.getSpawnsForGrade(targetGrade)
//
//    println("스폰된 몬스터 리스트")
//    spawns.forEach { spawn ->
//        println("몬스터 이름 : ${spawn.monsterName}, 위치 : ${spawn.position}")
//    }

//    MonsterSpawner_latest 사용예제
    val spawner = MonsterSpawner(
        File("프로젝트X_몬스터 그룹 테이블 - 층별 레벨 디자인.csv"),
        File("프로젝트X_몬스터 그룹 테이블 - 스폰 정보.csv")
    )

    val results = spawner.getFinalSpawnResults(targetFloor = 1, targetGrade = 1)

    results.forEach {
        println("위치: ${it.position}, 트리거: ${it.triggerInfo}, 그룹: ${it.selectedFullKey}")
        println("드랍 몬스터들:")
        it.dropOffsets.forEach { drop ->
            println("  - ${drop.monsterName} @ ${drop.offset}")
        }
    }
}