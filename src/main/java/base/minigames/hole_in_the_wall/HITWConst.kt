package base.minigames.hole_in_the_wall

import org.bukkit.Bukkit.getWorld
import org.bukkit.Location
import org.bukkit.World


object HITWConst {
    const val IS_IN_DEVELOPMENT: Boolean = false // If the plugin is in development mode, some features may be disabled or behave differently. this is so that the plugin can be tested easily without constantly tempering with the code.
    const val PLATFORMS_FOLDER: String = "platforms"


    const val WALLPACK_FOLDER: String = "wallpack"
    const val MAP_FOLDER: String = "map"

    val availableMaps: List<String> = listOf("Map1", "Map2", "Map3")

    //region wall constants that aren't tied to a specific wall spawner mode

    // regular value for this is 6.
    const val HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS: Int = 6

    const val DEFAULT_WALL_TRAVEL_LIFESPAN: Int = 25 // How many blocks the wall travels before it disappears. This is the default value, but can be overridden by the wall file itself.
    const val DEFAULT_PSYCH_WALL_TRAVEL_LIFESPAN: Int = 6 // How many blocks the psych wall travels before it stops moving, then it'll be decided if it gets deleted or not and continues to move later on. This is the default value, but can be overridden by the wall file itself.

    const val MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION = 6
    const val PSYCH_WALL_THAT_RETURNS_TO_MOVING_LIFESPAN: Int = DEFAULT_WALL_TRAVEL_LIFESPAN - DEFAULT_PSYCH_WALL_TRAVEL_LIFESPAN

    const val LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_AN_ADJACENT_DIRECTION: Int = DEFAULT_WALL_TRAVEL_LIFESPAN - 7
    const val LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_THE_DIRECTION_THIS_WALL_IS_FACING: Int = DEFAULT_WALL_TRAVEL_LIFESPAN - 4

    //endregion


    object Locations {
        val WORLD: World = getWorld("world") ?: throw IllegalStateException("World 'world' not found. Please ensure the world is loaded.")

        // The pivot point - everything is centered around this point. The idea is that this is the center of the map
        val PIVOT: Location = Location(WORLD,0.0, 130.0, 0.0)

        // The offset needed to center the arena (deco) relative to walls and the floor of the game. SHOULD NOT BE REFERENCED FOR LOCATIONS OTHER THAN THE PLAYER SPAWN.
        val CENTER_OF_MAP: Location = PIVOT.clone().add(1.0, 0.0, -1.0)

        val SPAWN: Location = PIVOT.clone().add(0.0, 3.0, 0.0) // The spawn point of the player in the game.

        val PLATFORM: Location = PIVOT.clone()

        // The max value this can be is 17, since after that the walls will collide with the Letters Signs
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
            const val MIN_AMOUNT_OF_SPAWNS_TILL_CHANGING_DIRECTIONS: Int = 5 // How many walls must be spawned before the direction is allowed to change
        }
        object WallsFromAllDirections {
            const val CHANCE_THAT_PSYCH_WALL_WILL_GET_REMOVED: Int = (0.66 * 100).toInt()
        }
        object WallsFrom2OppositeDirections {
            const val CHANCE_OF_CHANGING_DIRECTIONS: Int = (0.15 * 100).toInt() //0.2 regularly
            const val CHANCE_OF_CONSIDERING_TO_SWAP_REAL_WALL_DIRECTION: Int = (0.4 * 100).toInt() // 0.4 regularly
            const val MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION: Int = HITWConst.MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION

            const val MIN_AMOUNT_OF_SPAWNS_TILL_CHANGING_DIRECTIONS_FOR_DUO: Int = 7
            const val MIN_AMOUNT_OF_SPAWNS_TILL_THERE_CAN_BE_CONSIDERATION_TO_SWAP_REAL_WALL_DIRECTION: Int = 3

            const val MAX_AMOUNT_OF_SPAWNS_TILL_THERE_MUST_BE_CHANGE: Int = 10
        }
    }

    object Timers {
        const val DELAY_BEFORE_STARTING_GAME: Long = 2*20 // in ticks
        const val GAME_DURATION: Int = 300 // in seconds

        val WALL_SPEED_UP_LANDMARKS: IntArray = intArrayOf(30, 60, 90, 120, 155, 200) // in seconds
        val INCREASE_WALL_DIFFICULTY_LANDMARKS: IntArray = intArrayOf(45, 90, 155) // in seconds
        val PLATFORM_SHRINKAGE_LANDMARKS: IntArray = intArrayOf(70, 155)

        val WALL_SPEED: IntArray = intArrayOf(15, 12, 10, 7, 6, 5, 4) //in ticks

        // *after the game knows that the wall can safely spawn in that direction, we'll make it wait extra for randomness
        val DELAY_BEFORE_SPAWNING_A_WALL_FROM_THE_SAME_DIRECTION: LongRange = 0L..12L // in ticks
        val DELAY_BEFORE_SPAWNING_A_WALL_FROM_A_DIFFERENT_DIRECTION: LongRange = 0L..5L // in ticks


        val STOPPED_WALL_DELAY_BEFORE_ACTION_DEALT: LongRange = 1L*20..2L*20 // for walls that haven't entered center


        const val ALTERNATING_WALL_SPAWNER_MODES_DELAY: Long = 20*20
    }
}