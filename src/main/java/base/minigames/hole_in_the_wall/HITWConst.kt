package base.minigames.hole_in_the_wall

import base.utils.extensions_for_classes.toBlockVector
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallReference
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.regions.Regions
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.Parser
import org.bukkit.Bukkit.getWorld
import org.bukkit.Location
import org.bukkit.World
import base.utils.additions.Direction


object HITWConst {
    /** Whether the plugin is running in development mode. */
    const val IS_IN_DEVELOPMENT: Boolean = false

    const val PLATFORMS_FOLDER: String = "platforms"
    const val EASY_WALLPACK_FOLDER: String = "easy"
    const val MEDIUM_WALLPACK_FOLDER: String = "medium"
    const val HARD_WALLPACK_FOLDER: String = "hard"
    const val VERY_HARD_WALLPACK_FOLDER: String = "very_hard"

    const val WALLPACK_FOLDER: String = "wallpack"
    const val MAP_FOLDER: String = "map"
    val availableMaps: List<String> = listOf("Map1", "Map2", "Map3")

    //region wall constants that aren't tied to a specific wall spawner mode

    /** Maximum number of walls that can exist at once. */
    const val HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS: Int = 6

    /** Default travel lifespan for a regular wall. */
    const val DEFAULT_WALL_TRAVEL_LIFESPAN: Int = 24
    /** Default travel lifespan for a psych wall that dies when stopped. */
    const val PSYCH_WALL_TRAVEL_LIFESPAN: Int = 6

    const val MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION_FROM_SPAWN = 6

    const val TRAVEL_DISTANCE_THAT_LETS_YOU_SPAWN_A_WALL_FROM_AN_ADJACENT_DIRECTION: Int = DEFAULT_WALL_TRAVEL_LIFESPAN - 7//25
    const val TRAVEL_DISTANCE_THAT_LETS_YOU_SPAWN_A_WALL_FROM_THE_DIRECTION_THIS_WALL_IS_FACING: Int = DEFAULT_WALL_TRAVEL_LIFESPAN - 4 //15

    //endregion

    object Locations {
        val WORLD: World = getWorld("world") ?: throw IllegalStateException("World 'world' not found. Please ensure the world is loaded.")

        /** The pivot point all arena locations are centered around. */
        val PIVOT: Location = Location(WORLD, 0.0, 130.0, 0.0)

        /** Offset used to center the arena relative to walls and floor. */
        val CENTER_OF_MAP: Location = PIVOT.clone().add(1.0, 0.0, -1.0)

        /** The player spawn point. */
        val SPAWN: Location = PIVOT.clone().add(0.0, 3.0, 0.0)

        val PLATFORM: Location = PIVOT.clone()

        object PlatformGeometry {
            /** The platform is 12x12, centered, so its block offsets run from -5 to 6. */
            private const val PLATFORM_MIN_HORIZONTAL_OFFSET: Int = -5
            private const val PLATFORM_MAX_HORIZONTAL_OFFSET: Int = 6

            /** The area above the platform includes the platform itself plus a one-block buffer. */
            private const val ABOVE_PLATFORM_MIN_HORIZONTAL_OFFSET: Int = -6
            private const val ABOVE_PLATFORM_MAX_HORIZONTAL_OFFSET: Int = 7
            private const val ABOVE_PLATFORM_MIN_Y_OFFSET: Int = 1
            private const val ABOVE_PLATFORM_MAX_Y_OFFSET: Int = 5

            private const val NORTH_STOP_SIGN_Z: Int = -9
            private const val EAST_STOP_SIGN_X: Int = 9
            private const val SOUTH_STOP_SIGN_Z: Int = 8
            private const val WEST_STOP_SIGN_X: Int = -8

            private const val STOP_SIGN_MIN_Y: Int = 1
            private const val STOP_SIGN_MAX_Y: Int = 6

            private fun lineRegion(
                minX: Int,
                maxX: Int,
                minZ: Int,
                maxZ: Int
            ): CuboidRegion {
                return CuboidRegion(
                    PLATFORM.clone().add(minX.toDouble(), STOP_SIGN_MIN_Y.toDouble(), minZ.toDouble()).toBlockVector(),
                    PLATFORM.clone().add(maxX.toDouble(), STOP_SIGN_MAX_Y.toDouble(), maxZ.toDouble()).toBlockVector()
                )
            }

            val NORTH_STOP_SIGN_REGION: CuboidRegion = lineRegion(
                PLATFORM_MIN_HORIZONTAL_OFFSET,
                PLATFORM_MAX_HORIZONTAL_OFFSET,
                NORTH_STOP_SIGN_Z,
                NORTH_STOP_SIGN_Z
            )
            val EAST_STOP_SIGN_REGION: CuboidRegion = lineRegion(
                EAST_STOP_SIGN_X,
                EAST_STOP_SIGN_X,
                PLATFORM_MIN_HORIZONTAL_OFFSET,
                PLATFORM_MAX_HORIZONTAL_OFFSET
            )
            val SOUTH_STOP_SIGN_REGION: CuboidRegion = lineRegion(
                PLATFORM_MIN_HORIZONTAL_OFFSET,
                PLATFORM_MAX_HORIZONTAL_OFFSET,
                SOUTH_STOP_SIGN_Z,
                SOUTH_STOP_SIGN_Z
            )
            val WEST_STOP_SIGN_REGION: CuboidRegion = lineRegion(
                WEST_STOP_SIGN_X,
                WEST_STOP_SIGN_X,
                PLATFORM_MIN_HORIZONTAL_OFFSET,
                PLATFORM_MAX_HORIZONTAL_OFFSET
            )

            val ABOVE_PLATFORM_REGION: CuboidRegion = CuboidRegion(
                PLATFORM.clone().add(ABOVE_PLATFORM_MIN_HORIZONTAL_OFFSET.toDouble(), ABOVE_PLATFORM_MIN_Y_OFFSET.toDouble(), ABOVE_PLATFORM_MIN_HORIZONTAL_OFFSET.toDouble()).toBlockVector(),
                PLATFORM.clone().add(ABOVE_PLATFORM_MAX_HORIZONTAL_OFFSET.toDouble(), ABOVE_PLATFORM_MAX_Y_OFFSET.toDouble(), ABOVE_PLATFORM_MAX_HORIZONTAL_OFFSET.toDouble()).toBlockVector()
            )
        }

        /** Maximum wall distance before colliding with the letter signs. */
        const val DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM: Double = 16.0

        val SOUTH_WALL_SPAWN: Location = PLATFORM.clone().add(1.0, 1.0, DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM)
        val NORTH_WALL_SPAWN: Location = PLATFORM.clone().add(0.0, 1.0, -DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM - 1.0)
        val WEST_WALL_SPAWN: Location = PLATFORM.clone().add(-DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM, 1.0, 0.0)
        val EAST_WALL_SPAWN: Location = PLATFORM.clone().add(DISTANCE_OF_WALL_FROM_CENTER_OF_PLATFORM + 1.0, 1.0, -1.0)

        enum class ArenaAxis { X, Z }

        private val X_AXIS_RELATIVE_MIN_TO_SPAWN: Int = WEST_WALL_SPAWN.clone().blockX - SPAWN.blockX
        private val X_AXIS_RELATIVE_MAX_TO_SPAWN: Int = EAST_WALL_SPAWN.clone().blockX - SPAWN.blockX
        private val Z_AXIS_RELATIVE_MIN_TO_SPAWN: Int = NORTH_WALL_SPAWN.clone().blockZ - SPAWN.blockZ
        private val Z_AXIS_RELATIVE_MAX_TO_SPAWN: Int = SOUTH_WALL_SPAWN.clone().blockZ - SPAWN.blockZ

        /**
         * Creates two arrays that keep track of the axis occupation.
         */
        fun createWallAxisOccupancies(): Pair<Array<WallReference>, Array<WallReference>> {
            fun createAxisOccupancy(axis: ArenaAxis): Array<WallReference> {
                val size = when (axis) {
                    ArenaAxis.X -> X_AXIS_RELATIVE_MAX_TO_SPAWN - X_AXIS_RELATIVE_MIN_TO_SPAWN + 1
                    ArenaAxis.Z -> Z_AXIS_RELATIVE_MAX_TO_SPAWN - Z_AXIS_RELATIVE_MIN_TO_SPAWN + 1
                }

                return Array(size) { WallReference(null) }
            }

            return createAxisOccupancy(ArenaAxis.X) to createAxisOccupancy(ArenaAxis.Z)
        }

        /**
         * The point is that the indices of the arrays that keep track of the axis occupation must be non-negative,
         * so we convert a potential negative displacement, into a positive number, to put it into the array.
         */
        fun relativeCoordinateToAxisIndex(axis: ArenaAxis, relativeCoordinateToSpawn: Int): Int {
            return when (axis) {
                ArenaAxis.X -> relativeCoordinateToSpawn - X_AXIS_RELATIVE_MIN_TO_SPAWN
                ArenaAxis.Z -> relativeCoordinateToSpawn - Z_AXIS_RELATIVE_MIN_TO_SPAWN
            }
        }

        fun axisSpawnPosition(direction: Direction): Int {
            return when (direction) {
                Direction.SOUTH -> SOUTH_WALL_SPAWN.clone().blockZ - SPAWN.blockZ
                Direction.NORTH -> NORTH_WALL_SPAWN.clone().blockZ - SPAWN.blockZ
                Direction.WEST -> WEST_WALL_SPAWN.clone().blockX - SPAWN.blockX
                Direction.EAST -> EAST_WALL_SPAWN.clone().blockX - SPAWN.blockX
            }
        }

        fun axisPositionAfterTravelFromSpawn(direction: Direction, travelDistanceFromSpawn: Int): Int {
            return when (direction) {
                Direction.SOUTH, Direction.EAST -> axisSpawnPosition(direction) - travelDistanceFromSpawn
                Direction.NORTH, Direction.WEST -> axisSpawnPosition(direction) + travelDistanceFromSpawn
            }
        }

        fun axisPositionForSameDirectionSpawn(direction: Direction): Int {
            return axisPositionAfterTravelFromSpawn(direction, MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION_FROM_SPAWN)
        }

        fun axisPositionForAdjacentDirectionSpawn(direction: Direction): Int {
            return axisPositionAfterTravelFromSpawn(direction, TRAVEL_DISTANCE_THAT_LETS_YOU_SPAWN_A_WALL_FROM_AN_ADJACENT_DIRECTION)
        }

        fun axisPositionForFacingDirectionSpawn(direction: Direction): Int {
            return axisPositionAfterTravelFromSpawn(direction, TRAVEL_DISTANCE_THAT_LETS_YOU_SPAWN_A_WALL_FROM_THE_DIRECTION_THIS_WALL_IS_FACING)
        }

        fun hasReachedAxisPosition(direction: Direction, currentAxisPosition: Int, targetAxisPosition: Int): Boolean {
            return when (direction) {
                Direction.SOUTH, Direction.EAST -> currentAxisPosition <= targetAxisPosition
                Direction.NORTH, Direction.WEST -> currentAxisPosition >= targetAxisPosition
            }
        }
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

    object Timers {
        /** Delay before the game starts, in ticks. */
        const val DELAY_BEFORE_STARTING_GAME: Long = 2*20
        /** Game duration in seconds. */
        const val GAME_DURATION: Int = 300

        /** Wall speeds in ticks. */
        val WALL_SPEED: IntArray = intArrayOf(10, 9, 8, 7, 6, 5, 4)
        /** Wall speed increase landmarks in seconds. */
        val WALL_SPEED_UP_LANDMARKS: IntArray = intArrayOf(15, 30, 45, 60, 75, 100)
        /** Wall difficulty increase landmarks in seconds. */
        val INCREASE_WALL_DIFFICULTY_LANDMARKS: IntArray = intArrayOf(20, 45, 70)

        val PLATFORM_SHRINKAGE_LANDMARKS: IntArray = intArrayOf(35, 75)

        /**
         * delay until decay for Psych walls that don't reach mid, and ran out of lifespan
         */
        val DEAD_PSYCHE_WALL_TIME_TILL_DECAY_RANGE: IntRange = 0..3

        // *after the game knows that the wall can safely spawn in that direction, we'll make it wait extra for randomness
        /** Random delay range before spawning from the same direction. */
        val DELAY_BEFORE_SPAWNING_A_WALL_FROM_THE_SAME_DIRECTION: LongRange = 0L..12L
        /** Random delay range before spawning from a different direction. */
        val DELAY_BEFORE_SPAWNING_A_WALL_FROM_A_DIFFERENT_DIRECTION: LongRange = 0L..5L

        /** Delay before taking action on a stopped wall that has not entered the center. */
        val STOPPED_WALL_DELAY_BEFORE_ACTION_DEALT: LongRange = 1L*20..2L*20
    }
}
