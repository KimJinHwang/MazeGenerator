import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
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

class MazeGenerator(private val file: File) {

    private val config: Map<String, String>
    init {
        config = readConfig(file)
    }

    private val directions = listOf("UP", "DOWN", "LEFT", "RIGHT")
    private val dx = listOf(-1, 1, 0, 0)
    private val dy = listOf(0, 0, -1, 1)

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

        // 실제 경로에 대해 정확한 grade 재분배
        assignPathGrades(
            map,
            path
        )

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

    private fun isValid(x: Int, y: Int): Boolean {
        val size = config["SIZE"]?.toInt() ?: 10
        return x in 0 until size && y in 0 until size
    }

    private fun initializeMap(): Array<Array<Room>> {
        val size = config["SIZE"]?.toInt() ?: 10
        return Array(size) { i ->
            Array(size) { j ->
                Room(x = i, y = j)
            }
        }
    }

    private fun setEntranceAndExit(map: Array<Array<Room>>): Pair<Pair<Int, Int>, Pair<Int, Int>> {
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

    private fun placeObstacles(map: Array<Array<Room>>, entrance: Pair<Int, Int>, exit: Pair<Int, Int>) {
        val size = config["SIZE"]?.toInt() ?: 10
        val obstacleProbability = (config["OBSTACLE_PROBABILITY"]?.toFloat()?.div(100.0f)) ?: 0.2f
        var maxObstacles = (size * size * obstacleProbability).toInt()
        if (maxObstacles <= size) {
            maxObstacles = size + 1
        }
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

    private fun dfs(
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

    private fun findPathWithTargetGrade(
        map: Array<Array<Room>>,
        start: Pair<Int, Int>,
        end: Pair<Int, Int>,
        targetGrade: Int
    ): List<Pair<Int, Int>>? {
        val path = mutableListOf<Pair<Int, Int>>()
        val visited = mutableSetOf<Pair<Int, Int>>()
        val initialGrade = map[start.first][start.second].grade
        if (dfs(map, start, end, visited, path, initialGrade, targetGrade)) {
            return path
        }
        return null
    }

    private fun assignGrades(map: Array<Array<Room>>, entrance: Pair<Int, Int>, exit: Pair<Int, Int>) {
        val size = config["SIZE"]?.toInt() ?: 10
        val maxRoomGrade = config["MAX_GRADE"]?.toInt() ?: 5
        val maxDistance = size * 2 - 2 // 대각선 거리의 최대값
        for (i in 0 until size) {
            for (j in 0 until size) {
                if (map[i][j].type != RoomType.OR) {
                    val distanceToEntrance = abs(i - entrance.first) + abs(j - entrance.second)
                    val distanceToExit = abs(i - exit.first) + abs(j - exit.second)
                    val normalizedDistance = (distanceToEntrance + distanceToExit).toFloat() / maxDistance
                    map[i][j].grade = (normalizedDistance * maxRoomGrade).toInt().coerceAtLeast(1)
                }
            }
        }
        // 입구는 항상 grade = 1
        map[entrance.first][entrance.second].grade = 1

        // 출구에는 지금까지 계산된 등급 중 최대값을 할당
        val maxGrade = map
            .flatten()
            .filter { it.type != RoomType.OR }
            .maxOf { it.grade }
        map[exit.first][exit.second].grade = maxGrade
    }

    private fun assignPathGrades(map: Array<Array<Room>>, path: List<Pair<Int, Int>>) {
        val maxGrade = config["MAX_GRADE"]!!.toInt()
        val targetGrade = config["TARGET_GRADE"]!!.toInt()
        val n = path.size
        if (n < 2) return

        // 1) 선형 분포로 등급 계산
        val grades = IntArray(n) { idx ->
            1 + (idx * (maxGrade - 1)) / (n - 1)
        }

        // 2) 합이 targetGrade를 넘으면 최소 strictly increasing 으로 대체
        if (grades.sum() > targetGrade) {
            for (i in 0 until n - 1) {
                grades[i] = i + 1
            }
            grades[n - 1] = maxGrade
        }

        // 3) 맵에 적용
        path.forEachIndexed { idx, (x, y) ->
            map[x][y].grade = grades[idx]
        }
    }

    private fun activatePassage(room: Room, direction: String) {
        room.passages[direction] = true
    }

    private fun setMonsterRoomsAndActivatePassages(map: Array<Array<Room>>, path: List<Pair<Int, Int>>) {
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

    private fun setFakeRooms(map: Array<Array<Room>>, path: List<Pair<Int, Int>>) {
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

    private fun printMap(map: Array<Array<Room>>) {
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

    private fun updateMapWithSortedPath(map: Array<Array<Room>>, path: List<Pair<Int, Int>>) {
        val target = config["TARGET_GRADE"]?.toInt() ?: return
        val n = path.size
        if (n == 0) return

        // 1. 최소 등급 1씩을 모든 방에 할당했을 때의 합 = n
        //    남은 예산(budget)은 target - n
        var budget = target - n
        if (budget < 0) budget = 0

        // 2. 인접 차이를 최대 2까지 허용하기 위해 d[i] ∈ [0..2]
        val maxDelta = 2
        val d = IntArray(n) { 0 }

        // 3. (n-i) 가중치를 가진 “코인”을 maxDelta 개수만큼 사용하여
        //    예산을 소진하는 그리디 알고리즘
        for (i in 1 until n) {
            val weight = n - i
            // 해당 위치에 사용할 수 있는 최대 개수
            val use = minOf(maxDelta, budget / weight)
            d[i] = use
            budget -= use * weight
        }

        // 4. 만약 아직 예산이 남아 있다면,
        //    뒤에서부터 (weight 작은 방향) 다시 한 번 소진 시도
        if (budget > 0) {
            for (i in n - 1 downTo 1) {
                val weight = n - i
                while (budget >= weight && d[i] < maxDelta) {
                    d[i]++
                    budget -= weight
                }
                if (budget == 0) break
            }
        }

        // 5. map에 실제 grade 반영
        //    - 첫 방(입구)은 기존 grade 유지 (보통 1)
        //    - 이후 currentGrade += d[i] 로만 변경 → 감소 절대 없음
        val (sx, sy) = path[0]
        var currentGrade = map[sx][sy].grade

        for (i in 1 until n) {
            currentGrade += d[i]
            val (x, y) = path[i]
            map[x][y].grade = currentGrade
        }
    }

    private fun readConfig(file: File): Map<String, String> {
        val configMap = mutableMapOf<String, String>()
        file.forEachLine { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") && !trimmedLine.startsWith(";")) {
                val parts = trimmedLine.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    configMap[key] = value
                }
            }
        }
        return configMap
    }
}