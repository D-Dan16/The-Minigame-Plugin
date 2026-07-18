package base.minigames.hole_in_the_wall.models.wall

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.utils.additions.Direction
import com.sk89q.worldedit.math.BlockVector3
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

/** A scheduled move and the temporary buttons it placed. */
internal data class PendingMove(
    val task: BukkitTask,
    val buttonLocations: List<Location>
)

/** Finds the pistons after this wall's schematic has been loaded into the world. */
internal fun Wall.initializeWallMotion() {
    pistonLocations = findPistonLocations()
}

/**
 * Moves the wall one block by triggering its pistons.
 *
 * A move is ignored if the wall is deleted, already moving, or has exhausted its lifespan.
 */
internal fun Wall.moveWallByPistons() {
    if (!canStartMove()) return

    scheduleMoveCompletion(placeMoveButtons())
}

/** Cancels outstanding moves and removes their temporary buttons. */
internal fun Wall.cancelPendingMoves() {
    pendingMoves.forEach { pendingMove ->
        pendingMove.task.cancel()
        clearMoveButtons(pendingMove.buttonLocations)
    }

    pendingMoves.clear()
    isMoveInProgress = false
}

private fun Wall.canStartMove(): Boolean {
    if (state != WallState.Spawned || isMoveInProgress) return false

    if (lifespanRemaining <= 0) {
        isMovementHalted = true
        return false
    }

    return true
}

private fun Wall.findPistonLocations(): MutableList<Location> {
    val locations = mutableListOf<Location>()

    for (x in wallRegion.minimumPoint.x()..wallRegion.maximumPoint.x()) {
        for (y in wallRegion.minimumPoint.y()..wallRegion.maximumPoint.y()) {
            for (z in wallRegion.minimumPoint.z()..wallRegion.maximumPoint.z()) {
                if (HITWConst.Locations.WORLD.getBlockAt(x, y, z).type == Material.PISTON) {
                    locations += Location(HITWConst.Locations.WORLD, x.toDouble(), y.toDouble(), z.toDouble())
                }
            }
        }
    }

    return locations
}

private fun Wall.placeMoveButtons(): List<Location> = pistonLocations.map { pistonLocation ->
    val buttonLocation = buttonLocationBehind(pistonLocation)
    if (buttonLocation.block.type != Material.AIR) reportWallCollision()

    placeAndPowerButton(buttonLocation)
    buttonLocation
}

private fun Wall.buttonLocationBehind(pistonLocation: Location): Location = when (directionWallIsFacing) {
    Direction.SOUTH -> pistonLocation.clone().add(0.0, 0.0, -1.0)
    Direction.NORTH -> pistonLocation.clone().add(0.0, 0.0, 1.0)
    Direction.WEST -> pistonLocation.clone().add(1.0, 0.0, 0.0)
    Direction.EAST -> pistonLocation.clone().add(-1.0, 0.0, 0.0)
}

private fun Wall.placeAndPowerButton(buttonLocation: Location) {
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

    val state: BlockState = buttonBlock.state
    val powerableState: Powerable = state.blockData as Powerable
    if (!powerableState.isPowered) {
        powerableState.isPowered = true
        state.blockData = powerableState
        state.update(true, true)
    }
}

private fun Wall.reportWallCollision() {
    val game = MinigamePlugin.plugin.getInstanceOfMinigame(
        MinigamePlugin.Companion.MinigameType.HOLE_IN_THE_WALL
    ) as HoleInTheWall

    game.pauseGame()
    HITWDevLogger.wall(this, "collision detected while placing move button")

    if (HITWConst.Development.IS_IN_DEVELOPMENT) {
        Bukkit.getServer().broadcast(
            Component.text("Two walls have seemed to collide. Cleaning the arena and pausing.")
                .color(NamedTextColor.YELLOW)
        )
    }
}

private fun Wall.scheduleMoveCompletion(buttonLocations: List<Location>) {
    isMoveInProgress = true

    var scheduledTask: BukkitTask? = null
    scheduledTask = Bukkit.getScheduler().runTaskLater(MinigamePlugin.plugin, Runnable {
        try {
            completeDeferredMove(buttonLocations)
        } finally {
            isMoveInProgress = false
            scheduledTask?.let { task -> pendingMoves.removeAll { it.task == task } }
        }
    }, 2L)

    pendingMoves += PendingMove(scheduledTask, buttonLocations)
}

private fun Wall.completeDeferredMove(buttonLocations: List<Location>) {
    if (state != WallState.Spawned) {
        HITWDevLogger.wall(this, "deferred move skipped because wall was deleted")
        return
    }

    shiftWallRegion()
    clearMoveButtons(buttonLocations)
    movePistonsForward()
    advanceOneStep()
    HITWDevLogger.wall(this, "lifespanRemaining=$lifespanRemaining | ${getArenaAxis()}: ${this.axisPositionFromSpawn}")
}

private fun Wall.shiftWallRegion() {
    when (directionWallIsFacing) {
        Direction.SOUTH -> wallRegion.shift(BlockVector3.at(0, 0, 1))
        Direction.NORTH -> wallRegion.shift(BlockVector3.at(0, 0, -1))
        Direction.WEST -> wallRegion.shift(BlockVector3.at(-1, 0, 0))
        Direction.EAST -> wallRegion.shift(BlockVector3.at(1, 0, 0))
    }
}

private fun clearMoveButtons(buttonLocations: List<Location>) {
    buttonLocations.forEach { it.block.type = Material.AIR }
}

private fun Wall.movePistonsForward() {
    pistonLocations.forEach { location ->
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
