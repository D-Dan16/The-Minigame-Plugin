package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.spawnWall
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.designWallBehaviorAndCreateIt
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.wall_types.JumpscareWall
import base.utils.additions.Direction
import base.utils.additions.activateTaskAfterConditionIsMet
import base.utils.extensions_for_classes.getNextWeighted
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random


/**
 * Manages the spawning of walls in the game by transitioning through various
 * states of the wall spawner. The spawning logic ensures adherence to rules such as max wall limits,
 * spacing between walls, and randomness in wall direction and type.
 *
 * Functionality:
 * - Handles the following states of the wall spawner:
 *      - `IDLE`: Checks game state and prepares for spawning walls when necessary.
 *      - `INTENDING_TO_CREATE_1_WALL`: Plans the creation of a single wall with randomness in direction.
 *      - `SPAWNING_1_WALL`: Finalizes the process of bringing a single wall to life.
 *      - `WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN`: Pauses until it is safe to spawn a new wall.
 *      - `SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS`: Ensures the spawner transitions to idle
 *        when no walls remain.
 *      - `INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE`: Plans the creation of multiple walls
 *        with direction and type combination logic.
 *      - `SPAWNING_MULTIPLE_WALLS_AT_ONCE`: Finalizes the spawning process for multiple walls.
 *      - `DO_NO_ACTION`: No operations are performed in this state.
 *
 * Transition Mechanics:
 * - Transitions between states are driven by conditions like the number of existing walls,
 *   the state of the game, and safety checks for wall placement.
 * - Uses scheduled transition mechanisms to wait for specific states or conditions to be met.
 */
internal fun HoleInTheWall.manageWallSpawning() {
    when (SpawnerRuntimeState.stateOfWallSpawner) {
        WallSpawnerState.IDLE -> {
            if (!isGameRunning) return
            if (WallsRuntimeState.existingWalls.size >= HITWConst.WallSpawning.HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS) return
            if (SpawnerRuntimeState.upcomingWalls.isNotEmpty()) return

            val wantedState = setOf(
                WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE,
                WallSpawnerState.INTENDING_TO_CREATE_1_WALL,
            ).random()

            attemptChangingStateTo(wantedState)
        }

        WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> {
            val directionOfLast = WallsRuntimeState.existingWalls.allWalls().lastOrNull()?.directionWallComesFrom
            val chosenDir = directionOfLast?.let { lastDirection ->
                Random.getNextWeighted(
                    Direction.entries.associateWith { direction ->
                        if (direction == lastDirection) 6 else 1
                    }
                )
            } ?: Direction.entries.random()

            designWallBehaviorAndCreateIt(mutableListOf(chosenDir), WallSpawnerState.INTENDING_TO_CREATE_1_WALL)

            scheduleStateTransition(
                condition = ::isSafeToSpawnWall,
                targetState = WallSpawnerState.SPAWNING_1_WALL,
                onSafetyConfirmed = ::startJumpscareWarnings
            )

            attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)
        }

        WallSpawnerState.SPAWNING_1_WALL -> {
            SpawnerRuntimeState.upcomingWalls.firstOrNull()?.let(::spawnWall)
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

        WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE -> {
            val directionsToChooseFrom = Direction.entries.shuffled().toMutableList()

            designWallBehaviorAndCreateIt(directionsToChooseFrom, WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE)

            scheduleStateTransition(
                condition = ::isSafeToSpawnWall,
                targetState = WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE,
                onSafetyConfirmed = ::startJumpscareWarnings
            )

            attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)

        }
        WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE -> {
            SpawnerRuntimeState.upcomingWalls.toList().forEach {
                spawnWall(it)
            }
            SpawnerRuntimeState.upcomingWalls.clear()

            attemptChangingStateTo(WallSpawnerState.IDLE)
        }

        WallSpawnerState.DO_NO_ACTION -> {}
    }
}


/** Schedules a transition to the target wall spawner state once the condition becomes true. */
fun HoleInTheWall.scheduleStateTransition(
    condition: () -> Boolean,
    targetState: WallSpawnerState,
    onSafetyConfirmed: () -> Long = { 0L },
) {
    activateTaskAfterConditionIsMet(
        condition = condition,
        action = {
            val warningDuration = onSafetyConfirmed()
            if (warningDuration == 0L) {
                attemptChangingStateTo(targetState)
            } else {
                scheduleDelayedStateTransition(targetState, warningDuration)
            }
        },
        actionToDoIfCanceled = { attemptChangingStateTo(WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS) },
        listOfRunnablesToAddTo = runnables
    )
}

/** Starts all queued Jumpscare wall type warnings and returns the longest required warning duration. */
private fun startJumpscareWarnings(): Long {
    val jumpscareWalls = SpawnerRuntimeState.upcomingWalls.mapNotNull { it.getWallType<JumpscareWall>() }
    jumpscareWalls.forEach(JumpscareWall::beginSpawnWarning)
    return jumpscareWalls.maxOfOrNull(JumpscareWall::warningDurationTicks) ?: 0L
}

/** Keeps the post-warning transition cancellable when the game ends. */
private fun HoleInTheWall.scheduleDelayedStateTransition(targetState: WallSpawnerState, delayTicks: Long) {
    lateinit var task: BukkitRunnable
    task = object : BukkitRunnable() {
        override fun run() {
            runnables.remove(task)
            if (isGameRunning && !isGamePaused) {
                attemptChangingStateTo(targetState)
            }
        }
    }

    runnables += task
    task.runTaskLater(plugin, delayTicks)
}
