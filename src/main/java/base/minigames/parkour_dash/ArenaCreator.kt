package base.minigames.parkour_dash

import base.MinigamePlugin
import base.minigames.parkour_dash.PDConst.CourseBoundaries.CourseDirections.FORWARDS
import base.minigames.parkour_dash.PDConst.WORLD
import base.utils.extensions_for_classes.getBlockAt
import base.utils.extensions_for_classes.getMaterialAt
import base.utils.other.BuildLoader
import com.google.gson.Gson
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import org.bukkit.Location
import org.bukkit.Material
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.random.Random

private lateinit var coursesFile: File
private var activeCoursePool: MutableList<CourseVariantContainer> = mutableListOf()
private var locationToGenerateLeftCourse: Location = PDConst.Locations.START_LOCATION_OF_LEFT_PATH
private var locationToGenerateMiddleCourse: Location = PDConst.Locations.START_LOCATION_OF_MIDDLE_PATH
private var locationToGenerateRightCourse: Location = PDConst.Locations.START_LOCATION_OF_RIGHT_PATH
private val baseFolder = MinigamePlugin.plugin.getSchematicsBaseFolder(MinigamePlugin.Companion.MinigameType.PARKOUR_DASH)

internal val courseRegions: MutableList<CuboidRegion> = mutableListOf()

fun createCoursePaths(parkourDash: ParkourDash) {
    fetchCourses(parkourDash)
    prepareGeneratingCourses(parkourDash)
}

private fun fetchCourses(parkourDash: ParkourDash) {
    //<editor-fold desc="obtain courses file">
    if (!baseFolder.exists()) baseFolder.mkdirs()
    coursesFile = File(baseFolder, PDConst.FilePaths.COURSES_METADATA)

    if (!coursesFile.exists()) {
        parkourDash.plugin.getResource(PDConst.FilePaths.COURSES_METADATA)?.use { input ->
            coursesFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Parkour Dash Courses file resource not found")
    }
    //</editor-fold>

    //<editor-fold desc="obtain the course pool from the courses file">
    val reader = coursesFile.bufferedReader()
    val courseData = Gson().fromJson(reader, CoursePoolData::class.java)
    activeCoursePool = courseData.courses.toMutableList()
    reader.close()
    //</editor-fold>
}

private fun prepareGeneratingCourses(parkourDash: ParkourDash) {
    // 1. Get the path configurations (ranges) based on the selected game difficulty
    val configs: List<PDConst.CoursePathConfig> = when (parkourDash.difficulty) {
        ParkourDashCommands.Modes.NORMAL -> PDConst.NormalMode.pathsConfig
        ParkourDashCommands.Modes.HARD -> PDConst.HardMode.pathsConfig
        ParkourDashCommands.Modes.EXTREME -> PDConst.ExtremeMode.pathsConfig
    }

    // 2. Define the starting locations for the three paths
    val locations: List<Location> = listOf(locationToGenerateLeftCourse, locationToGenerateMiddleCourse, locationToGenerateRightCourse)

    // 3. For each path, determine the difficulty level of each course it will contain
    val difficultiesOfCourses: List<List<Int>> = configs.map {
        // Randomly pick the total difficulty budget for this specific path
        var pathDifficultyTokens = it.difficultyTokensRange.random()
        var courseDifficulties: MutableList<Int> = mutableListOf()

        // Distribute the total budget into individual courses until the budget is empty
        while (pathDifficultyTokens > 0) {
            // Pick a random difficulty for the next course
            val difficultyTokensOfCourse = it.individualCourseDifficultyRange.random()

            // If the difficulty of the next course exceeds the remaining budget, stop distributing
            if (difficultyTokensOfCourse > pathDifficultyTokens) break

            pathDifficultyTokens -= difficultyTokensOfCourse
            courseDifficulties += difficultyTokensOfCourse
        }

        return@map courseDifficulties
    }

    // 4. Pair each path's course difficulty list with its starting location
    val difficultiesAndLocations = difficultiesOfCourses.zip(locations)

    // 5. Generate the courses for each path, updating the location pointer after each generation
    difficultiesAndLocations.forEach { (listOfDifficulties, pathStartLocation) ->
        var currentCourseLocation = pathStartLocation
        for (difficulty in listOfDifficulties) {
            val chosenCourse = pickCourse(difficulty, currentCourseLocation)

            val courseBoundaries = generateCourse(chosenCourse)

            removeRedstoneCorners(courseBoundaries)
            currentCourseLocation = updateLocationPointer(courseBoundaries)
        }
    }
}

private fun generateCourse(chosenCourse: Course): CuboidRegion {
    val courseClipboard = BuildLoader.getClipboardHolderFromFile(
        chosenCourse.file,
        chosenCourse.startPos
    )
    if (chosenCourse.shouldBeMirrored) {
        BuildLoader.mirrorClipboardHolder(
            courseClipboard,
            PDConst.CourseBoundaries.CourseDirections.FORWARDS.toCardinalDirection()
        )
    }
    val courseBoundaries = BuildLoader.getRotatedRegion(courseClipboard)
    courseRegions += courseBoundaries

    BuildLoader.loadSchematic(courseClipboard)
    return courseBoundaries
}

private fun removeRedstoneCorners(courseBoundaries: CuboidRegion) {
    // Get the 8 corner blocks of the course and set them all to air (those blocks are redstone blocks)
    val min = courseBoundaries.minimumPoint
    val max = courseBoundaries.maximumPoint

    val corners = listOf(
        min,
        max,
        BlockVector3.at(min.x(), min.y(), max.z()),
        BlockVector3.at(min.x(), max.y(), min.z()),
        BlockVector3.at(max.x(), min.y(), min.z()),
        BlockVector3.at(min.x(), max.y(), max.z()),
        BlockVector3.at(max.x(), min.y(), max.z()),
        BlockVector3.at(max.x(), max.y(), min.z())
    )

    corners.forEach { corner ->
        WORLD.getBlockAt(corner).type = Material.AIR
    }
}


private fun pickCourse(
    difficultyTokensOfCourse: Int, locationToGenerateCourse: Location
) : Course {
    val allAvailableDifficulties =
        activeCoursePool.asSequence().flatMap { container -> container.variants.map { it.difficulty } }.distinct().toList()

    var courseContainer: CourseVariantContainer? = null
    var targetDifficulty: Int? = null

    // pick a course with the closest possible difficulty to the wanted difficulty
    if (allAvailableDifficulties.isNotEmpty()) {
        targetDifficulty = allAvailableDifficulties.minByOrNull { abs(it - difficultyTokensOfCourse) }!!
        courseContainer = activeCoursePool
            .filter { container -> container.variants.any { it.difficulty == targetDifficulty } }
            .randomOrNull()
    }

    if (courseContainer == null) {
        throw IllegalStateException("No courses available in the pool at all!")
    }

    // Pick a random variant that matches the (possibly updated) target difficulty
    val validVariants = courseContainer.variants.filter { it.difficulty == targetDifficulty }
    val selectedVariant = validVariants.random()

    // Remove the entire course from the pool so no other variants of it can be picked
    activeCoursePool.remove(courseContainer)

    val course = Course(
        File(
            File(baseFolder,PDConst.FilePaths.SCHEMATICS_FOLDER),
            selectedVariant.path
        ),
        selectedVariant.difficulty,
        shouldBeMirrored = Random.nextBoolean(),
        locationToGenerateCourse.apply { y++ }
    )

    return course
}

private fun updateLocationPointer(boundingBox: CuboidRegion): Location {
    // Locate the diamond block of the schematic - this is the indicator of the course's end.
    val diamondBlockLocation = boundingBox.firstOrNull { WORLD.getMaterialAt(it) == Material.DIAMOND_BLOCK }?.let {
        WORLD.getBlockAt(it).location
    } ?: throw IllegalStateException("No diamond block found in the course")
    return diamondBlockLocation
}