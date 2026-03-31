@file:Suppress("DEPRECATION")

package base.minigames.parkour_dash

import base.MinigamePlugin.Companion.world
import base.annotations.OptionalFeature
import com.sk89q.worldedit.math.BlockVector3
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World

object PDConst {
    const val TIME_OF_DAY = 1000L
    val WORLD: World = Bukkit.getWorld("world") ?: throw IllegalStateException("World 'world' not found")


    object FilePaths {
        const val COURSES_FILE_PATH = "parkourdash-courses.json"
    }

    object Locations {
        val PIVOT = Location(WORLD, 0.0, 100.0, 0.0)

        /** Starting location for players */
        val START_LOCATION = Location(
            WORLD,
            PIVOT.x + 20,
            PIVOT.y,
            PIVOT.z
        )
    }

    object CourseBoundaries {
        enum class Directions {
            /** Left */ NEG_Z,
            /** Right */ POS_Z,
            /** Backwards */ NEG_X,
            /** Forwards */ POS_X,
            /** Down */ NEG_Y,
            /** Up  */ POS_Y
        }

        /**
         * These are offsets from the pivot point of a parkour course [the gold block].
         * These offsets help us to know and assess where courses can generate so we won't overlap with those already generated
         */
        val COURSE_BOUNDARIES: Map<Directions, BlockVector3> = mapOf(
            Directions.NEG_Z to BlockVector3.at(0, 0, -10),
            Directions.POS_Z to BlockVector3.at(0, 0, 10),
            Directions.NEG_X to BlockVector3.at(0, 0, 0), //This overlaps with the pivot point
            Directions.POS_X to BlockVector3.at(45, 0, 0),
            Directions.NEG_Y to BlockVector3.at(0, -12, 0),
            Directions.POS_Y to BlockVector3.at(0, 16, 0)
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
}
