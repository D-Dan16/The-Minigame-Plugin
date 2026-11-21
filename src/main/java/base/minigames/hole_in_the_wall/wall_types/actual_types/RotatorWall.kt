package base.minigames.hole_in_the_wall.wall_types.actual_types

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.models.wall.WallAxisCoordinate
import base.minigames.hole_in_the_wall.models.wall.WallState
import base.minigames.hole_in_the_wall.models.wall.startTeleportation
import base.minigames.hole_in_the_wall.wall_types.Teleporter
import base.utils.additions.Direction
import org.bukkit.scheduler.BukkitRunnable
import kotlin.collections.plusAssign
import kotlin.random.Random

/**
 * TODO: NOT WORKING
 *
 * A wall that while at mid rotates itself by 90 degrees and then continues moving on.
 */
class RotatorWall : Teleporter() {
    companion object {
        internal const val ID = "Rotator"
        internal const val DESCRIPTION = "A wall that while at mid rotates itself by 90 degrees and then continues moving on."
    }

    lateinit var initialDirection: Direction
    lateinit var rotatedDirection: Direction

    override fun toString(): String = "$ID(rotateFrom:${initialDirection} | rotateTo:${rotatedDirection})"

    override fun initStateAndRunnables() {
        initialDirection = thisWall.directionWallComesFrom
        rotatedDirection = if (Random.nextBoolean())
            initialDirection.getClockwise()
        else
            initialDirection.getCounterClockwise()

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
                        if (allowedToRotate()) {
                            startTeleportation()
                            cancel()
                        }
                    }
                }
            }

        }.also { it.runTaskTimer(MinigamePlugin.plugin, 0L, 1L) }

    }

    //TODO: temp
    private fun allowedToRotate(): Boolean {
        return when (initialDirection) {
            Direction.NORTH -> thisWall.axisLocation.coordinate == 0
            Direction.SOUTH -> thisWall.axisLocation.coordinate == 0
            Direction.EAST -> thisWall.axisLocation.coordinate == 0
            Direction.WEST -> thisWall.axisLocation.coordinate == 0
        }
    }

    private fun startTeleportation() {
        currentlyTeleporting = true
        WallsRuntimeState.forbidAnyLifecycleProgression = true

        thisWall.startTeleportation(
            destination = WallAxisCoordinate(
                thisWall.axisLocation.coordinate,
                when (thisWall.axisLocation.axis) {
                    HITWConst.Locations.ArenaAxis.X -> HITWConst.Locations.ArenaAxis.Z
                    HITWConst.Locations.ArenaAxis.Z -> HITWConst.Locations.ArenaAxis.X
                }
            ),
            newDirWallComesFrom = rotatedDirection,
            game = holeInTheWall,
            animationOwner = this,
            onFinishedTeleport = {
                currentlyTeleporting = false
                WallsRuntimeState.forbidAnyLifecycleProgression = false
            }
        )
    }

    fun forbidWallFromBehindToEnterCenter() {
//        WallsRuntimeState.forbidAnyLifecycleProgression = currentlyTeleporting
    }
}