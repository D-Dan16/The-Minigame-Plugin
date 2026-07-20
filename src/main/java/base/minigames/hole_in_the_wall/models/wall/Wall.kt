package base.minigames.hole_in_the_wall.models.wall

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallReference
import base.minigames.hole_in_the_wall.wall_types.EarlyDecayedWall
import base.minigames.hole_in_the_wall.wall_types.JumpscareWall
import base.minigames.hole_in_the_wall.wall_types.RammingWall
import base.minigames.hole_in_the_wall.wall_types.PsychWall
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.utils.additions.Direction
import base.utils.other.BuildLoader
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import org.bukkit.Location
import org.bukkit.Material
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class Wall(
    val wallFile: File,
    /** The direction the wall is coming from. */
    directionWallComesFrom: Direction,
    val isFlipped: Boolean = false,
    val wallTypes: MutableList<WallType> = mutableListOf(),
    /** The spawn wave that created this wall. */
    val spawnBatch: WallSpawnBatch,
) {
    companion object {
        private val nextDebugId = AtomicInteger(1)
    }

    //region -- State --
    /** Stable identifier used for dev logging. */
    val debugId: Int = nextDebugId.getAndIncrement()
    /** Clipboard holder for the wall schematic. */
    lateinit var holder: ClipboardHolder
    /** The current direction the wall comes from. */
    var directionWallComesFrom: Direction = directionWallComesFrom
        set(direction) {
            field = direction

            // Update the direction the wall is facing based on the direction it comes from.
            directionWallIsFacing = when (direction) {
                Direction.SOUTH -> Direction.NORTH
                Direction.NORTH -> Direction.SOUTH
                Direction.WEST -> Direction.EAST
                Direction.EAST -> Direction.WEST
            }

            // Update the spawn location based on the new direction.
            spawnLocation = getWallSpawnPosition(direction)

            axisPositionFromSpawn = axisPositionRelativeToPlayerSpawn(spawnLocation, direction)

            // We will gather the schematic as a Clipboard from the wall file.
            // This is to easily and conveniently manipulate the schematic based on the characteristics of the wall.
            holder = BuildLoader.getClipboardHolderFromFile(wallFile, spawnLocation)

            // update the holder to reflect the new direction the wall is facing.
            BuildLoader.applyDirectionToClipboardHolder(holder, directionWallIsFacing)

            // Create the wall region based on the clipboard's dimensions.
            wallRegion = BuildLoader.getRotatedRegion(holder)
        }

    internal lateinit var directionWallIsFacing: Direction

    private var spawnLocation: Location = getWallSpawnPosition(directionWallComesFrom)
    /** The wall's lifecycle relative to the arena world. */
    internal var state: WallState = WallState.Queued
        private set
    lateinit var wallRegion: CuboidRegion

    /** How many blocks the wall travels before it stops moving. */
    var lifespanRemaining = calculateInitialLifespan()
    var lifespanTraveled = 0
    /** The wall's signed position on its travel axis, relative to `HITWConst.Locations.SPAWN`. */
    var axisPositionFromSpawn: Int = axisPositionRelativeToPlayerSpawn(spawnLocation, directionWallComesFrom)
    /** Whether the wall should be removed from the game when it stops moving. */
    var shouldBeRemoved: Boolean = false
    /** Whether gameplay has halted this wall's movement. A halted wall may later resume. */
    var isMovementHalted: Boolean = false
    /** Prevents repeated handling of stopped walls. */
    var isBeingHandled: Boolean = false

    /** Game-loop tick at which this wall may take its first movement step. */
    private var initialMovementUnlockTick: Int = 0

    /** Prevents a new move from starting before the previous delayed move has finished. */
    internal var isMoveInProgress: Boolean = false

    /** Tracks delayed move tasks so they can be canceled and cleaned up if the wall is deleted. */
    internal val pendingMoves: MutableList<PendingMove> = mutableListOf()

    /** Piston locations, initialized after the schematic has been pasted into the world. */
    internal lateinit var pistonLocations: MutableList<Location>

    //endregion

    /** Returns the spawn location for a wall coming from the given direction. */
    private fun getWallSpawnPosition(directionWallComesFrom: Direction): Location {
        val initialLocation = when (directionWallComesFrom) {
            Direction.SOUTH -> HITWConst.Locations.SOUTH_WALL_SPAWN.clone()
            Direction.NORTH -> HITWConst.Locations.NORTH_WALL_SPAWN.clone()
            Direction.WEST -> HITWConst.Locations.WEST_WALL_SPAWN.clone()
            Direction.EAST -> HITWConst.Locations.EAST_WALL_SPAWN.clone()
        }

        // Modify the spawn location based on the wall type's requirements.
        if (hasWallType<JumpscareWall>()) {
            when (directionWallComesFrom) {
                Direction.NORTH -> initialLocation.z += HITWConst.WallLifespans.JUMPSCARE_WALL_LIFESPAN_SHORTENER
                Direction.SOUTH -> initialLocation.z -= HITWConst.WallLifespans.JUMPSCARE_WALL_LIFESPAN_SHORTENER
                Direction.EAST -> initialLocation.x -= HITWConst.WallLifespans.JUMPSCARE_WALL_LIFESPAN_SHORTENER
                Direction.WEST -> initialLocation.x += HITWConst.WallLifespans.JUMPSCARE_WALL_LIFESPAN_SHORTENER
            }
        }

        return initialLocation.clone()
    }

    /** Returns a wall location's signed coordinate on its travel axis, relative to the player spawn. */
    private fun axisPositionRelativeToPlayerSpawn(location: Location, direction: Direction): Int {
        return when (direction) {
            Direction.NORTH, Direction.SOUTH -> location.blockZ - HITWConst.Locations.SPAWN.blockZ
            Direction.EAST, Direction.WEST -> location.blockX - HITWConst.Locations.SPAWN.blockX
        }
    }

    /**
     * Returns the two travel-axis cells occupied by this wall.
     *
     * The first entry is the slime/front cell. The second entry is the piston/rear cell.
     */
    fun occupiedAxisPositions(): IntArray {
        val frontAxisPosition = axisPositionFromSpawn
        val rearAxisPosition = when (directionWallIsFacing) {
            Direction.NORTH, Direction.WEST -> frontAxisPosition + 1
            Direction.SOUTH, Direction.EAST -> frontAxisPosition - 1
        }

        return intArrayOf(frontAxisPosition, rearAxisPosition)
    }

    /** Returns the arena axis this wall travels along. */
    fun getArenaAxis(): HITWConst.Locations.ArenaAxis {
        return when (directionWallComesFrom) {
            Direction.NORTH, Direction.SOUTH -> HITWConst.Locations.ArenaAxis.Z
            Direction.WEST, Direction.EAST -> HITWConst.Locations.ArenaAxis.X
        }
    }

    /** Marks this wall's occupied positions on the provided axis occupancy array. */
    fun markOnAxisOccupancy(axisOccupancy: Array<WallReference>) {
        occupiedAxisPositions().forEach { occupiedAxisPosition ->
            val index = HITWConst.Locations.relativeCoordinateToAxisIndex(getArenaAxis(), occupiedAxisPosition)

            if (index in axisOccupancy.indices) {
                axisOccupancy[index] = WallReference(this)
            }
        }
    }

    fun distanceTravelledFromSpawn(): Int {
        val spawnPosition = HITWConst.Locations.axisSpawnPosition(directionWallComesFrom)

        return when (directionWallComesFrom) {
            Direction.SOUTH, Direction.EAST -> spawnPosition - axisPositionFromSpawn
            Direction.NORTH, Direction.WEST -> axisPositionFromSpawn - spawnPosition
        }
    }


    /**
     * A wall has fully passed the middle ring only once both occupied axis cells are outside it.
     */
    fun hasPassedMiddleRing(): Boolean {
        val middleRingAxisRange = when (getArenaAxis()) {
            HITWConst.Locations.ArenaAxis.X ->
                HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.x()..
                    HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.x()

            HITWConst.Locations.ArenaAxis.Z ->
                HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.z()..
                    HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.z()
        }

        return occupiedAxisPositions().none { it in middleRingAxisRange }
    }

    //region -- Lifecycle --

    init {
        // Load the wall file and validate its contents if necessary
        if (wallFile.isDirectory) {
            throw IllegalArgumentException("Wall file cannot be a directory: ${wallFile.path}")
        }
        if (!wallFile.exists()) {
            throw IllegalArgumentException("Wall file does not exist: ${wallFile.path}")
        }

        // we will set the direction of the wall and update the holder to reflect the direction the wall is facing.
        this.directionWallComesFrom = directionWallComesFrom

        // mirror the schematic if the wall is flipped.
        if (isFlipped) {
            BuildLoader.mirrorClipboardHolder(holder, directionWallIsFacing)
        }

        // give the wall behaviors a ref to the wall so they can operate on it
        wallTypes.forEach { it.thisWall = this }

        wallTypes.forEach { it.activateRunnables() }
        // -------------------------------------------------------------------------------------------- //
    }

    /** Loads the wall schematic into the world at its spawn location. */
    fun spawn() {
        check(state == WallState.Queued) { "Wall#$debugId cannot spawn from state $state" }

        // Now we have the schematic ready to be pasted into the world.
        // after modifying the schematic, now we can finally paste the schematic into the world at the spawn location.
        BuildLoader.loadSchematic(holder)

        // Get the locations of all pistons in the wall region. important that this is done after the wall region is set (which it is only after loading the schem), since the method relies on the wall region to get the piston locations.
        initializeWallMotion()

        state = WallState.Spawned
        initialMovementUnlockTick = GameLoopRuntimeState.tickCount +
            (getWallType<JumpscareWall>()?.movementStartDelayTicks() ?: 0)
    }

    /** Moves the wall one block via its piston-based movement implementation. */
    fun move() = moveWallByPistons()

    /**
     * A jumpscare wall exists before it begins moving; this is distinct from [isMovementHalted], which
     * represents a wall that has been halted by gameplay after (or during) its movement.
     */
    internal fun isWaitingForInitialMovement(currentTick: Int): Boolean =
        currentTick < initialMovementUnlockTick

    /** Whether this spawned wall is currently progressing through its normal movement lifecycle. */
    internal fun isActivelyMoving(currentTick: Int): Boolean =
        state == WallState.Spawned &&
            lifespanRemaining > 0 &&
            !isMovementHalted &&
            !isWaitingForInitialMovement(currentTick)

    /** Advances the wall's lifespan counters and signed axis position by one step. */
    internal fun advanceOneStep() {
        lifespanRemaining--
        lifespanTraveled++
        axisPositionFromSpawn += when (directionWallComesFrom) {
            Direction.SOUTH, Direction.EAST -> -1
            Direction.NORTH, Direction.WEST -> 1
        }
    }

    /** Marks the wall as deleted and cancels any delayed tasks that have not yet been completed. */
    fun markDeleted() {
        if (state == WallState.Deleted) return

        state = WallState.Deleted
        cancelPendingMoves()
        wallTypes.forEach { it.clearRunnables() }
    }

    //endregion

    //region -- Wall types --

    private fun calculateInitialLifespan(): Int {
        // Decide the base lifespan
        var baseLifespan = when {
            hasWallType<PsychWall>() -> {
                getWallType<PsychWall>()!!.initialTravelLifespan
            }
            hasWallType<EarlyDecayedWall>() || hasWallType<RammingWall>() -> {
                getWallType<EarlyDecayedWall>()?.initialTravelLifespan
                    ?: getWallType<RammingWall>()!!.initialTravelLifespan
            }

            else -> HITWConst.WallLifespans.DEFAULT_WALL_TRAVEL_LIFESPAN
        }

        // Modify the lifespan based on additional wall types' requirements
        baseLifespan += when {
            hasWallType<JumpscareWall>() -> -HITWConst.WallLifespans.JUMPSCARE_WALL_LIFESPAN_SHORTENER
            else -> 0
        }

        return baseLifespan
    }

    /** Replaces the current wall type set with the provided types, deduplicated by id. */
    fun setWallTypes(types: Collection<WallType>) {
        wallTypes.clear()

        types.forEach { type ->
            if (wallTypes.none { it.id == type.id }) {
                wallTypes.add(type)
            }
        }
    }

    fun hasAnyWallTypes() = wallTypes.isNotEmpty()

    /** Adds a wall type if another type with the same id is not already present. */
    fun addWallType(type: WallType) {
        if (wallTypes.none { it.id == type.id }) {
            wallTypes.add(type)
        }
    }

    /** Removes every wall type whose id matches the given value. */
    fun removeWallType(typeId: String) {
        wallTypes.removeAll { it.id == typeId }
    }

    /** Returns `true` when a wall type with the given id is attached. */
    fun hasWallType(typeId: String): Boolean {
        return wallTypes.any { it.id == typeId }
    }

    /** Returns `true` when a wall type of the requested reified type is attached. */
    inline fun <reified T : WallType> hasWallType(): Boolean {
        return wallTypes.any { it is T }
    }

    /** Returns the first attached wall type of the requested reified type, if any. */
    inline fun <reified T : WallType> getWallType(): T? {
        return wallTypes.filterIsInstance<T>().firstOrNull()
    }


    //endregion

    /** Highlights the wall's bounding corners for debugging. */
    internal fun showBlocks() {
        fun putBlock(location: Location) {
            location.block.type = Material.DIAMOND_BLOCK
        }

        val min = Location(
            HITWConst.Locations.WORLD,
            wallRegion.minimumPoint.x().toDouble(),
            wallRegion.minimumPoint.y().toDouble(),
            wallRegion.minimumPoint.z().toDouble()
        )
        val max = Location(
            HITWConst.Locations.WORLD,
            wallRegion.maximumPoint.x().toDouble(),
            wallRegion.maximumPoint.y().toDouble(),
            wallRegion.maximumPoint.z().toDouble()
        )

        putBlock(min)
        putBlock(max)
    }

    override fun toString(): String {
        return "{spawnDir=$directionWallComesFrom, batch=${spawnBatch.id}, lifespanRem=$lifespanRemaining, wallTypes=$wallTypes}"
    }
}
