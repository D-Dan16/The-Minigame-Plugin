package base.minigames.hole_in_the_wall.models.wall

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallReference
import base.minigames.hole_in_the_wall.wall_types.DoominatorWall
import base.minigames.hole_in_the_wall.wall_types.EarlyDecayedWall
import base.minigames.hole_in_the_wall.wall_types.JumpscareWall
import base.minigames.hole_in_the_wall.wall_types.RammingWall
import base.minigames.hole_in_the_wall.wall_types.PsychWall
import base.minigames.hole_in_the_wall.wall_types.RepeaterWall
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.utils.additions.Direction
import base.utils.other.BuildLoader
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.TextDisplay
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Identifies whether a wall reached the end of its lifespan normally or was destroyed by another effect. */
internal enum class WallDecayCause {
    NATURAL,
    DOOMINATOR_NUKE,
    RAMMING,
}

class Wall(
    val holeInTheWall: HoleInTheWall,
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
        private set

    internal lateinit var directionWallIsFacing: Direction

    /** The wall's original spawn location, retained for its full lifetime. */
    private val spawnLocation: Location = getWallSpawnPosition(directionWallComesFrom)
    /** The wall's lifecycle relative to the arena world. */
    internal var state: WallState = WallState.Queued
        private set
    lateinit var wallRegion: CuboidRegion

    /** How many blocks the wall travels before it stops moving. */
    var lifespanRemaining = calculateInitialLifespan()
    var lifespanTraveled = 0
    /** The wall's signed position and travel axis, relative to `HITWConst.Locations.SPAWN`. */
    var axisLocation: WallAxisCoordinate = axisLocationRelativeToPlayerSpawn(spawnLocation, directionWallComesFrom)
    /** Whether the wall should be removed from the game when it stops moving. */
    var shouldBeRemoved: Boolean = false
    /** Whether gameplay has halted this wall's movement. A halted wall may later resume. */
    var isMovementHalted: Boolean = false
    /** Prevents repeated handling of stopped walls. */
    var isBeingHandled: Boolean = false
    /** The action to take when this wall reaches runs out of lifespan and has been removed from the arena.*/
    var actionsWhenDecayed: MutableList<Runnable> = mutableListOf()
    /** Why this wall's lifespan ended. Nuke-caused decay must not trigger another Doominator alert. */
    internal var decayCause: WallDecayCause = WallDecayCause.NATURAL

    /** Game-loop tick at which this wall may take its first movement step. */
    private var initialMovementUnlockTick: Int = 0

    /** Prevents a new move from starting before the previous delayed move has finished. */
    internal var isMoveInProgress: Boolean = false

    /** Tracks delayed move tasks so they can be canceled and cleaned up if the wall is deleted. */
    internal val pendingMoves: MutableList<PendingMove> = mutableListOf()

    /** Development-only label showing this wall's stable debug id. */
    private var debugIdDisplay: TextDisplay? = null

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

    /** Returns the normal spawn anchor for a wall coming from [direction]. */
    internal fun spawnAnchorFor(direction: Direction): Location = getWallSpawnPosition(direction)

    /** Returns a wall location's signed coordinate and travel axis, relative to the player spawn. */
    private fun axisLocationRelativeToPlayerSpawn(location: Location, direction: Direction): WallAxisCoordinate {
        val axis = arenaAxisFor(direction)
        val coordinate = when (axis) {
            HITWConst.Locations.ArenaAxis.X -> location.blockX - HITWConst.Locations.SPAWN.blockX
            HITWConst.Locations.ArenaAxis.Z -> location.blockZ - HITWConst.Locations.SPAWN.blockZ
        }

        return WallAxisCoordinate(coordinate, axis)
    }

    /** Rebuilds the wall clipboard and bounds at [location] using its current facing direction. */
    internal fun rebuildSchematicAt(location: Location) {
        holder = BuildLoader.getClipboardHolderFromFile(wallFile, location)
        BuildLoader.applyDirectionToClipboardHolder(holder, directionWallIsFacing)

        if (isFlipped) {
            BuildLoader.mirrorClipboardHolder(holder, directionWallIsFacing)
        }

        wallRegion = BuildLoader.getRotatedRegion(holder)
    }

    /** Updates direction-dependent state without changing the wall's location or spawn anchor. */
    internal fun updateDirection(direction: Direction) {
        directionWallComesFrom = direction
        directionWallIsFacing = when (direction) {
            Direction.SOUTH -> Direction.NORTH
            Direction.NORTH -> Direction.SOUTH
            Direction.WEST -> Direction.EAST
            Direction.EAST -> Direction.WEST
        }
    }

    /**
     * Returns the two travel-axis cells occupied by this wall.
     *
     * The first entry is the slime/front cell. The second entry is the piston/rear cell.
     */
    fun occupiedAxisPositions(): IntArray {
        val frontAxisPosition = axisLocation.coordinate
        val rearAxisPosition = when (directionWallIsFacing) {
            Direction.NORTH, Direction.WEST -> frontAxisPosition + 1
            Direction.SOUTH, Direction.EAST -> frontAxisPosition - 1
        }

        return intArrayOf(frontAxisPosition, rearAxisPosition)
    }

    /** Returns the travel axis for a wall moving in [direction]. */
    private fun arenaAxisFor(direction: Direction): HITWConst.Locations.ArenaAxis {
        return when (direction) {
            Direction.NORTH, Direction.SOUTH -> HITWConst.Locations.ArenaAxis.Z
            Direction.WEST, Direction.EAST -> HITWConst.Locations.ArenaAxis.X
        }
    }

    /** Marks this wall's occupied positions on the provided axis occupancy array. */
    fun markOnAxisOccupancy(axisOccupancy: Array<WallReference>) {
        occupiedAxisPositions().forEach { occupiedAxisPosition ->
            val index = HITWConst.Locations.relativeCoordinateToAxisIndex(axisLocation.axis, occupiedAxisPosition)

            if (index in axisOccupancy.indices) {
                axisOccupancy[index] = WallReference(this)
            }
        }
    }

    fun distanceTravelledFromSpawn(): Int {
        val spawnPosition = HITWConst.Locations.axisSpawnPosition(directionWallComesFrom)

        return when (directionWallComesFrom) {
            Direction.SOUTH, Direction.EAST -> spawnPosition - axisLocation.coordinate
            Direction.NORTH, Direction.WEST -> axisLocation.coordinate - spawnPosition
        }
    }


    /**
     * A wall has fully passed the middle ring only once both occupied axis cells are outside it.
     */
    fun hasPassedMiddleRing(): Boolean {
        val middleRingAxisRange = when (axisLocation.axis) {
            HITWConst.Locations.ArenaAxis.X ->
                HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.x()..
                    HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.x()

            HITWConst.Locations.ArenaAxis.Z ->
                HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.z()..
                    HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.z()
        }

        return occupiedAxisPositions().none { it in middleRingAxisRange }
    }

    init {
        // Load the wall file and validate its contents if necessary
        if (wallFile.isDirectory) {
            throw IllegalArgumentException("Wall file cannot be a directory: ${wallFile.path}")
        }
        if (!wallFile.exists()) {
            throw IllegalArgumentException("Wall file does not exist: ${wallFile.path}")
        }

        // Configure direction-dependent state, then build the schematic at its normal spawn.
        updateDirection(directionWallComesFrom)
        rebuildSchematicAt(spawnLocation)

        // give the wall behaviors a ref to the wall so they can operate on it
        wallTypes.forEach { it.thisWall = this }

        wallTypes.forEach { it.activateRunnables() }

        if (hasWallType<DoominatorWall>())
            actionsWhenDecayed += Runnable {
                getWallType<DoominatorWall>()!!.alertUpcomingWallNuking()
            }
        // -------------------------------------------------------------------------------------------- //
    }

    //region -- Lifecycle --

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
        createDebugIdDisplay()
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

    /** Advances the wall's lifespan counters and signed axis location by one step. */
    internal fun advanceOneStep() {
        lifespanRemaining--
        lifespanTraveled++
        val coordinateDelta = when (directionWallComesFrom) {
            Direction.SOUTH, Direction.EAST -> -1
            Direction.NORTH, Direction.WEST -> 1
        }
        axisLocation = axisLocation.copy(coordinate = axisLocation.coordinate + coordinateDelta)
    }

    /** Marks the wall as deleted and cancels any delayed tasks that have not yet been completed. */
    fun markDeleted() {
        if (state == WallState.Deleted) return

        state = WallState.Deleted
        removeDebugIdDisplay()
        cancelPendingMoves()
        wallTypes.forEach { it.clearRunnables() }
    }

    /** Creates the development-only id label above this wall. */
    private fun createDebugIdDisplay() {
        if (!HITWConst.Development.IS_IN_DEVELOPMENT) return

        debugIdDisplay = HITWConst.Locations.WORLD.spawn(debugDisplayLocation(), TextDisplay::class.java) { display ->
            display.text(debugDisplayText())
            display.billboard = Billboard.CENTER
            display.isSeeThrough = true
            display.isPersistent = false
        }
    }

    /** Moves the development-only id label above the wall's current schematic bounds. */
    internal fun updateDebugIdDisplayLocation() {
        debugIdDisplay?.apply {
            text(debugDisplayText())
            teleport(debugDisplayLocation())
        }
    }

    private fun debugDisplayText(): Component = Component.text(
        "wall#$debugId\n${wallTypes.joinToString().ifEmpty { "normal" }}"
    )

    private fun removeDebugIdDisplay() {
        debugIdDisplay?.remove()
        debugIdDisplay = null
    }

    private fun debugDisplayLocation(): Location = Location(
        HITWConst.Locations.WORLD,
        (wallRegion.minimumPoint.x() + wallRegion.maximumPoint.x() + 1) / 2.0,
        wallRegion.maximumPoint.y() + 3.0,
        (wallRegion.minimumPoint.z() + wallRegion.maximumPoint.z() + 1) / 2.0
    )

    //endregion

    //region -- Wall types --

    private fun calculateInitialLifespan(): Int {
        val psychWall = getWallType<PsychWall>()

        // Decide the base lifespan
        var baseLifespan = when {
            // A Psych wall that will decay at its stop sign must keep its short lifespan. A
            // resumed Psych wall otherwise behaves like a normal wall, so it must not override
            // a combined lifespan modifier such as Early Decayed.
            psychWall?.canResume == false -> {
                HITWConst.WallLifespans.STOPPED_PSYCH_WALL_TRAVEL_LIFESPAN
            }
            hasWallType<EarlyDecayedWall>() -> HITWConst.WallLifespans.FAST_DECAYED_WALL_LIFESPAN_RANGE.random()
            hasWallType<RammingWall>() -> HITWConst.WallLifespans.RAMMING_WALL_LIFESPAN

            else -> HITWConst.WallLifespans.DEFAULT_WALL_TRAVEL_LIFESPAN
        }

        // Modify the lifespan based on additional wall types' requirements
        if (hasWallType<JumpscareWall>()) baseLifespan -= HITWConst.WallLifespans.JUMPSCARE_WALL_LIFESPAN_SHORTENER
        if (hasWallType<RepeaterWall>()) baseLifespan += getWallType<RepeaterWall>()!!.calculateExtraLifespan(directionWallComesFrom)

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
