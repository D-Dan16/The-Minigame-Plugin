package base.minigames.hole_in_the_wall.game_loop_handlers.state_machine

import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop_handlers.bringWallToLife
import base.minigames.hole_in_the_wall.game_loop_handlers.createNewWall
import base.minigames.hole_in_the_wall.objects.Wall
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.SpawnerRuntimeState.existingWallsList
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.SpawnerRuntimeState.stateOfWallSpawner
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.SpawnerRuntimeState.upcomingWalls
import base.utils.additions.Direction
import base.utils.additions.activateTaskAfterConditionIsMet
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random

internal object SpawnerRuntimeState {
    /** Walls that are currently alive in the game. */
    internal val existingWallsList: MutableList<Wall> = mutableListOf()
    /** The state of the wall spawner. This is used to determine what action is being done at any given moment and to ensure that nothing unexpected or unwanted occurs with behaviors to walls. */
    internal var stateOfWallSpawner: WallSpawnerState = WallSpawnerState.DO_NO_ACTION
    /** Walls that are scheduled to be spawned next. */
    internal val upcomingWalls: MutableList<Wall> = mutableListOf()

    fun reset() {
        existingWallsList.clear()
        stateOfWallSpawner = WallSpawnerState.DO_NO_ACTION
        upcomingWalls.clear()
    }
}

/** The State Machine of wall spawning. **/
internal fun HoleInTheWall.manageWallSpawning() {
    when (stateOfWallSpawner) {
        WallSpawnerState.IDLE -> {
            if (!isGameRunning) return
            if (existingWallsList.size >= base.minigames.hole_in_the_wall.HITWConst.HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS) return
            if (upcomingWalls.isNotEmpty()) return

            val wantedState = determineNextIdleState()
            attemptChangingStateTo(wantedState)
        }

        WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> {
            this.createNewWall(Direction.entries.random())

            scheduleStateTransition(
                condition = { isSafeToSpawnWall() },
                targetState = WallSpawnerState.SPAWNING_1_WALL
            )

            attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)
        }

        WallSpawnerState.SPAWNING_1_WALL -> {
            bringWallToLife(upcomingWalls[0])
            upcomingWalls.clear()

            attemptChangingStateTo(WallSpawnerState.IDLE)
        }

        WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN -> {
            // Waiting for the safety condition or a cancellation to move us onward.
        }

        WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS -> {
            if (existingWallsList.isEmpty()) {
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

fun HoleInTheWall.scheduleStateTransition(condition: () -> Boolean, targetState: WallSpawnerState) {
    activateTaskAfterConditionIsMet(
        condition = condition,
        action = { attemptChangingStateTo(targetState) },
        actionToDoIfCanceled = { attemptChangingStateTo(WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS) },
        listOfRunnablesToAddTo = runnables
    )
}

fun HoleInTheWall.determineNextIdleState(): WallSpawnerState {
    return if (existingWallsList.isEmpty()) {
        this.createNewWall(Direction.entries.random())
        WallSpawnerState.SPAWNING_1_WALL
    } else {
        WallSpawnerState.INTENDING_TO_CREATE_1_WALL
    }
}
