package base.minigames.hole_in_the_wall

import org.bukkit.Bukkit.getWorld
import org.bukkit.Location
import org.bukkit.World


object HITWConst {
    /** Whether the plugin is running in development mode. */
    const val IS_IN_DEVELOPMENT: Boolean = false
    const val PLATFORMS_FOLDER: String = "platforms"


    const val WALLPACK_FOLDER: String = "wallpack"
    const val MAP_FOLDER: String = "map"

    val availableMaps: List<String> = listOf("Map1", "Map2", "Map3")

    //region wall constants that aren't tied to a specific wall spawner mode

    /** Maximum number of walls that can exist at once. */
    const val HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS: Int = 6

    /** Default travel lifespan for a regular wall. */
    const val DEFAULT_WALL_TRAVEL_LIFESPAN: Int = 25
    /** Default travel lifespan for a psych wall. */
    const val DEFAULT_PSYCH_WALL_TRAVEL_LIFESPAN: Int = 6

    const val MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION = 6
    const val PSYCH_WALL_THAT_RETURNS_TO_MOVING_LIFESPAN: Int = DEFAULT_WALL_TRAVEL_LIFESPAN - DEFAULT_PSYCH_WALL_TRAVEL_LIFESPAN

    const val LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_AN_ADJACENT_DIRECTION: Int = DEFAULT_WALL_TRAVEL_LIFESPAN - 7
    const val LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_THE_DIRECTION_THIS_WALL_IS_FACING: Int = DEFAULT_WALL_TRAVEL_LIFESPAN - 4

    //endregion


    object Locations {
        val WORLD: World = getWorld("world") ?: throw IllegalStateException("World 'world' not found. Please ensure the world is loaded.")

        /** The pivot point all arena locations are centered around. */
        val PIVOT: Location = Location(WORLD,0.0, 130.0, 0.0)

        /** Offset used to center the arena relative to walls and floor. */
        val CENTER_OF_MAP: Location = PIVOT.clone().add(1.0, 0.0, -1.0)

        /** The player spawn point. */
        val SPAWN: Location = PIVOT.clone().add(0.0, 3.0, 0.0)

        val PLATFORM: Location = PIVOT.clone()

        /** Maximum wall distance before colliding with the letter signs. */
        const val DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM: Double = 16.0

        val SOUTH_WALL_SPAWN: Location = PIVOT.clone().add(1.0, 1.0, DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM)
        val NORTH_WALL_SPAWN: Location = PIVOT.clone().add(0.0, 1.0, -DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM - 1.0)
        val WEST_WALL_SPAWN: Location = PIVOT.clone().add(-DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM, 1.0, 0.0)
        val EAST_WALL_SPAWN: Location = PIVOT.clone().add(DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM + 1.0, 1.0, -1.0)
    }

        object WallDifficulty {
            const val EASY: Int = 0
            const val MEDIUM: Int = 1
            const val HARD: Int = 2
            const val VERY_HARD: Int = 3
        }

    enum class WallSpawnerState {
        DO_NO_ACTION,
        IDLE, // The spawner is not doing anything
        INTENDING_TO_CREATE_1_WALL,
        WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN, // The spawner is waiting for the next wall to spawn
        SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS,
        INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE,
        SPAWNING_1_WALL, // The spawner is currently spawning a wall
        SPAWNING_MULTIPLE_WALLS_AT_ONCE
    }

    enum class WallSpawnerMode {
        WALL_CHAINER,
        WALLS_FROM_ALL_DIRECTIONS,
        WALLS_FROM_2_OPPOSITE_DIRECTIONS;
       // WALLS_ARE_UNPREDICTABLE,
       // WALLS_REVERSE;

        companion object {
            fun getModesAsAStringList(): List<String> {
                val modeNames: MutableList<String> = entries.map { it.name }.toMutableList()
                modeNames.add("Alternating")
                return modeNames
            }
        }
    }

    object WallSpawnerModes {
        object WallChainer {
            /** How many walls must spawn before the direction may change. */
            const val MIN_AMOUNT_OF_SPAWNS_TILL_CHANGING_DIRECTIONS: Int = 5
        }
        object WallsFromAllDirections {
            const val CHANCE_THAT_PSYCH_WALL_WILL_GET_REMOVED: Int = (0.66 * 100).toInt()
        }
        object WallsFrom2OppositeDirections {
            /** Chance that the duo mode changes directions. */
            const val CHANCE_OF_CHANGING_DIRECTIONS: Int = (0.15 * 100).toInt()
            /** Chance of considering a real-wall direction swap. */
            const val CHANCE_OF_CONSIDERING_TO_SWAP_REAL_WALL_DIRECTION: Int = (0.4 * 100).toInt()
            const val MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION: Int = HITWConst.MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION

            const val MIN_AMOUNT_OF_SPAWNS_TILL_CHANGING_DIRECTIONS_FOR_DUO: Int = 7
            const val MIN_AMOUNT_OF_SPAWNS_TILL_THERE_CAN_BE_CONSIDERATION_TO_SWAP_REAL_WALL_DIRECTION: Int = 3

            const val MAX_AMOUNT_OF_SPAWNS_TILL_THERE_MUST_BE_CHANGE: Int = 10
        }
    }

    object Timers {
        /** Delay before the game starts, in ticks. */
        const val DELAY_BEFORE_STARTING_GAME: Long = 2*20
        /** Game duration in seconds. */
        const val GAME_DURATION: Int = 300

        /** Wall speed increase landmarks in seconds. */
        val WALL_SPEED_UP_LANDMARKS: IntArray = intArrayOf(30, 60, 90, 120, 155, 200)
        /** Wall difficulty increase landmarks in seconds. */
        val INCREASE_WALL_DIFFICULTY_LANDMARKS: IntArray = intArrayOf(45, 90, 155)
        val PLATFORM_SHRINKAGE_LANDMARKS: IntArray = intArrayOf(70, 155)

        /** Wall speeds in ticks. */
        val WALL_SPEED: IntArray = intArrayOf(15, 12, 10, 7, 6, 5, 4)

        // *after the game knows that the wall can safely spawn in that direction, we'll make it wait extra for randomness
        /** Random delay range before spawning from the same direction. */
        val DELAY_BEFORE_SPAWNING_A_WALL_FROM_THE_SAME_DIRECTION: LongRange = 0L..12L
        /** Random delay range before spawning from a different direction. */
        val DELAY_BEFORE_SPAWNING_A_WALL_FROM_A_DIFFERENT_DIRECTION: LongRange = 0L..5L


        /** Delay before taking action on a stopped wall that has not entered center. */
        val STOPPED_WALL_DELAY_BEFORE_ACTION_DEALT: LongRange = 1L*20..2L*20


        const val ALTERNATING_WALL_SPAWNER_MODES_DELAY: Long = 20*20
    }
}
