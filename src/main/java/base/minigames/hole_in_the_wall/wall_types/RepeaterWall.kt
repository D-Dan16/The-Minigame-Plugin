package base.minigames.hole_in_the_wall.wall_types

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.models.wall.WallAxisCoordinate
import base.minigames.hole_in_the_wall.models.wall.WallState
import base.minigames.hole_in_the_wall.models.wall.playTeleportAnimation
import base.utils.additions.Direction
import org.bukkit.scheduler.BukkitRunnable
import kotlin.collections.plusAssign
import kotlin.math.abs
import kotlin.random.Random

/**
 * A wall that once hit a certain threshold stops in place and teleports backwards to the stop sign it came from.
 * The tp threshold is randomized, however, It's somewhere around the 2nd half of the mid-platform
 *
 * Emits WITCH particles throughout the TP action.
 */
class RepeaterWall : WallType() {
    override val id: String = "repeater"
    override fun toString(): String = "$id(tpFrom:${coordinates.tpFromLoc}|tpTo:${coordinates.tpToLoc})"

    private lateinit var coordinates: RepeaterTeleportCoordinates
    internal var finishedTeleportation = false

    override fun activateRunnables() {
        teleportOnceThresholdHit()
    }

    private fun teleportOnceThresholdHit() {
        runnables += object : BukkitRunnable() {
            override fun run() {
                when (thisWall.state) {
                    WallState.Queued -> {}
                    WallState.Deleted -> {
                        cancel()
                    }
                    WallState.Spawned -> {
                        if (thisWall.axisLocation.coordinate == coordinates.tpFromLoc) {
                            playTeleportAnimation()
                            cancel()
                        }
                    }
                }
            }
        }.also { it.runTaskTimer(MinigamePlugin.plugin, 0L, 1L) }
    }

    private fun playTeleportAnimation() {
        thisWall.playTeleportAnimation(
            destination = WallAxisCoordinate(coordinates.tpToLoc, thisWall.axisLocation.axis),
            newDirWallComesFrom = thisWall.directionWallComesFrom,
            game = holeInTheWall,
            animationOwner = this,
        )
    }

    /** Returns the additional travel needed after the wall is sent back to its own stop sign. */
    fun calculateExtraLifespan(directionWallComesFrom: Direction): Int {
        coordinates = createTeleportCoordinates(directionWallComesFrom)
        return abs(coordinates.tpToLoc - coordinates.tpFromLoc)
    }

    /** A same-direction follower must wait until this wall has completed its rewind. */
    internal fun hasPendingTeleport(): Boolean = !finishedTeleportation

    private fun createTeleportCoordinates(directionWallComesFrom: Direction): RepeaterTeleportCoordinates {
        val directionWallIsFacing = directionWallComesFrom.getOpposite()
        val movementDelta = when (directionWallComesFrom) {
            Direction.SOUTH, Direction.EAST -> -1
            Direction.NORTH, Direction.WEST -> 1
        }
        val blocksBeforeStopSign = Random.nextInt(
            1,
            HITWConst.RepeaterWall.TELEPORT_TRIGGER_BLOCKS_BEFORE_STOP_SIGN + 1
        )
        val tpFromLoc = HITWConst.Locations.stopSignAxisPosition(directionWallIsFacing) -
            movementDelta * blocksBeforeStopSign
        val tpToLoc = HITWConst.Locations.stopSignAxisPosition(directionWallComesFrom)

        return RepeaterTeleportCoordinates(tpFromLoc, tpToLoc)
    }
}

private data class RepeaterTeleportCoordinates(
    val tpFromLoc: Int,
    val tpToLoc: Int
)
