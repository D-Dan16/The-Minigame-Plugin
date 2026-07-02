package base.minigames.hole_in_the_wall.game_loop_handlers.state_machine

import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerMode
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.objects.Wall
import base.utils.additions.Direction
import base.utils.additions.activateTaskAfterConditionIsMet
import base.utils.extensions_for_classes.getNextWeighted
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.scheduler.BukkitRunnable
import kotlin.collections.set
import kotlin.random.Random

//<editor-fold desc="Global State Variables">
// A list of walls that are currently alive in the game. This is used to keep track of walls that are currently in play.
// This list is updated as walls are spawned and deleted, and is tackled in the periodic() method.
internal val existingWallsList: MutableList<Wall> = mutableListOf()

internal var stateOfWallSpawner: WallSpawnerState = WallSpawnerState.DO_NO_ACTION // The state of the wall spawner. This is used to determine what action is being done at any given moment and to ensure that nothing unexpected or unwanted occurs with behaviors to walls.

// The current mode of spawning walls logic. A mode dictates what possible WallSpawnerStates can be done in the state machine at a given moment.
// The moment swaps naturally every so often to increase replayability.
internal var wallSpawningMode: WallSpawnerMode? = null

// a tracker for how many *real* walls have been spawned in a row. used for control flow - so one direction will be chosen for a healthy number of times.
internal var amountOfSpawnsSinceDirectionChange: MutableMap<WallSpawnerMode, Int> = mutableMapOf(
    WallSpawnerMode.WALL_CHAINER to 0,
    WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS to 0
)

// A runnable that is used to change the wall spawning mode every so often when the mode is set to Alternating.
internal var alternatingWallSpawnerModeRunnable: BukkitRunnable? = null

internal var currentAvailableListOfModesToAlternateTo: MutableList<WallSpawnerMode> = mutableListOf() // A list of modes that the wall spawner can alternate to when the mode is set to Alternating. When a mode is set, it will be taken out of the list, and when the list is empty, it will be refilled with all the modes that are available to play.


val upcomingWalls: MutableList<Wall> = mutableListOf()// A list of walls that are upcoming to be spawned. This is used to keep track of walls that are about to be spawned in the game.

// this is a flag used for
// Mode: WALLS_FROM_2_OPPOSITE_DIRECTIONS,
// at the state: WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE
// purpose: to stop the wall spawner from swapping the real wall direction multiple times in a row from the method isConsideringSwappingRealWallDirection()
var atTheProcessOfConsideringSwappingRealWallDirection: Boolean = false

// this is a flag used for
// Mode: WALLS_FROM_2_OPPOSITE_DIRECTIONS,
// at the state: WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE
// purpose: to gatekeep and limit changing the directions of the real wall from the duo / directions of the walls..
var amountOfSpawnsSinceSwitchedTheRealDirection = 0
//</editor-fold>

/** The State Machine of wall spawning. **/
internal fun HoleInTheWall.manageWallSpawning() {
    when (stateOfWallSpawner) {
        WallSpawnerState.IDLE -> { //region IDLE
            if (!isGameRunning) return

            val wantedState = determineNextIdleState()

            attemptChangingStateTo(wantedState)
        } //endregion

        WallSpawnerState.SPAWNING_1_WALL -> { //region SPAWNING
            _root_ide_package_.base.minigames.hole_in_the_wall.game_loop_handlers.bringWallToLife(upcomingWalls[0]) // Make the wall exist in the world by loading the schematic
            upcomingWalls.clear()

            handleSpawnSingleWallCompletion()

            attemptChangingStateTo(WallSpawnerState.IDLE)
        } //endregion

        WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE -> { //region SPAWNING_MULTIPLE_WALLS_AT_ONCE
            upcomingWalls.forEach { wall ->
                _root_ide_package_.base.minigames.hole_in_the_wall.game_loop_handlers.bringWallToLife(
                    wall
                )
            }
            upcomingWalls.clear()

            handleSpawnMultipleWallsCompletion()

            attemptChangingStateTo(WallSpawnerState.IDLE)
        }//endregion

        WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> {  //region INTENDING_TO_CREATE_1_WALL
            val weights = when (wallSpawningMode) {
                WallSpawnerMode.WALL_CHAINER -> handleWallChainerDirectionWeights()
                else -> throw IllegalArgumentException("HITW: Invalid wall spawning mode: $wallSpawningMode to be at for this state: $stateOfWallSpawner")
            }

            // Select a direction based on the weights
            val directionOfUpcomingWall = Random.getNextWeighted(weights)

            _root_ide_package_.base.minigames.hole_in_the_wall.game_loop_handlers.createNewWall(
                directionOfUpcomingWall,
                false
            ) // Create a new wall with the selected direction and add it to the upcoming walls list

            scheduleStateTransition(
                condition = {isSafeToSpawnWall()},
                targetState = WallSpawnerState.SPAWNING_1_WALL
            )

            attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)
        } //endregion

        WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE -> { //region INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE
            // Default condition to swap state will be set later based on the mode
            val condition = when (wallSpawningMode) {
                WallSpawnerMode.WALLS_FROM_ALL_DIRECTIONS -> handleWallsFromAllDirectionsMode()
                WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS -> handleWallsFrom2OppositeDirectionsMode()
                else -> throw IllegalArgumentException("HITW: Invalid wall spawning mode: $wallSpawningMode to be at for this state: $stateOfWallSpawner")
            }

            scheduleStateTransition(
                condition = condition,
                targetState = WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE
            )
            attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)

        }//endregion

        WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN -> { //region WAITING_FOR_NEXT_WALL
        } //endregion

        WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS -> {//region WAITING_TILL_THERE_ARE_NO_EXISTING_WALLS
            if (existingWallsList.isEmpty())
                attemptChangingStateTo(WallSpawnerState.IDLE)
        }//endregion

        WallSpawnerState.DO_NO_ACTION -> {}
    }
}

fun HoleInTheWall.scheduleStateTransition(condition: () -> Boolean, targetState: WallSpawnerState) {
    activateTaskAfterConditionIsMet(
        condition = condition,
        action =  {attemptChangingStateTo(targetState)},
        actionToDoIfCanceled =  {attemptChangingStateTo(WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS)},
        listOfRunnablesToAddTo = runnables
    )
}

fun determineNextIdleState(): WallSpawnerState {
    return when (wallSpawningMode!!) {
        WallSpawnerMode.WALL_CHAINER -> {
            // if we don't have any walls in the arena, we can add one immediately, otherwise we'll decide where and when to add it via the bridger states
            if (existingWallsList.isEmpty()) {
                // Create a new wall with a random direction and add it to the upcoming walls list
                _root_ide_package_.base.minigames.hole_in_the_wall.game_loop_handlers.createNewWall(
                    Direction.entries.random(),
                    false
                )

                WallSpawnerState.SPAWNING_1_WALL
            } else {
                WallSpawnerState.INTENDING_TO_CREATE_1_WALL
            }


        }
        WallSpawnerMode.WALLS_FROM_ALL_DIRECTIONS,
        WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS -> {
            WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE
        }
        //                    WallSpawnerMode.WALLS_ARE_UNPREDICTABLE -> TODO()
        //                    WallSpawnerMode.WALLS_REVERSE -> TODO()

    }
}

fun handleSpawnSingleWallCompletion() {
    when (val mode = wallSpawningMode!!) {
        WallSpawnerMode.WALL_CHAINER -> {
            // increment the amount of spawn since direction change for the current mode
            amountOfSpawnsSinceDirectionChange.let {
                it[mode] = it[mode]!! + 1
            }
        }
        else -> {}
    }
}

fun handleSpawnMultipleWallsCompletion() {
    // Do extra logic depending on the mode we're at
    when (val mode = wallSpawningMode!!) {
        WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS -> {
            // Increment the counters that keep track of how many walls have been spawned since the vars' states were checked
            amountOfSpawnsSinceSwitchedTheRealDirection++

            // increment the number of spawns since direction change for the current mode
            amountOfSpawnsSinceDirectionChange.let {
                it[mode] = it[mode]!! + 1
            }
        }
        else -> {}
    }
}

fun handleWallChainerDirectionWeights(): Map<Direction, Int> {
    val weightsOfDirections = mutableMapOf<Direction, Int>() // A map to hold the weights of each direction

    // gather the direction of the last wall that was spawned
    val directionOfLastWall = existingWallsList.last().directionWallComesFrom

    // Assign weights to each direction based on the mode we're at
    if (amountOfSpawnsSinceDirectionChange[WallSpawnerMode.WALL_CHAINER]!! >= base.minigames.hole_in_the_wall.HITWConst.WallSpawnerModes.WallChainer.MIN_AMOUNT_OF_SPAWNS_TILL_CHANGING_DIRECTIONS) {
        // If we have spawned enough walls, we can change the direction of the wall
        weightsOfDirections[directionOfLastWall] = 3
        weightsOfDirections[directionOfLastWall.getClockwise()] = 1
        weightsOfDirections[directionOfLastWall.getOpposite()] = 1
        weightsOfDirections[directionOfLastWall.getCounterClockwise()] = 1

        // Reset the counter of spawns since direction change
        amountOfSpawnsSinceDirectionChange[WallSpawnerMode.WALL_CHAINER] = 0
    } else {
        // If we haven't spawned enough walls, we can only spawn a wall in the same direction as the last wall
        weightsOfDirections[directionOfLastWall] = 1
    }

    return weightsOfDirections
}

fun handleWallsFromAllDirectionsMode(): () -> Boolean {
    // randomly take between 2 and 4 directions from the Direction enum to add to the DirectionsOfUpcomingWalls
    val numOfWallsToSpawn = Random.nextInt(2, 4 + 1)

    val directionsToSpawn = Direction.entries.shuffled().take(numOfWallsToSpawn).toMutableList()

    // one wall from the wave must not be psych, while the rest will be psych. we'll take the first direction from the directionsOfUpcomingWalls and spawn it as a regular wall. (states that lead to this state may have shuffled the directions)
    for (direction in directionsToSpawn) {
        val isPsych =
            direction != directionsToSpawn.first() // The first wall will not be a psych wall, the rest will be

        // Randomly decide if the wall should be removed or not.
        // 66% - to get removed, 34% - to stay.
        val chosenToBeRemoved =
            (0..100).random() <= base.minigames.hole_in_the_wall.HITWConst.WallSpawnerModes.WallsFromAllDirections.CHANCE_THAT_PSYCH_WALL_WILL_GET_REMOVED

        _root_ide_package_.base.minigames.hole_in_the_wall.game_loop_handlers.createNewWall(
            direction,
            isPsych,
            chosenToBeRemoved
        )
    }

    // If we are spawning walls from all directions, we will wait until there are no existing walls
    return { existingWallsList.isEmpty() }
}

fun HoleInTheWall.handleWallsFrom2OppositeDirectionsMode(): () -> Boolean {
    val const = base.minigames.hole_in_the_wall.HITWConst.WallSpawnerModes.WallsFrom2OppositeDirections

    val rndShouldSwapDirections =
        amountOfSpawnsSinceDirectionChange[WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS]!! > const.MIN_AMOUNT_OF_SPAWNS_TILL_CHANGING_DIRECTIONS_FOR_DUO &&
                (0..100).random() < const.CHANCE_OF_CHANGING_DIRECTIONS
    val rndConsideringSwappingRealWallDirection = when {
        amountOfSpawnsSinceSwitchedTheRealDirection > const.MAX_AMOUNT_OF_SPAWNS_TILL_THERE_MUST_BE_CHANGE ->
            true
        amountOfSpawnsSinceSwitchedTheRealDirection > const.MIN_AMOUNT_OF_SPAWNS_TILL_THERE_CAN_BE_CONSIDERATION_TO_SWAP_REAL_WALL_DIRECTION ->
            (0..100).random() < const.CHANCE_OF_CONSIDERING_TO_SWAP_REAL_WALL_DIRECTION
        else -> false
    }

    fun createDuo(direction: Direction,isPsychA: Boolean, isPsychB: Boolean) {
        _root_ide_package_.base.minigames.hole_in_the_wall.game_loop_handlers.createNewWall(direction, isPsychA)
        _root_ide_package_.base.minigames.hole_in_the_wall.game_loop_handlers.createNewWall(
            direction.getOpposite(),
            isPsychB
        )
    }

    // ---------------------starting the logic of spawning walls from 2 opposite directions

    if (existingWallsList.isEmpty()) {
        Direction.entries.random().let { direction ->
            createDuo(direction, isPsychA = false, isPsychB = true)
        }
    } else {
        // Get the wall that is not a psych wall out of the walls
        val realWall: Wall = existingWallsList.lastOrNull { wall -> !wall.isPsych } ?: run {
            sender!!.sendMessage(
                Component.text("HITW: No real wall found in the existing walls list.")
            )
            return { true }
        }

        val directionOfRealWall = realWall.directionWallComesFrom

        //-------------------------------------------------------------------------------------------
        // we are going to spawn 2 walls at once from 2 opposite directions. we are gonna determine which walls should be psych and which should not.

        if (!atTheProcessOfConsideringSwappingRealWallDirection) {
            createDuo(directionOfRealWall, false, true)

            atTheProcessOfConsideringSwappingRealWallDirection = rndConsideringSwappingRealWallDirection
        } else {
            if (realWall.lifespanRemaining >= 10) {
                createDuo(directionOfRealWall, true, true)
            } else {
                atTheProcessOfConsideringSwappingRealWallDirection = false

                listOf(
                    { createDuo(directionOfRealWall, false, true) },
                    { createDuo(directionOfRealWall, true, false) }
                ).random().invoke()

                activateTaskAfterConditionIsMet(
                    condition = { upcomingWalls.isEmpty() },
                    action = {
                        amountOfSpawnsSinceSwitchedTheRealDirection = 0
                    },
                    listOfRunnablesToAddTo = runnables
                )
            }
        }


        // logic for swapping the directions of the walls if we randomly decided to do so
        if (rndShouldSwapDirections) {
            if (upcomingWalls.size != 2) {
                sender!!.sendMessage(
                    Component.text("HITW: for mode WALLS_FROM_2_OPPOSITE_DIRECTIONS, we must have exactly 2 walls in the upcoming walls list, but we have ${upcomingWalls.size} walls.").color(NamedTextColor.YELLOW)
                )
            }
            for (wall in upcomingWalls) {
                wall.directionWallComesFrom = wall.directionWallComesFrom.getClockwise()
            }


            // Reset the counter so that we don't swap the directions of the walls too often. we will reset it only when we know for sure that the walls that are planned to be spawned have been spawned.
            activateTaskAfterConditionIsMet(
                condition = {upcomingWalls.isEmpty()},
                action = { amountOfSpawnsSinceDirectionChange[WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS] = 0},
                listOfRunnablesToAddTo = runnables
            )
        }

        //---------------------------------------------------------------------------------------------

        return r@{
            // If we have decided to swap directions of the walls, we will need to wait more time compared to the case when we aren't swapping directions. So let's divide the cases so that as soon as we can spawn the wall without collision, we will spawn it.
            if (rndShouldSwapDirections) {

                val lastReal = getLastRealWall()
                // If there are no real walls, we can spawn the walls immediately
                if (lastReal == null) {
                    return@r true
                } else {
                    return@r lastReal.lifespanTraveled >= base.minigames.hole_in_the_wall.HITWConst.LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_AN_ADJACENT_DIRECTION
                }
            } else {
                return@r existingWallsList.last().lifespanTraveled >= const.MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION
            }
        }
    }
    // Make it so that when the lifespan of those walls has reached 0, they'll immediately be removed, instead of just stopping in place.
    upcomingWalls.forEach { it.shouldBeRemoved = true}
    return { true }
}

private fun getLastRealWall(): Wall? {
    return existingWallsList.lastOrNull { !it.isPsych } // Return the last non-psych wall
}