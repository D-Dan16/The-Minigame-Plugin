package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.models.wall.WallState

internal object SpawnerRuntimeState {
    /** The state of the wall spawner. This is used to determine what action is being done at any given moment and to ensure that nothing unexpected or unwanted occurs with behaviors to walls. */
    internal var stateOfWallSpawner: HITWConst.WallSpawnerState = HITWConst.WallSpawnerState.DO_NO_ACTION
    /** Walls that are scheduled to be spawned next. */
    internal val upcomingWalls: MutableList<Wall> = mutableListOf()

    /** Resets the spawner back to its idle baseline and clears any queued walls. */
    fun reset() {
        stateOfWallSpawner = HITWConst.WallSpawnerState.DO_NO_ACTION
        upcomingWalls.forEach {
            it.markDeleted()
        }
        upcomingWalls.clear()
    }
}