package base.minigames.hole_in_the_wall.models

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallReference
import base.utils.additions.Direction
import base.utils.other.BuildLoader
import base.minigames.hole_in_the_wall.wall_types.PsychWallType
import base.minigames.hole_in_the_wall.wall_types.WallType
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.data.Powerable
import org.bukkit.block.data.type.Piston
import org.bukkit.block.data.type.Switch
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class Wall(
    val wallFile: File,
    /** The direction the wall is coming from. */
    directionWallComesFrom: Direction,
    val isFlipped: Boolean = false,
    val wallTypes: MutableList<WallType> = mutableListOf()
) {
    companion object {
        private val nextDebugId = AtomicInteger(1)
    }

    //region -- Properties --
    /** Stable identifier used for dev logging. */
    val debugId: Int = nextDebugId.getAndIncrement()

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

            axisPositionFromSpawn = HITWConst.Locations.axisSpawnPosition(direction)

            // We will gather the schematic as a Clipboard from the wall file.
            // This is to easily and conveniently manipulate the schematic based on the characteristics of the wall.
            holder = BuildLoader.getClipboardHolderFromFile(wallFile, spawnLocation)

            // update the holder to reflect the new direction the wall is facing.
            BuildLoader.applyDirectionToClipboardHolder(holder, directionWallIsFacing)

            // Create the wall region based on the clipboard's dimensions.
            wallRegion = BuildLoader.getRotatedRegion(holder)
        }
    private lateinit var directionWallIsFacing: Direction
    private var spawnLocation: Location = getWallSpawnPosition(directionWallComesFrom)

    /** Returns the spawn location for a wall coming from the given direction. */
    private fun getWallSpawnPosition(directionWallComesFrom: Direction): Location = when (directionWallComesFrom) {
        Direction.SOUTH -> HITWConst.Locations.SOUTH_WALL_SPAWN
        Direction.NORTH -> HITWConst.Locations.NORTH_WALL_SPAWN
        Direction.WEST -> HITWConst.Locations.WEST_WALL_SPAWN
        Direction.EAST -> HITWConst.Locations.EAST_WALL_SPAWN
    }


    lateinit var wallRegion: CuboidRegion
    lateinit var locationOfPistons: MutableList<Location>

    /** How many blocks the wall travels before it stops moving. */
    var lifespanRemaining = when {
        this.hasWallType<PsychWallType>() -> {
            if (this.getWallType<PsychWallType>()!!.isResumed)
                HITWConst.Walls.DEFAULT_WALL_TRAVEL_LIFESPAN
            else
                HITWConst.Walls.STOPPED_PSYCH_WALL_TRAVEL_LIFESPAN
        }
        else -> HITWConst.Walls.DEFAULT_WALL_TRAVEL_LIFESPAN
    }
    var lifespanTraveled = 0
    /** The wall's signed position on its travel axis, relative to `HITWConst.Locations.SPAWN`. */
    var axisPositionFromSpawn = HITWConst.Locations.axisSpawnPosition(directionWallComesFrom)


    /** Whether the wall should be removed from the game when it stops moving. */
    var shouldBeRemoved: Boolean = false
    /** Whether the wall should be stopped from moving. */
    var isStopped: Boolean = false

    /** Prevents repeated handling of stopped walls. */
    var isBeingHandled: Boolean = false

    /** Prevents a new move from starting before the previous delayed move has finished. */
    private var isMoveInProgress: Boolean = false

    /** Marks walls that have been removed from the arena. */
    internal var isDeleted: Boolean = false

    private data class PendingMove(
        val task: BukkitTask,
        val buttonLocations: List<Location>
    )

    /** Tracks delayed move tasks so they can be canceled and cleaned up if the wall is deleted. */
    private val pendingMoves: MutableList<PendingMove> = mutableListOf()

    //endregion

    /** Clipboard holder for the wall schematic. */
    lateinit var holder: ClipboardHolder

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

        // -------------------------------------------------------------------------------------------- //
    }

    /** Loads the wall schematic into the world at its spawn location. */
    fun makeWallExist() {
        // Now we have the schematic ready to be pasted into the world.
        // after modifying the schematic, now we can finally paste the schematic into the world at the spawn location.
        BuildLoader.loadSchematic(holder)

        // Get the locations of all pistons in the wall region. important that this is done after the wall region is set (which it is only after loading the schem), since the method relies on the wall region to get the piston locations.
        locationOfPistons = getPistonLocations()
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


    /** Collects all piston block locations currently inside this wall's region. */
    private fun getPistonLocations(): MutableList<Location> {
        // Get the locations of all piston blocks within the bounding box of the wall
        val locations = mutableListOf<Location>()

        for (x in wallRegion.minimumPoint.x()..wallRegion.maximumPoint.x()) {
            for (y in wallRegion.minimumPoint.y()..wallRegion.maximumPoint.y()) {
                for (z in wallRegion.minimumPoint.z()..wallRegion.maximumPoint.z()) {
                    val block = HITWConst.Locations.WORLD.getBlockAt(x, y, z)
                    // Only check blocks that are pistons
                    if (block.type == Material.PISTON) {
                        locations.add(
                            Location(
                                HITWConst.Locations.WORLD,
                                x.toDouble(),
                                y.toDouble(),
                                z.toDouble()
                            )
                        )
                    }
                }
            }
        }
        return locations
    }

    /** Marks the wall as deleted and cancels any delayed movement that has not yet completed. */
    fun markDeleted() {
        isDeleted = true
        cancelPendingMoves()
    }

    /** Cancels delayed movement tasks and removes any buttons they already placed. */
    private fun cancelPendingMoves() {
        pendingMoves.forEach { pendingMove ->
            pendingMove.task.cancel()
            clearMoveButtons(pendingMove.buttonLocations)
        }

        pendingMoves.clear()
        isMoveInProgress = false
    }


    /** Advances the wall's lifespan counters and signed axis position by one step. */
    private fun updateLifespans() {
        lifespanRemaining--
        lifespanTraveled++
        axisPositionFromSpawn += when (directionWallComesFrom) {
            Direction.SOUTH, Direction.EAST -> -1
            Direction.NORTH, Direction.WEST -> 1
        }
    }


    /**
     * Moves the wall in the specified direction by a singular block via activating the pistons.
     * Each wall has a lifespan, which is the number of blocks it can travel before it stops moving.
     * Each time the wall moves, its lifespan is decremented by 1.
     * If the wall has a lifespan of 0, it will be stopped, but not necessarily removed from the game. The game logic handles the logic for removing the wall.
     */
    fun move() {
        if (!canStartMove()) return

        val buttonLocations = placeMoveButtons()
//        HITWDevLogger.wall(this, "move scheduled with ${buttonLocations.size} buttons")
        scheduleMoveCompletion(buttonLocations)
    }

    /** Returns `true` when the wall can start a new move on this tick. */
    private fun canStartMove(): Boolean {
        if (isDeleted) return false
        if (isMoveInProgress) return false

        if (lifespanRemaining <= 0) {
            isStopped = true
            return false
        }

        return true
    }

    /** Places the buttons that power the wall's pistons and returns their locations. */
    private fun placeMoveButtons(): List<Location> {
        val buttonLocations = mutableListOf<Location>()

        locationOfPistons.forEach { pistonLocation ->
            val buttonLocation = getButtonLocationBehindPiston(pistonLocation)

            if (buttonLocation.block.type != Material.AIR) {
                reportWallCollision()
            }

            buttonLocations.add(buttonLocation)
            placeAndPowerButton(buttonLocation)
        }

        return buttonLocations
    }

    /** Returns the block location directly behind the piston for the current facing direction. */
    private fun getButtonLocationBehindPiston(pistonLocation: Location): Location {
        return when (directionWallIsFacing) {
            Direction.SOUTH -> pistonLocation.clone().add(0.0, 0.0, -1.0)
            Direction.NORTH -> pistonLocation.clone().add(0.0, 0.0, 1.0)
            Direction.WEST -> pistonLocation.clone().add(1.0, 0.0, 0.0)
            Direction.EAST -> pistonLocation.clone().add(-1.0, 0.0, 0.0)
        }
    }

    /** Puts a button behind the piston and powers it once to trigger the piston extension. */
    private fun placeAndPowerButton(buttonLocation: Location) {
        val buttonBlock: Block = buttonLocation.block
        buttonBlock.type = Material.STONE_BUTTON

        val data = buttonBlock.blockData as Switch
        data.facing = when (directionWallIsFacing) {
            Direction.SOUTH -> BlockFace.NORTH
            Direction.NORTH -> BlockFace.SOUTH
            Direction.WEST -> BlockFace.EAST
            Direction.EAST -> BlockFace.WEST
        }
        buttonBlock.blockData = data

        powerOnAndOffButton(buttonBlock)
    }

    /** Powers a button once to activate the piston it is attached to. */
    private fun powerOnAndOffButton(block: Block) {
        val state: BlockState = block.state
        val powerableState: Powerable = state.blockData as Powerable

        if (!powerableState.isPowered) {
            powerableState.isPowered = true
            state.blockData = powerableState
            state.update(true, true)
        }
    }

    /** Handles the collision case where a button cannot be placed because another block is there. */
    private fun reportWallCollision() {
        val game = MinigamePlugin.plugin.getInstanceOfMinigame(MinigamePlugin.Companion.MinigameType.HOLE_IN_THE_WALL) as HoleInTheWall

        game.pauseGame()
        HITWDevLogger.wall(this, "collision detected while placing move button")

        if (HITWConst.Development.IS_IN_DEVELOPMENT) {
            Bukkit.getServer().broadcast(
                Component.text("Two walls have seemed to collide. Cleaning the arena and pausing.").color(
                    NamedTextColor.YELLOW
                )
            )
        }
    }

    /** Schedules the wall's actual movement after the pistons have had time to extend. */
    private fun scheduleMoveCompletion(buttonLocations: List<Location>) {
        isMoveInProgress = true

        var scheduledTask: BukkitTask? = null
        scheduledTask = Bukkit.getScheduler().runTaskLater(MinigamePlugin.plugin, Runnable {
            try {
                completeDeferredMove(buttonLocations)
            } finally {
                isMoveInProgress = false
                scheduledTask?.let { task ->
                    pendingMoves.removeAll { it.task == task }
                }
            }
        }, 2L)

        pendingMoves.add(PendingMove(scheduledTask, buttonLocations))
    }

    /** Finishes the delayed part of the wall move if the wall still exists. */
    private fun completeDeferredMove(buttonLocations: List<Location>) {
        if (isDeleted) {
            HITWDevLogger.wall(this, "deferred move skipped because wall was deleted")
            return
        }

        shiftWallRegion()
        clearMoveButtons(buttonLocations)
        movePistonsForward()
        updateLifespans()
        HITWDevLogger.wall(this, "lifespanRemaining=$lifespanRemaining")
    }

    /** Moves the wall region one block forward along its travel direction. */
    private fun shiftWallRegion() {
        when (directionWallIsFacing) {
            Direction.SOUTH -> wallRegion.shift(BlockVector3.at(0, 0, 1))
            Direction.NORTH -> wallRegion.shift(BlockVector3.at(0, 0, -1))
            Direction.WEST -> wallRegion.shift(BlockVector3.at(-1, 0, 0))
            Direction.EAST -> wallRegion.shift(BlockVector3.at(1, 0, 0))
        }
    }

    /** Removes the temporary buttons that were used to trigger the move. */
    private fun clearMoveButtons(buttonLocations: List<Location>) {
        buttonLocations.forEach { location ->
            location.block.type = Material.AIR
        }
    }

    /** Moves the pistons to the next wall position after the wall has shifted. */
    private fun movePistonsForward() {
        locationOfPistons.forEach { location ->
            location.block.type = Material.AIR

            when (directionWallIsFacing) {
                Direction.SOUTH -> location.add(0.0, 0.0, 1.0)
                Direction.NORTH -> location.add(0.0, 0.0, -1.0)
                Direction.WEST -> location.add(-1.0, 0.0, 0.0)
                Direction.EAST -> location.add(1.0, 0.0, 0.0)
            }

            if (lifespanRemaining > 0) {
                location.block.type = Material.PISTON
                val pistonData = location.block.blockData as Piston
                pistonData.facing = when (directionWallIsFacing) {
                    Direction.SOUTH -> BlockFace.SOUTH
                    Direction.NORTH -> BlockFace.NORTH
                    Direction.WEST -> BlockFace.WEST
                    Direction.EAST -> BlockFace.EAST
                }

                location.block.blockData = pistonData
            }
        }
    }

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
        return "{dir=$directionWallComesFrom, lifespanRem=$lifespanRemaining, wallTypes=$wallTypes}"
    }
}
