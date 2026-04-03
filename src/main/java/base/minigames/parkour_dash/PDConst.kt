@file:Suppress("DEPRECATION")

package base.minigames.parkour_dash

import base.annotations.OptionalFeature
import base.utils.additions.Direction
import com.sk89q.worldedit.math.BlockVector3
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World

object PDConst {
    const val TIME_OF_DAY = 1000L
    val WORLD: World = Bukkit.getWorld("world") ?: throw IllegalStateException("World 'world' not found")


    object FilePaths {
        const val COURSES_METADATA = "parkourdash-courses.json"
        const val SCHEMATICS_FOLDER = "courses-schematics"
    }

    object Locations {
        val PIVOT = Location(WORLD, 0.0, 250.0, 0.0)
        /** Starting location for players */
        val START_LOCATION = PIVOT.clone().add(0.0, 30.0, 0.0)

        val START_LOCATION_OF_MIDDLE_PATH: Location = PIVOT.clone()
        val START_LOCATION_OF_LEFT_PATH: Location = START_LOCATION_OF_MIDDLE_PATH.clone().add(0.0, 0.0, -40.0)
        val START_LOCATION_OF_RIGHT_PATH: Location = START_LOCATION_OF_MIDDLE_PATH.clone().add(0.0, 0.0, 40.0)
    }

    object CourseBoundaries {
        enum class CourseDirections {
            LEFT,
            RIGHT,
            BACKWARDS,
            FORWARDS,
            DOWN,
            UP;

            fun toCardinalDirection(): Direction {
                return when (this) {
                    LEFT -> Direction.NORTH
                    RIGHT -> Direction.SOUTH
                    BACKWARDS -> Direction.WEST
                    FORWARDS -> Direction.EAST
                    else -> {throw IllegalStateException("Invalid direction")}
                }
            }
        }

        /**
         * These are offsets from the pivot point of a parkour course [the gold block].
         * These offsets help us to know and assess where courses can generate so we won't overlap with those already generated
         */
        val COURSE_BOUNDARIES: Map<CourseDirections, BlockVector3> = mapOf(
            CourseDirections.LEFT to BlockVector3.at(0, 0, -10),
            CourseDirections.RIGHT to BlockVector3.at(0, 0, 10),
            CourseDirections.BACKWARDS to BlockVector3.at(0, 0, 0), //This overlaps with the pivot point
            CourseDirections.FORWARDS to BlockVector3.at(45, 0, 0),
            CourseDirections.DOWN to BlockVector3.at(0, -12, 0),
            CourseDirections.UP to BlockVector3.at(0, 16, 0)
        )
    }

    object Colors {
        const val LEVEL_COMPLETE_COLOR = "#00FF00" // Green
        const val FAIL_COLOR = "#FF0000" // Red
        const val TITLE_COLOR = "#00BFFF" // Aqua
    }

    object CourseMaterials {
        /** Materials used for parkour platforms which are for intermissions between stages, with their weights */
        val INTERMISSION_PLATFORM_MATERIALS = listOf(
            Material.STONE to 30,
            Material.COBBLESTONE to 25,
            Material.SMOOTH_STONE to 20,
            Material.MOSSY_COBBLESTONE to 15,
            Material.ANDESITE to 10
        )
    }

    object Times {
        const val GAME_DURATION = 300L
    }

    object GameSettings {
        /** Time limit per level in seconds */
        const val TIME_LIMIT_PER_LEVEL = 120L

        /** Number of lives per player */
        @OptionalFeature
        const val MAX_LIVES = 10

        /** Whether players can use items */
        @OptionalFeature
        const val ITEMS_ENABLED = false
    }

    object Points {
        /** Points awarded for completing a level. looks at the difficulty rating of the level */
        val POINTS_FOR_LEVEL_COMPLETION = listOf(
            (1..2) to 10,
            (3..4) to 20,
            (5..6) to 30,
            (7..9) to 40,
            (10..13) to 70,
            (14..17) to 100,
            (18..24) to 150,
            (25..30) to 200,
        )

        /** Bonus points for completing without falling */
        const val POINTS_MULTIPLIER_FOR_FLAWLESS_COMPLETION = 1.5
    }

    /** Configuration for a specific path (Left, Middle, Right) in the parkour course */
    object NormalMode {
        /** The configurations for each path in Normal mode */
        val pathsConfig = listOf(
            CoursePathConfig(25..45, 1..6),
            CoursePathConfig(66..86, 7..12),
            CoursePathConfig(99..119, 13..18)
        )
    }

    /** Configuration for a specific path (Left, Middle, Right) in the parkour course */
    object HardMode {
        /** The configurations for each path in Hard mode */
        val pathsConfig = listOf(
            CoursePathConfig(65..85, 5..10),
            CoursePathConfig(98..118, 11..16),
            CoursePathConfig(137..157, 17..25)
        )
    }

    /** Configuration for a specific path (Left, Middle, Right) in the parkour course */
    object ExtremeMode {
        /** The configurations for each path in Extreme mode */
        val pathsConfig = listOf(
            CoursePathConfig(100..120, 8..14),
            CoursePathConfig(138..158, 15..22),
            CoursePathConfig(176..196, 23..30)
        )
    }

    data class CoursePathConfig(
        /**
         * The sum of Difficulty Tokens the path has.
         * Let's say the path has 3 parkour courses
         * A -> 5, B -> 10, C -> 15
         * The sum of these 3 courses is 25 Difficulty Tokens => this value.
         */
        val difficultyTokensRange: IntRange,
        /**
         * The difficulty each course can have in this path is inside this range.
         * If this range is (1..3), then the individual course difficulty can be 1, 2, or 3 Difficulty Tokens.
         */
        val individualCourseDifficultyRange: IntRange
    )
}
