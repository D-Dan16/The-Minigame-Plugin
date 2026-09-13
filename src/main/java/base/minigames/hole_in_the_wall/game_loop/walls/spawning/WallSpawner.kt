package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.spawnWall
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.designWallBehaviorAndCreateIt
import base.minigames.hole_in_the_wall.wall_types.JumpscareWall
import base.utils.additions.Direction
import base.utils.additions.activateTaskAfterConditionIsMet
import base.utils.extensions_for_classes.getNextWeighted
import base.utils.extensions_for_classes.getWeightedRandom
import org.bukkit.scheduler.BukkitRunnable
import kotlin.random.Random

/**
 * Manages the spawning of walls in the game by transitioning through various
 * states of the wall spawner. The spawning logic ensures adherence to rules such as max wall limits,
 * spacing between walls, and randomness in wall direction and type.
 *
 * Transition Mechanics:
 * - Transitions between states are driven by conditions like the number of existing walls,
 *   the state of the game, and safety checks for wall placement.
 * - Uses scheduled transition mechanisms to wait for specific states or conditions to be met.
 */
internal fun HoleInTheWall.manageWallSpawning() {
    when (SpawnerRuntimeState.stateOfWallSpawner) {
        WallSpawnerState.IDLE -> {
            if (SpawnerRuntimeState.upcomingWalls.isNotEmpty()) return

            //TODO: fast swap
            val wantedState = setOf(
                WallSpawnerState.CREATE_MULTI_WALL_BATCH to 1,
//                WallSpawnerState.CREATE_MULTI_WALL_BATCH to 0,
                WallSpawnerState.CREATE_1_WALL_BATCH to 2,
            ).getWeightedRandom()

            attemptChangingStateTo(wantedState)
        }

        WallSpawnerState.CREATE_1_WALL_BATCH -> {
            val directionOfLast = WallsRuntimeState.aliveWalls.allWalls().lastOrNull()?.directionWallComesFrom
            val chosenDir = directionOfLast?.let { lastDirection ->
                Random.getNextWeighted(
                    Direction.entries.associateWith { direction ->
                        //TODO: fast swap
                        if (direction == lastDirection) 15 else 1
//                        if (direction == lastDirection) 6 else 1
//                        if (direction == lastDirection) 6 else 0
                    }
                )
            } ?: Direction.entries.random()

            designWallBehaviorAndCreateIt(mutableListOf(chosenDir), 1)

            scheduleSpawnSequence(
                batchReadyCondition = ::isBatchReadyToBeginSpawnSequence,
                physicalSpawnCondition = ::canPhysicallySpawnQueuedWalls,
                targetState = WallSpawnerState.SPAWNING_1_WALL,
                onBatchSpawnStarted = ::startQueuedSpawnWarnings
            )

            attemptChangingStateTo(WallSpawnerState.WAITING_FOR_BATCH_SPAWN)
        }

        WallSpawnerState.SPAWNING_1_WALL -> {
            SpawnerRuntimeState.upcomingWalls.firstOrNull()?.let(::spawnWall)
            SpawnerRuntimeState.upcomingWalls.clear()

            attemptChangingStateTo(WallSpawnerState.IDLE)
        }

        WallSpawnerState.WAITING_FOR_BATCH_SPAWN -> {
            // Waiting for the safety condition or a cancellation to move us onward.
        }

        WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_ALIVE_WALLS -> {
            if (WallsRuntimeState.aliveWalls.isEmpty()) {
                attemptChangingStateTo(WallSpawnerState.IDLE)
            }
        }

        WallSpawnerState.CREATE_MULTI_WALL_BATCH -> {
            val directionsToChooseFrom = Direction.entries.shuffled().toMutableList()

            designWallBehaviorAndCreateIt(directionsToChooseFrom, GameLoopRuntimeState.multiWallSelectionRange.random())

            scheduleSpawnSequence(
                batchReadyCondition = ::isBatchReadyToBeginSpawnSequence,
                physicalSpawnCondition = ::canPhysicallySpawnQueuedWalls,
                targetState = WallSpawnerState.SPAWNING_MULTI_WALL_BATCH,
                onBatchSpawnStarted = ::startQueuedSpawnWarnings
            )

            attemptChangingStateTo(WallSpawnerState.WAITING_FOR_BATCH_SPAWN)

        }
        WallSpawnerState.SPAWNING_MULTI_WALL_BATCH -> {
            SpawnerRuntimeState.upcomingWalls.toList().forEach {
                spawnWall(it)
            }
            SpawnerRuntimeState.upcomingWalls.clear()

            attemptChangingStateTo(WallSpawnerState.IDLE)
        }

        WallSpawnerState.DO_NO_ACTION -> {}
    }
}


/**
 * Starts a queued batch's spawn sequence once it is eligible, then waits until the future wall
 * regions are physically clear before transitioning to the state that pastes the schematics.
 */
fun HoleInTheWall.scheduleSpawnSequence(
    batchReadyCondition: () -> Boolean,
    physicalSpawnCondition: () -> Boolean,
    targetState: WallSpawnerState,
    onBatchSpawnStarted: () -> Long = { 0L },
) {
    activateTaskAfterConditionIsMet(
        condition = batchReadyCondition,
        action = {
            markBatchSpawnSequenceStarted()
            val warningDuration = onBatchSpawnStarted()
            schedulePhysicalSpawnTransition(targetState, warningDuration, physicalSpawnCondition)
        },
        actionToDoIfCanceled = { attemptChangingStateTo(WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_ALIVE_WALLS) },
        listOfRunnablesToAddTo = runnables
    )
}

/** Starts all queued pre-spawn warnings and returns the longest required warning duration. */
private fun startQueuedSpawnWarnings(): Long {
    val jumpscareWalls = SpawnerRuntimeState.upcomingWalls.mapNotNull { it.getWallType<JumpscareWall>() }
    jumpscareWalls.forEach(JumpscareWall::beginSpawnWarning)

    return jumpscareWalls.maxOfOrNull(JumpscareWall::warningDurationTicks) ?: 0L
}

/** Keeps the post-warning physical spawn transition cancellable when the game ends. */
private fun HoleInTheWall.schedulePhysicalSpawnTransition(
    targetState: WallSpawnerState,
    warningDurationTicks: Long,
    physicalSpawnCondition: () -> Boolean,
) {
    var remainingWarningTicks = warningDurationTicks
    lateinit var task: BukkitRunnable
    task = object : BukkitRunnable() {
        override fun run() {
            if (!isGameRunning) {
                runnables.remove(task)
                cancel()
                return
            }

            if (isGamePaused) return

            if (remainingWarningTicks > 0L) {
                remainingWarningTicks--
                return
            }

            if (physicalSpawnCondition()) {
                runnables.remove(task)
                cancel()
                attemptChangingStateTo(targetState)
            }
        }
    }

    runnables += task
    task.runTaskTimer(plugin, 0L, 1L)
}
