package base.minigames.parkour_dash

import org.bukkit.Location
import java.io.File

data class CoursePoolData(
    val version: Int,
    val schematicsBase: String,
    val courses: List<CourseVariantContainer>
)

data class CourseVariantContainer(
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
