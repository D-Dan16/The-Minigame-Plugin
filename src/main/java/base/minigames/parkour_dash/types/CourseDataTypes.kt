package base.minigames.parkour_dash.types

import org.bukkit.Location
import java.io.File

data class CoursePoolData(
    val version: Int,
    val schematicsBase: String,
    val courses: List<CourseGroup>
)

data class CourseGroup(
    val id: String,
    val name: String,
    val theme: String,
    val variants: List<CourseVariant>
)

data class CourseVariant(
    val path: String,
    val difficulty: Int
)

data class Course(
    val file: File,
    val difficulty: Int,
    val shouldBeMirrored: Boolean,
    val startPos: Location
)

/**
 * Tracks mutable generation state for a single parkour path
 */
data class PathGenerator(
    val courseDifficulties: List<Int>,
    var currentCourseLocation: Location,
    val checkpoints: MutableList<Location>
)