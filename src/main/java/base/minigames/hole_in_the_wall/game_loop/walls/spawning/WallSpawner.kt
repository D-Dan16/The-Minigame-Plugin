package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop.walls.bringWallToLife
import base.minigames.hole_in_the_wall.game_loop.walls.createNewWall
import base.minigames.hole_in_the_wall.models.Wall
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.wall_types.PsychWallType
import base.utils.additions.Direction
import base.utils.additions.activateTaskAfterConditionIsMet

internal object SpawnerRuntimeState {
    /** The state of the wall spawner. This is used to determine what action is being done at any given moment and to ensure that nothing unexpected or unwanted occurs with behaviors to walls. */
    internal var stateOfWallSpawner: WallSpawnerState = WallSpawnerState.DO_NO_ACTION
    /** Walls that are scheduled to be spawned next. */
    internal val upcomingWalls: MutableList<Wall> = mutableListOf()

    /** Resets the spawner back to its idle baseline and clears any queued walls. */
    fun reset() {
        stateOfWallSpawner = WallSpawnerState.DO_NO_ACTION
        upcomingWalls.clear()
    }
}

/** The State Machine of wall spawning. **/
/** Runs the wall spawning state machine for the current game tick. */
internal fun HoleInTheWall.manageWallSpawning() {
    when (SpawnerRuntimeState.stateOfWallSpawner) {
        WallSpawnerState.IDLE -> {
            if (!isGameRunning) return
            if (WallsRuntimeState.existingWalls.size >= HITWConst.HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS) return
            if (SpawnerRuntimeState.upcomingWalls.isNotEmpty()) return

            val wantedState: WallSpawnerState = if (WallsRuntimeState.existingWalls.isEmpty()) {
                createNewWall(Direction.entries.random())
                WallSpawnerState.SPAWNING_1_WALL
            } else {
                WallSpawnerState.INTENDING_TO_CREATE_1_WALL
            }

            attemptChangingStateTo(wantedState)
        }

        WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> {
            createNewWall(Direction.entries.random())

            scheduleStateTransition(
                condition = { isSafeToSpawnWall() },
                targetState = WallSpawnerState.SPAWNING_1_WALL
            )

            attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)
        }

        WallSpawnerState.SPAWNING_1_WALL -> {
            bringWallToLife(SpawnerRuntimeState.upcomingWalls[0])
            SpawnerRuntimeState.upcomingWalls.clear()

            attemptChangingStateTo(WallSpawnerState.IDLE)
        }

        WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN -> {
            // Waiting for the safety condition or a cancellation to move us onward.
        }

        WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS -> {
            if (WallsRuntimeState.existingWalls.isEmpty()) {
                attemptChangingStateTo(WallSpawnerState.IDLE)
            }
        }

        WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE,
        WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE -> {
            // The free-form spawner currently only spawns one wall at a time.
            attemptChangingStateTo(WallSpawnerState.IDLE)
        }

        WallSpawnerState.DO_NO_ACTION -> {}
    }
}

/** Schedules a transition to the target wall spawner state once the condition becomes true. */
fun HoleInTheWall.scheduleStateTransition(condition: () -> Boolean, targetState: WallSpawnerState) {
    activateTaskAfterConditionIsMet(
        condition = condition,
        action = { attemptChangingStateTo(targetState) },
        actionToDoIfCanceled = { attemptChangingStateTo(WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS) },
        listOfRunnablesToAddTo = runnables
    )
}
