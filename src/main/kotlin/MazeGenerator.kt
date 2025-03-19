import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.random.Random

@Serializable
enum class RoomType {
    NR, // 일반 룸
    ER, // 입구 룸
    XR, // 출구 룸
    OR, // 장애물 룸
    MR, // 몬스터 룸
    FR  // 가짜 룸
}

@Serializable
data class Room(
    var x: Int = 0,
    var y: Int = 0,
    var type: RoomType = RoomType.NR,
    var passages: MutableMap<String, Boolean> = mutableMapOf(
        "UP" to false,
        "DOWN" to false,
        "LEFT" to false,
        "RIGHT" to false
    ),
    var grade: Int = 0
){
    fun copy(): Room {
        return Room(
            x = this.x,
            y = this.y,
            type = this.type,
            passages = this.passages.toMutableMap(),
            grade = this.grade
        )
    }
}

class MazeGenerator(private val filePath: String) {

    private val config: Map<String, String>
    init {
        config = readConfig(filePath)
    }

    val directions = listOf("UP", "DOWN", "LEFT", "RIGHT")
    val dx = listOf(-1, 1, 0, 0)
    val dy = listOf(0, 0, -1, 1)

    fun createMaze() : Array<Array<Room>> {
        var map: Array<Array<Room>>
        var entrance: Pair<Int, Int>
        var exit: Pair<Int, Int>
        var path: List<Pair<Int, Int>>?

        do {
            map = initializeMap()
            val (entrancePos, exitPos) = setEntranceAndExit(map)
            entrance = entrancePos
            exit = exitPos
            placeObstacles(map, entrance, exit)
            assignGrades(map, entrance, exit)
            path = findPathWithTargetGrade(map, entrance, exit, config["TARGET_GRADE"]?.toInt() ?: 20)
        } while (path == null)

        setMonsterRoomsAndActivatePassages(map, path)
        setFakeRooms(map, path)
        //printMap(map)
        //printRoomConnections(map)
        //println()
        updateMapWithSortedPath(map, path)
        printMap(map)

        //saveMapToFile(map, "map_data.json")

        return map
    }

    fun isValid(x: Int, y: Int): Boolean {
        val size = config["SIZE"]?.toInt() ?: 10
        return x in 0 until size && y in 0 until size
    }

    fun initializeMap(): Array<Array<Room>> {
        val size = config["SIZE"]?.toInt() ?: 10
        return Array(size) { i ->
            Array(size) { j ->
                Room(x = i, y = j)
            }
        }
    }

    fun setEntranceAndExit(map: Array<Array<Room>>): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val size = config["SIZE"]?.toInt() ?: 10
        val entrance = Pair(Random.nextInt(size), Random.nextInt(size))
        var exit: Pair<Int, Int>
        do {
            exit = Pair(Random.nextInt(size), Random.nextInt(size))
        } while (entrance == exit)
        map[entrance.first][entrance.second].type = RoomType.ER
        map[exit.first][exit.second].type = RoomType.XR
        return Pair(entrance, exit)
    }

    fun placeObstacles(map: Array<Array<Room>>, entrance: Pair<Int, Int>, exit: Pair<Int, Int>) {
        val size = config["SIZE"]?.toInt() ?: 10
        val obstacleProbability = (config["OBSTACLE_PROBABILITY"]?.toFloat()?.div(100.0f)) ?: 0.2f
        val maxObstacles = (size * size * obstacleProbability).toInt()
        val obstacleCount = Random.nextInt(size, maxObstacles) // 장애물 개수는 10~20개 사이로 설정
        var count = 0
        while (count < obstacleCount) {
            val x = Random.nextInt(size)
            val y = Random.nextInt(size)
            if (map[x][y].type == RoomType.NR && (x to y) != entrance && (x to y) != exit) {
                map[x][y].type = RoomType.OR
                count++
            }
        }
    }

    fun dfs(
        map: Array<Array<Room>>,
        current: Pair<Int, Int>,
        end: Pair<Int, Int>,
        visited: MutableSet<Pair<Int, Int>>,
        path: MutableList<Pair<Int, Int>>,
        currentGrade: Int,
        targetGrade: Int
    ): Boolean {
        val (x, y) = current
        visited.add(current)
        path.add(current)

        if (current == end) {
            return currentGrade == targetGrade
        }

        for (i in directions.indices) {
            val nx = x + dx[i]
            val ny = y + dy[i]
            val next = Pair(nx, ny)
            if (isValid(nx, ny) && !visited.contains(next) && map[nx][ny].type != RoomType.OR) {
                val nextGrade = currentGrade + map[nx][ny].grade
                if (nextGrade <= targetGrade) {
                    if (dfs(map, next, end, visited, path, nextGrade, targetGrade)) {
                        return true
                    }
                }
            }
        }

        visited.remove(current)
        path.removeAt(path.size - 1)
        return false
    }

    fun findPathWithTargetGrade(
        map: Array<Array<Room>>,
        start: Pair<Int, Int>,
        end: Pair<Int, Int>,
        targetGrade: Int
    ): List<Pair<Int, Int>>? {
        val path = mutableListOf<Pair<Int, Int>>()
        val visited = mutableSetOf<Pair<Int, Int>>()
        if (dfs(map, start, end, visited, path, 0, targetGrade)) {
            return path
        }
        return null
    }

    fun assignGrades(map: Array<Array<Room>>, entrance: Pair<Int, Int>, exit: Pair<Int, Int>) {
        val size = config["SIZE"]?.toInt() ?: 10
        val maxDistance = size * 2 - 2 // 대각선 거리의 최대값
        for (i in 0 until size) {
            for (j in 0 until size) {
                if (map[i][j].type == RoomType.NR) {
                    val distanceToEntrance = abs(i - entrance.first) + abs(j - entrance.second)
                    val distanceToExit = abs(i - exit.first) + abs(j - exit.second)
                    val normalizedDistance = (distanceToEntrance + distanceToExit).toFloat() / maxDistance
                    map[i][j].grade = (normalizedDistance * 5).toInt() // 1부터 5까지의 점수 부여
                }
            }
        }
    }

    fun activatePassage(room: Room, direction: String) {
        room.passages[direction] = true
    }

    fun setMonsterRoomsAndActivatePassages(map: Array<Array<Room>>, path: List<Pair<Int, Int>>) {
        for (i in path.indices) {
            val (x, y) = path[i]
            if (map[x][y].type == RoomType.ER) continue
            else if (map[x][y].type == RoomType.NR) {
                map[x][y].type = RoomType.MR
            }
            if (i > 0) {
                val (px, py) = path[i - 1]
                val direction = when {
                    px == x - 1 -> "UP"
                    px == x + 1 -> "DOWN"
                    py == y - 1 -> "LEFT"
                    else -> "RIGHT"
                }
                activatePassage(map[x][y], direction)
                activatePassage(
                    map[px][py], when (direction) {
                        "UP" -> "DOWN"
                        "DOWN" -> "UP"
                        "LEFT" -> "RIGHT"
                        else -> "LEFT"
                    }
                )
            }
        }
    }

    fun setFakeRooms(map: Array<Array<Room>>, path: List<Pair<Int, Int>>) {
        val visited = mutableSetOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        val fakeRoomProbability = config["FAKE_ROOM_PROBABILITY"]?.toInt() ?: 20

        // 몬스터 룸을 큐에 추가
        path.forEach { queue.add(it) }

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (visited.contains(Pair(x, y))) continue
            visited.add(Pair(x, y))

            // 현재 룸이 몬스터 룸이 아닌 경우 건너뜀
            if (map[x][y].type != RoomType.MR && map[x][y].type != RoomType.FR) continue

            directions.forEachIndexed { index, direction ->
                val nx = x + dx[index]
                val ny = y + dy[index]

                if (isValid(nx, ny) && map[nx][ny].type == RoomType.NR && !map[x][y].passages[direction]!!) {
                    if (Random.nextInt(100) < fakeRoomProbability) { // 20% 확률
                        activatePassage(map[x][y], direction)
                        val oppositeDirection = when (direction) {
                            "UP" -> "DOWN"
                            "DOWN" -> "UP"
                            "LEFT" -> "RIGHT"
                            else -> "LEFT"
                        }
                        activatePassage(map[nx][ny], oppositeDirection)
                        map[nx][ny].type = RoomType.FR
                        queue.add(Pair(nx, ny))
                    }
                }
            }
        }
    }

    fun printMap(map: Array<Array<Room>>) {
        val size = config["SIZE"]?.toInt() ?: 10
        val RESET = "\u001b[0m"
        val YELLOW = "\u001b[33m"
        val RED = "\u001b[31m"
        val PURPLE = "\u001b[35m"

        // Define the width for each room's display
        val roomWidth = 4

        // Function to format room display
        fun formatRoom(room: Room): String {
            val (roomChar, color) = when (room.type) {
                RoomType.ER -> 'E' to YELLOW
                RoomType.XR -> 'X' to YELLOW
                RoomType.OR -> 'O' to RESET
                RoomType.MR -> 'M' to RED
                RoomType.FR -> 'F' to PURPLE
                else -> ' ' to RESET
            }
            val content = "$roomChar${room.grade}"
            val paddingLength = roomWidth - content.length
            return "$color$content$RESET" + " ".repeat(paddingLength)
        }

        // Top border
        println(" " + "_".repeat(size * (roomWidth + 1)))

        for (i in 0 until size) {
            var roomLine = "|"
            var passageLine = "|"
            for (j in 0 until size) {
                val room = map[i][j]
                roomLine += formatRoom(room)
                roomLine += if (room.passages["RIGHT"] == true) " " else "|"
                passageLine += if (room.passages["DOWN"] == true) " ".repeat(roomWidth) else "_".repeat(roomWidth)
                passageLine += if (room.passages["DOWN"] == true && room.passages["RIGHT"] == true) " " else "|"
            }
            println(roomLine)
            println(passageLine)
        }
    }

    fun printRoomConnections(map: Array<Array<Room>>) {
        val size = map.size
        for (i in 0 until size) {
            for (j in 0 until size) {
                val room = map[i][j]
                val currentIndex = "($i, $j)"
                room.passages.forEach { (direction, isActive) ->
                    if (isActive) {
                        val (di, dj) = when (direction) {
                            "UP" -> -1 to 0
                            "DOWN" -> 1 to 0
                            "LEFT" -> 0 to -1
                            "RIGHT" -> 0 to 1
                            else -> 0 to 0
                        }
                        val ni = i + di
                        val nj = j + dj
                        if (ni in 0 until size && nj in 0 until size) {
                            val connectedIndex = "($ni, $nj)"
                            println("현재 룸 인덱스: $currentIndex, 활성화 된 통로: $direction, 연결된 룸 인덱스: $connectedIndex")
                        }
                    }
                }
            }
        }
    }

    fun saveMapToFile(map: Array<Array<Room>>, fileName: String) {
        val jsonString = Json.encodeToString(map)
        File(fileName).writeText(jsonString)
    }

    fun loadMapFromFile(fileName: String): Array<Array<Room>> {
        val jsonString = File(fileName).readText()
        return Json.decodeFromString(jsonString)
    }

    fun deepCopyMap(originalMap: Array<Array<Room>>): Array<Array<Room>> {
        return Array(originalMap.size) { i ->
            Array(originalMap[i].size) { j ->
                originalMap[i][j].copy()
            }
        }
    }

    fun updateMapWithSortedPath(map: Array<Array<Room>>, path: List<Pair<Int, Int>>) {
        // 맵의 깊은 복사본 생성
        val tempMap = deepCopyMap(map)

        val sortedPath = path.sortedBy { (x, y) -> tempMap[x][y].grade }
        val sortedPathWithoutFirst = sortedPath.drop(1)

        // 정렬된 경로를 따라 맵 데이터 수정
        for ((index, position) in sortedPathWithoutFirst.withIndex()) {
            val (sortedX, sortedY) = position
            val (originalX, originalY) = path[index]
            map[originalX][originalY].grade = tempMap[sortedX][sortedY].grade
        }
    }

    fun readConfig(fileName: String): Map<String, String> {
        val configMap = mutableMapOf<String, String>()
        val inputStream = object {}.javaClass.getResourceAsStream("/$fileName")
            ?: throw IllegalArgumentException("파일을 찾을 수 없습니다: $fileName")
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.forEachLine { line ->
                val (key, value) = line.split(",")
                configMap[key] = value
            }
        }
        return configMap
    }
}