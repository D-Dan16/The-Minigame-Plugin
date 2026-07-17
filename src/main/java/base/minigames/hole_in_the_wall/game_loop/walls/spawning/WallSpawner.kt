package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.bringWallToLife
import base.minigames.hole_in_the_wall.game_loop.walls.createNewWall
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.wall_types.PsychWallType
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.utils.additions.Direction
import base.utils.additions.activateTaskAfterConditionIsMet
import kotlin.random.Random

/** The State Machine of wall spawning. **/
/** Runs the wall-spawning state machine for the current game tick. */
internal fun HoleInTheWall.manageWallSpawning() {
    when (SpawnerRuntimeState.stateOfWallSpawner) {
        WallSpawnerState.IDLE -> {
            if (!isGameRunning) return
            if (WallsRuntimeState.existingWalls.size >= HITWConst.Walls.HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS) return
            if (SpawnerRuntimeState.upcomingWalls.isNotEmpty()) return

            val wantedState: WallSpawnerState =
                setOf(WallSpawnerState.INTENDING_TO_CREATE_1_WALL, WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE).random()

            attemptChangingStateTo(wantedState)
        }

        WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> {
            val randomDir = Direction.entries.random()
            val directionOfLast = WallsRuntimeState.existingWalls.allWalls().lastOrNull()?.directionWallComesFrom

            if (directionOfLast != null)
                createNewWall(setOf(directionOfLast,randomDir).random())
            else
                createNewWall(randomDir)

            scheduleStateTransition(
                condition = ::isSafeToSpawnWall,
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

        WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE -> {
            val wallsToSpawn = GameLoopRuntimeState.multiWallSelectionRange.random()
            val directionToChooseFrom = Direction.entries.shuffled().toMutableList()

            var createdRealWall = false
            repeat(wallsToSpawn) {
                val wallTypes = mutableListOf<WallType>()
                if (!createdRealWall) {
                    createdRealWall = true
                } else {
                    wallTypes += PsychWallType(Random.nextBoolean())
                }

                createNewWall(directionToChooseFrom.removeFirst(),wallTypes)
            }

            scheduleStateTransition(
                condition = ::isSafeToSpawnWall,
                targetState = WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE
            )

            attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)

        }
        WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE -> {
            SpawnerRuntimeState.upcomingWalls.forEach {
                bringWallToLife(it)
            }
            SpawnerRuntimeState.upcomingWalls.clear()

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
