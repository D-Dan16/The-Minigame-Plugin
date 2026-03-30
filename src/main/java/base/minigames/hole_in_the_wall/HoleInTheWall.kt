package base.minigames.hole_in_the_wall

import com.sk89q.worldedit.regions.Region
import base.utils.other.BuildLoader
import base.utils.additions.Direction
import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerMode
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.MinigameSkeleton
import base.utils.additions.activateTaskAfterConditionIsMet
import base.utils.extensions_for_classes.getNextWeighted
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.*
import kotlin.random.Random


@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class HoleInTheWall (val plugin: Plugin) : MinigameSkeleton() {
    override val minigameName: String = "HoleInTheWall"
//region vars

    private lateinit var selectedMapBaseFile: File
    private lateinit var platformSchematics: Array<File> //the platform stages for a given map
    private lateinit var wallPackSchematics: Array<File> //the wallpack selected from a given map. each element features a group of files of walls, whose grouped via difficulty.
    private lateinit var mapSchematic: File //the map schematic that is being played.
    private lateinit var mapSchematicRegion : Region //the region of the map schematic that is being played. used to nuke the area gracefully.
    private lateinit var mapName: String //the map name that is being played. gets a value on the start() method.


    //region ----Game Modifiers that change as the game progresses
    private var timeLeft: Double = Timers.GAME_DURATION.toDouble()
    private var timeElapsed: Double = 0.0 //in seconds
    var wallSpeed: Int = Timers.WALL_SPEED[0] //in ticks
        set(value) {
            if (value !in Timers.WALL_SPEED.last() .. Timers.WALL_SPEED.first()) {
                Bukkit.getServer().broadcast(Component.text("Wall speed must be between ${Timers.WALL_SPEED[0]} and ${Timers.WALL_SPEED.last()} ticks").color(NamedTextColor.RED))
                return
            }

            field = value

            val title = Title.title(
                Component.empty(),
                Component.text("Wall speed set to $value ticks").color(NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(300))
            )
            Bukkit.getOnlinePlayers().forEach { player -> player.showTitle(title)  }

            Bukkit.getServer().broadcast(Component.text("Wall speed set to $value ticks").color(NamedTextColor.AQUA))
        }

    private val wallSpeedUpLandmarks: IntArray = Timers.WALL_SPEED_UP_LANDMARKS //in seconds
    private var wallSpeedIndex = 0 //index of the wall speed in the array

    //the current wall difficulty in the pack. starts from EASY and increases as the game progresses.
    // note that previous wall difficulties are also used in the game, but less frequently.
    private var curWallDifficultyInPack = HITWConst.WallDifficulty.EASY
    private val increaseWallDifficultyLandmarks: IntArray = Timers.INCREASE_WALL_DIFFICULTY_LANDMARKS //in seconds


    // A list of walls that are currently alive in the game. This is used to keep track of walls that are currently in play.
    // This list is updated as walls are spawned and deleted, and is tackled in the periodic() method.
    val existingWallsList: MutableList<Wall> = mutableListOf()

    private val wallsToDelete: MutableList<Wall> = mutableListOf() // A list of walls that are to be deleted. This is used to delete walls that are no longer alive

    private var stateOfWallSpawner: WallSpawnerState = WallSpawnerState.DO_NO_ACTION // The state of the wall spawner. This is used to determine what action is being done at any given moment and to ensure that nothing unexpected or unwanted occurs with behaviors to walls.

    // The current mode of spawning walls logic. A mode dictates what possible WallSpawnerStates can be done in the state machine at a given moment.
    // The moment swaps naturally every so often to increase replayability.
    private var wallSpawningMode: WallSpawnerMode? = null

    // a tracker for how many *real* walls have been spawned in a row. used for control flow - so one direction will be chosen for a healthy number of times.
    var amountOfSpawnsSinceDirectionChange: MutableMap<WallSpawnerMode, Int> = mutableMapOf(
        WallSpawnerMode.WALL_CHAINER to 0,
        WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS to 0
    )

    //endregion -----------------------------------------------------------------------------------

    var tickCount: Int = 0 // Used to keep track of the number of ticks that have passed since the game started

    //the periodic task that runs every second to update the game state
    //Update every second the time left and the time elapsed, and keep track if certain events should trigger based on the time that has elapsed.
    private var gameEvents: BukkitRunnable? = null
    // A runnable that is used to change the wall spawning mode every so often when the mode is set to Alternating.
    private var alternatingWallSpawnerModeRunnable: BukkitRunnable? = null
    private var currentAvailableListOfModesToAlternateTo: MutableList<WallSpawnerMode> = mutableListOf() // A list of modes that the wall spawner can alternate to when the mode is set to Alternating. When a mode is set, it will be taken out of the list, and when the list is empty, it will be refilled with all the modes that are available to play.



    //endregion

    @Throws(InterruptedException::class)
    fun start(player: Player, mapName: String, wantedWallSpawnerMode: String? = null) {
        // if the player has specified a wanted game mode *in the /start command*, we will use it, otherwise, we will check if the player has specified a wall spawning mode via using the /set command.
        if (wantedWallSpawnerMode != null) {
            changeWallSpawningMode(wantedWallSpawnerMode)
        // if the player has not specified via /set a mode, we will check if the mode alternator is built, and if not, we will force the mode to be Alternating.
        } else if (wallSpawningMode == null && alternatingWallSpawnerModeRunnable == null) {
            player.sendMessage(Component.text("Wall Spawning Mode is not set! selecting Alternating").color(NamedTextColor.RED))
            changeWallSpawningMode("Alternating")
        }

        stateOfWallSpawner = WallSpawnerState.IDLE // Set the initial state of the wall spawner to IDLE

        this.mapName = mapName
        super.start(player)

        startRepeatingGameLoop()
    }



    fun startFastMode(player: Player, mapName: String, wallSpawningMode: String? = null) {
        wallSpeed = Timers.WALL_SPEED.last() // Set the wall speed to the maximum speed
        this.start(player, mapName, wallSpawningMode)
    }

    // This func can be called whether the game is alive or not. (for the command that uses it)
    fun changeWallSpawningMode(mode: String) {
        fun changeMode(mode: WallSpawnerMode) {
            wallSpawningMode = mode

            // cancel all the runnables that are in charge of changing the state of the wall spawner, and then clear the list of runnables after canceling them
            //we do .toList() to avoid ConcurrentModificationException
            runnables.toList().forEach { it.cancel() }
            runnables.clear()

            // Clear the list of walls that were planned to be spawned in the game, since otherwise, when we will spawn in walls, the old walls will spawn along with the new ones. (which will deff make walls collide with each other)
            upcomingWalls.clear()

            // clear the trackers of the number of spawns since direction change
            for (wallSpawnerMode in amountOfSpawnsSinceDirectionChange.entries) {
                wallSpawnerMode.setValue(0)
            }

            // send a message to all players that the mode has been changed
            val title = Title.title(
                Component.empty(),
                Component.text("Wall Spawner Mode: ${mode.name.lowercase().replace('_',' ')}").color(NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(300))
            )
            Bukkit.getOnlinePlayers().forEach { player -> player.showTitle(title)  }

            Bukkit.getServer().broadcast(Component.text("Wall Spawner Mode: ${mode.name.lowercase().replace('_',' ')}").color(NamedTextColor.AQUA))
        }

        // Cancel the previous runnable if it exists. this is so that we don't have this running in the background when we change the mode to a set mode.
        try {
            alternatingWallSpawnerModeRunnable?.cancel()
        } catch (_: Exception) {
            //nothing to do here, we just want to make sure that the runnable is cancelled if it was scheduled
        }
        alternatingWallSpawnerModeRunnable = null



        WallSpawnerMode.entries.forEach {
            if (mode.uppercase() == it.name) {
                changeMode(it)
                return
            }
        }

        if (mode == "Alternating") {
            alternatingWallSpawnerModeRunnable = object : BukkitRunnable() {
                override fun run() {
                    if (isGamePaused) return

                    // refill the list of modes to alternate to with all the modes that are available to play
                    if (currentAvailableListOfModesToAlternateTo.isEmpty()) {
                        currentAvailableListOfModesToAlternateTo = WallSpawnerMode.entries.shuffled().toMutableList()
                    }

                    // take the first mode from the list of available modes to alternate to and change the mode of the wall spawner to it.
                    changeMode(currentAvailableListOfModesToAlternateTo.removeFirst())
                }
            }

            // only start alternating wall spawner mode when the game is running
            activateTaskAfterConditionIsMet(
                condition = { isGameRunning && !isGamePaused },
                action = {alternatingWallSpawnerModeRunnable?.runTaskTimer(plugin,0L,Timers.ALTERNATING_WALL_SPAWNER_MODES_DELAY)}
            )


            val title = Title.title(
                Component.empty(),
                Component.text("Wall Spawner Mode: Alternating").color(NamedTextColor.AQUA),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(300))
            )
            Bukkit.getOnlinePlayers().forEach { player -> player.showTitle(title)  }

            return
        }

        // if we got here, it means that the sender hasn't sent a proper mode to play
        Bukkit.getServer().broadcast(Component.text("the wallSpawnerMode provided is not valid. not starting the game").color(NamedTextColor.DARK_AQUA))
        throw IllegalArgumentException("HITW: mode provided to play as is illegal")
    }

    override fun endGame() {
        super.endGame()

        // -------------------------- INITIALIZATION --------------------------

        gameEvents?.cancel()
        gameEvents = null

        alternatingWallSpawnerModeRunnable?.cancel()
        alternatingWallSpawnerModeRunnable = null

        stateOfWallSpawner = WallSpawnerState.DO_NO_ACTION // Reset the state of the wall spawner
        wallSpawningMode = null // Reset the wall spawning mode
        currentAvailableListOfModesToAlternateTo.clear() // Clear the list of modes that are available to alternate to

        // Clear the list of alive walls
        existingWallsList.clear()
        // Clear the walls that were planned to be pasted into existence
        upcomingWalls.clear()

        atTheProcessOfConsideringSwappingRealWallDirection = false // Reset the flag that is used to prevent multiple direction changes in a row
        amountOfSpawnsSinceSwitchedTheRealDirection = 0


        for (entry in amountOfSpawnsSinceDirectionChange.entries) {
            entry.setValue(0)
        }

        tickCount = 0 // Reset the tick count

        // -------------------------- END INITIALIZATION --------------------------

        // Clear the area around the spawn point
        nukeArea()
    }

    fun nukeArea() {
        BuildLoader.deleteSchematic(mapSchematicRegion)
    }

    override fun pauseGame() {
        super.pauseGame()

        // Cancel the periodic task that updates the game state and handles all game events - such as wall movement, wall spawning, and wall deletion.
        gameEvents?.cancel()

        try {
            alternatingWallSpawnerModeRunnable?.cancel()
        } catch (_: Exception) {
            // nothing to do here, we just want to make sure that the runnable is cancelled if it was scheduled
        }
    }

    override fun resumeGame() {
        super.resumeGame()
        // resume the periodic task that updates the game state and handles all game events - such as wall movement, wall spawning, and wall deletion.
        startRepeatingGameLoop()

        // Also, we will resume the alternating wall spawner mode runnable if it was running before
        if (alternatingWallSpawnerModeRunnable != null && !alternatingWallSpawnerModeRunnable!!.isCancelled) {
            alternatingWallSpawnerModeRunnable!!.runTaskTimer(plugin, 0L, Timers.ALTERNATING_WALL_SPAWNER_MODES_DELAY)
        }
    }

    private fun startRepeatingGameLoop() {
        if (!this.isGameRunning || isGamePaused) {
            logger().warn("HITW: Game is not running, cannot start periodic task")
            return
        }

        gameEvents = object : BukkitRunnable() {
            fun handlePsychWallsThatRanOutOfLifespan(wall: Wall) {
                // If the wall is a psych wall, we will keep it existing for a lil, then later decide if it should be removed or not.
                Bukkit.getScheduler().runTaskLater(MinigamePlugin.plugin, Runnable {
                    // If the wall is chosen to be removed, we'll remove it, otherwise, we will resume its movement after a delay.
                    if (wall.shouldRemovePsychThatStopped) {
                        wall.shouldBeRemoved = true
                    } else {
                        activateTaskAfterConditionIsMet(
                            condition = {getAliveMovingWalls().isEmpty()} ,
                            action = {
                                wall.shouldBeStopped = false
                                wall.lifespanRemaining = HITWConst.PSYCH_WALL_THAT_RETURNS_TO_MOVING_LIFESPAN // Reset the lifespan of the wall to a lifespan that is enough for it to reach the same distance as a regular wall.

                                // get rid of the identity of the wall - since psych walls should only stop themselves once, and we don't want for them to stop later on when the lifespan is 0 again
                                wall.isPsych = false

                                wall.isBeingHandled = false
                            }
                        )
                    }

                }, Timers.STOPPED_WALL_DELAY_BEFORE_ACTION_DEALT.random())
            }
            override fun run() {
                tickCount++
                timeLeft-= 1/20
                timeElapsed+= 1/20
                if (timeLeft <= 0) {
                    endGame()
                }

                //region ---Check if the wall speed should be increased
                if (wallSpeedIndex < wallSpeedUpLandmarks.size && timeElapsed >= wallSpeedUpLandmarks[wallSpeedIndex]) {
                    wallSpeed = Timers.WALL_SPEED[++wallSpeedIndex]
                }
                //endregion

                //region ---Check if the wall difficulty should be increased
                //TODO: implement logic
                if (curWallDifficultyInPack != HITWConst.WallDifficulty.VERY_HARD && timeElapsed >= increaseWallDifficultyLandmarks[curWallDifficultyInPack]) {
                    when (++curWallDifficultyInPack) {
                        HITWConst.WallDifficulty.MEDIUM -> {}
                        HITWConst.WallDifficulty.HARD -> {}
                        HITWConst.WallDifficulty.VERY_HARD -> {}
                    }
                }
                //endregion


                //region --Check if the walls should be moved and handle if they should be stopped/deleted/resumed

                //If the time elapsed is a multiple of the wall speed (which resembles how often the walls should be moved at in ticks), then move the walls
                if (tickCount % wallSpeed == 0) {
                    // Get the walls that have a lifespan that's greater than 0
                    for (wall in getAliveMovingWalls()) {
                        // Move the wall if its lifespan is greater than 0, and it should not be stopped
                        wall.move()
                    }
                    for (wall in getWallsThatAreStopped()) {

                        when {
                            //TODO: currently, regular walls are removed immediately, but we can make it so that they can be stopped instead of removed for various reasons
                            !wall.isPsych -> wall.shouldBeRemoved = true // If the wall is not a psych wall, we will remove it immediately.

                            wall.isPsych && !wall.shouldBeRemoved -> {
                                if (!wall.isBeingHandled) {
                                    wall.isBeingHandled = true
                                    handlePsychWallsThatRanOutOfLifespan(wall)
                                }
                            }
                        }

                        // If the wall is no longer alive, delete it via adding it to a new list of walls to delete
                        if (wall.shouldBeRemoved) wallsToDelete.add(wall)
                    }

                    // Delete the walls that are no longer alive
                    wallsToDelete.forEach { deleteWall(it) }
                    // Clear the list of walls to delete after deleting them so that we don't delete the same walls again
                    wallsToDelete.clear()
                }
                //endregion

                //region --Add new walls to the game

                // Limit the number of walls to HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS at a time
                if (existingWallsList.size < HITWConst.HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS) {
                    // We'll make a state machine. depending on the state of the game, we'll decide to spawn new walls with different behavior and traits.
                    manageWallSpawning()
                }
                //endregion
            }
        }
        gameEvents?.runTaskTimer(plugin, Timers.DELAY_BEFORE_STARTING_GAME, 1L)
    }

    val upcomingWalls: MutableList<Wall> = mutableListOf()// A list of walls that are upcoming to be spawned. This is used to keep track of walls that are about to be spawned in the game.

    // this is a flag that is used for
    // Mode: WALLS_FROM_2_OPPOSITE_DIRECTIONS,
    // at the state: WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE
    // purpose: to stop the wall spawner from swapping the real wall direction multiple times in a row from the method isConsideringSwappingRealWallDirection()
    var atTheProcessOfConsideringSwappingRealWallDirection: Boolean = false

    // this is a flag that is used for
    // Mode: WALLS_FROM_2_OPPOSITE_DIRECTIONS,
    // at the state: WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE
    // purpose: to gatekeep and limit changing the directions of the real wall from the duo / directions of the walls..
    var amountOfSpawnsSinceSwitchedTheRealDirection = 0

    private fun manageWallSpawning() {
        //TODO: the logic currently is very dull and incomplete
        //only works with adding 1 wall at a time
        fun isSafeToSpawnWall() : Boolean {

            val directionsExistingWallsHave: Set<Direction> = existingWallsList.map { it.directionWallComesFrom }.toSet()
            val directionOfUpcomingWall: Direction = upcomingWalls.last().directionWallComesFrom

            val numOfDirectionsExistingWallsHave = directionsExistingWallsHave.size

            return when (numOfDirectionsExistingWallsHave) {
                0 -> true
                1 -> {
                    val lastWall: Wall = existingWallsList.last()

                    when {
                        directionOfUpcomingWall in directionsExistingWallsHave ->
                            lastWall.lifespanTraveled >= lastWall.minimumLifespanTraveledWhereWallsCanSpawnBehindIt

                        directionOfUpcomingWall.getClockwise() in directionsExistingWallsHave ||
                        directionOfUpcomingWall.getCounterClockwise() in directionsExistingWallsHave ->
                            lastWall.lifespanTraveled >= HITWConst.LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_AN_ADJACENT_DIRECTION

                        directionOfUpcomingWall.getOpposite() in directionsExistingWallsHave ->
                            lastWall.lifespanTraveled >= HITWConst.LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_THE_DIRECTION_THIS_WALL_IS_FACING

                        else -> false
                    }
                }
                2,3,4 -> false
                else -> {throw Exception("numOfDirectionsExistingWallHave must be between 0 and 4") }
            }
        }
        fun attemptChangingStateTo(wantedState: WallSpawnerState) {
            val canTransition = when (stateOfWallSpawner) {
                WallSpawnerState.IDLE -> wantedState in setOf(
                            WallSpawnerState.INTENDING_TO_CREATE_1_WALL,
                            WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE,
                            WallSpawnerState.SPAWNING_1_WALL
                )

                WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> wantedState in setOf(
                            WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN,
                            WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS
                )

                WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE -> wantedState in setOf(
                            WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN,
                            WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS
                )


                WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN -> wantedState in setOf(
                            WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS, //used only when we change the mode of the wall spawner, when we cancel runnables that are sending you to a desired state..
                            WallSpawnerState.SPAWNING_1_WALL,
                            WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE
                )

                WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE -> wantedState in setOf(
                            WallSpawnerState.IDLE
                )

                WallSpawnerState.SPAWNING_1_WALL -> wantedState in setOf(
                            WallSpawnerState.IDLE
                )

                WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS -> wantedState in setOf(
                            WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS,  //THIS IS CRITICAL TO HAVE SINCE WHEN CHANGING MODES, we might try to change states to this state multiple times, from the condition that is called when the runable is cancelled which are canceled when the mode is changed
                            WallSpawnerState.IDLE
                )

                WallSpawnerState.DO_NO_ACTION -> wantedState in setOf(
                            WallSpawnerState.IDLE
                )
            }

            if (!canTransition) {
                Bukkit.getServer().broadcast(Component.text("HITW: Cannot transition from $stateOfWallSpawner to $wantedState").color(NamedTextColor.RED))
                pauseGame()
            }

            stateOfWallSpawner = wantedState

            Bukkit.getServer().broadcast(Component.text("state = $stateOfWallSpawner").color(NamedTextColor.GRAY))
            //Bukkit.getServer().broadcast(Component.text("mode = $wallSpawningMode").color(NamedTextColor.WHITE))
        }

        // The State Evaluator.
        when (stateOfWallSpawner) {
            WallSpawnerState.IDLE -> { //region IDLE
                if (!isGameRunning) return

                val wantedState = when (wallSpawningMode!!) {
                    WallSpawnerMode.WALL_CHAINER -> {
                        // if we don't have any walls in the arena, we can add one immediately, otherwise we'll decide where and when to add it via the bridger states
                        if (existingWallsList.isEmpty()) {
                            // Create a new wall with a random direction and add it to the upcoming walls list
                            createNewWall(Direction.entries.random(), false)

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

                attemptChangingStateTo(wantedState)
            } //endregion

            WallSpawnerState.SPAWNING_1_WALL -> { //region SPAWNING
                bringWallToLife(upcomingWalls[0]) // Make the wall exist in the world by loading the schematic
                upcomingWalls.clear()

                val mode = wallSpawningMode!!
                when (mode) {
                    WallSpawnerMode.WALL_CHAINER -> {
                        // increment the amount of spawn since direction change for the current mode
                        amountOfSpawnsSinceDirectionChange.let {
                            it[mode] = it[mode]!! + 1
                        }
                    }
                    else -> {}
                }


                attemptChangingStateTo(WallSpawnerState.IDLE)
            } //endregion

            WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE -> { //region SPAWNING_MULTIPLE_WALLS_AT_ONCE
                upcomingWalls.forEach { wall -> bringWallToLife(wall) }
                upcomingWalls.clear()

                // Do extra logic depending on the mode we're at
                val mode = wallSpawningMode!!
                when (mode) {
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


                attemptChangingStateTo(WallSpawnerState.IDLE)
            }//endregion

            WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> {  //region INTENDING_TO_CREATE_1_WALL
                val weightsOfDirections = mutableMapOf<Direction, Int>() // A map to hold the weights of each direction

                // gather the direction of the last wall that was spawned
                val directionOfLastWall = existingWallsList.last().directionWallComesFrom

                // Assign weights to each direction based on the mode we're at
                when (wallSpawningMode) {
                    WallSpawnerMode.WALL_CHAINER -> {
                        if (amountOfSpawnsSinceDirectionChange[WallSpawnerMode.WALL_CHAINER]!! >= HITWConst.WallSpawnerModes.WALL_CHAINER.MIN_AMOUNT_OF_SPAWNS_TILL_CHANGING_DIRECTIONS) {
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
                    }
                    else -> throw IllegalArgumentException("HITW: Invalid wall spawning mode: $wallSpawningMode to be at for this state: $stateOfWallSpawner")
                }

                // Select a direction based on the weights
                val directionOfUpcomingWall = Random.getNextWeighted(weightsOfDirections)

                createNewWall(directionOfUpcomingWall, false) // Create a new wall with the selected direction and add it to the upcoming walls list

                activateTaskAfterConditionIsMet(
                    condition = {isSafeToSpawnWall()},
                    action =  {attemptChangingStateTo(WallSpawnerState.SPAWNING_1_WALL)},
                    actionToDoIfCanceled =  {attemptChangingStateTo(WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS)},
                    listOfRunnablesToAddTo = runnables
                )

                attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)
            } //endregion

            WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE -> { //region INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE
                // Default condition to swap state will be set later based on the mode
                var conditionToSwapState: () -> Boolean = { true }

                when (wallSpawningMode) {
                    WallSpawnerMode.WALLS_FROM_ALL_DIRECTIONS -> { //region WALLS_FROM_ALL_DIRECTIONS
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
                                (0..100).random() <= HITWConst.WallSpawnerModes.WALLS_FROM_ALL_DIRECTIONS.CHANCE_THAT_PSYCH_WALL_WILL_GET_REMOVED

                            createNewWall(direction, isPsych, chosenToBeRemoved)
                        }

                        // If we are spawning walls from all directions, we will wait until there are no existing walls
                        conditionToSwapState = { existingWallsList.isEmpty() }
                    } //endregion

                    WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS -> { //region WALLS_FROM_2_OPPOSITE_DIRECTIONS
                        val const = HITWConst.WallSpawnerModes.WALLS_FROM_2_OPPOSITE_DIRECTIONS

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
                            createNewWall(direction, isPsychA)
                            createNewWall(direction.getOpposite(), isPsychB)
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
                                return
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

                            conditionToSwapState = r@{
                                // If we have decided to swap directions of the walls, we will need to wait more time compared to the case when we aren't swapping directions. So let's divide the cases so that as soon as we can spawn the wall without collision, we will spawn it.
                                if ({rndShouldSwapDirections}.invoke()) {

                                    val lastReal = getLastRealWall()
                                    // If there are no real walls, we can spawn the walls immediately
                                    if (lastReal == null) {
                                        return@r true
                                    } else {
                                        return@r lastReal.lifespanTraveled >= HITWConst.LIFESPAN_TRAVELED_OF_WALL_THAT_LETS_YOU_SPAWN_A_WALL_FROM_AN_ADJACENT_DIRECTION
                                    }
                                } else {
                                    return@r existingWallsList.last().lifespanTraveled >= const.MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION
                                }
                            }
                        }
                        // Make it so that when the lifespan of any of those walls has reached 0, they'll immediately be removed, instead of just stopping in place.
                        upcomingWalls.forEach { it -> it.shouldBeRemoved = true}

                    } //endregion

                    else -> throw IllegalArgumentException("HITW: Invalid wall spawning mode: $wallSpawningMode to be at for this state: $stateOfWallSpawner")
                }

                activateTaskAfterConditionIsMet(
                    condition =  conditionToSwapState ,
                    action =  {attemptChangingStateTo(WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE)},
                    actionToDoIfCanceled =  {attemptChangingStateTo(WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS)},
                    listOfRunnablesToAddTo = runnables
                )
                attemptChangingStateTo(WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN)

            }//endregion

            WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN -> { //region WAITING_FOR_NEXT_WALL
            } //endregion

            WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS -> {//region WAITING_TILL_THERE_ARE_NO_EXISTING_WALLS
                if (existingWallsList.isEmpty()) attemptChangingStateTo(WallSpawnerState.IDLE)
            }//endregion


            WallSpawnerState.DO_NO_ACTION -> {

            }
        }
    }

    override fun prepareArea() {
        fun getGameBaseFolder(): File {
            check(plugin is MinigamePlugin) { "Invalid plugin type" }
            val baseFolder: File = plugin.getSchematicsBaseFolder(MinigamePlugin.Companion.MinigameType.HOLE_IN_THE_WALL)
            Objects.requireNonNull(baseFolder, "Game base folder not found")
            return baseFolder
        }

        fun loadMapSchematics(baseFolder: File) {
            // Check if the base folder exists and is a directory. If not, throw an exception. Otherwise, proceed to find the map schematics.
            val files: Array<File> = baseFolder.listFiles() ?: throw IOException("No files found in base folder named ${baseFolder.name}")

            selectedMapBaseFile = Arrays.stream(files)
                .filter { file: File -> file.isDirectory() && file.getName() == mapName }
                .findFirst()
                .orElse(null)
        }

        fun processMapComponents() {
            val mapComponents: Array<File> = selectedMapBaseFile.listFiles()
            for (component in mapComponents) {
                when (component.getName()) {
                    HITWConst.PLATFORMS_FOLDER -> {
                        platformSchematics = component.listFiles() ?: throw IOException("No platform schematics found in ${component.name}")
                    }
                    HITWConst.WALLPACK_FOLDER -> {
                        wallPackSchematics = component.listFiles() ?: throw IOException("No wall pack schematics found in ${component.name}")
                    }
                    HITWConst.MAP_FOLDER -> {
                        mapSchematic = component.listFiles()?.firstOrNull()
                            ?: throw IOException("No map schematic found in ${component.name}")
                    }
                }
            }
        }

        try {
            val baseFolder = getGameBaseFolder()
            loadMapSchematics(baseFolder)
            processMapComponents()
        } catch (e: IOException) {
            logger().error("HITW: I/O failure while preparing area", e)
            endGame()
        } catch (e: IllegalStateException) {
            logger().error("HITW: Invalid state during map preparation", e)
            endGame()
        } catch (e: Exception) {
            logger().error("HITW: Unexpected error during game setup", e)
            endGame()
        }

        // Load the map schematic (the deco arena) and store the region of the map
        mapSchematicRegion = BuildLoader.loadSchematicByFile(mapSchematic, HITWConst.Locations.CENTER_OF_MAP)
        // Load the platform schematic (the platform that players will stand on)
        BuildLoader.loadSchematicByFile(platformSchematics[2], HITWConst.Locations.PLATFORM)
    }

    override fun prepareGameSetting() {
        fun preparePlayer(player: Player) {
            player.gameMode = if (HITWConst.isInDevelopment) GameMode.CREATIVE else GameMode.ADVENTURE

            if (!HITWConst.isInDevelopment) {
                player.teleport(HITWConst.Locations.SPAWN)
            }

            //give the player infinite jump boost 2.
            player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, -1, 1, false))
        }

        super.prepareGameSetting()

        for (player in players) {
            preparePlayer(player)
        }
    }

    fun deleteWall(wall: Wall) {
        BuildLoader.deleteSchematic(wall.wallRegion.minimumPoint, wall.wallRegion.maximumPoint)
        // delete the wall reference from the AliveWallsList
        val hasWallBeenDeleted = existingWallsList.remove(wall)

        if (!hasWallBeenDeleted) {
            logger().warn("HITW: Wall deletion failed, wall not found in the alive walls list")
        }
    }

    //DO MODIFY THIS FOR DEBUGGING PURPOSES
    fun createNewWall() {
        val wallFile = wallPackSchematics.random() // Randomly select a wall from the wall pack
        val direction = Direction.entries.toTypedArray().random() // Randomly select a direction for the wall to come from
        val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped
        val newWall = Wall(wallFile, direction,shouldBeFlipped) // Create a new wall

        bringWallToLife(newWall) // Make the wall exist in the world by loading the schematic


        newWall.showBlocks() // Show the corners of the wall for debugging purposes
        Bukkit.getServer().broadcast(Component.text("flipped: ${newWall.isFlipped}. DirectionWallCome: ${newWall.directionWallComesFrom}").color(
            NamedTextColor.DARK_AQUA))
    }



    // DO NOT MODIFY THIS FOR DEBUGGING PURPOSES

    fun createNewWall(direction: Direction,isPsych: Boolean, shouldPsychDieWhenStopped: Boolean = true) {
        val wallFile = wallPackSchematics.random() // Randomly select a wall from the wall pack
        val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped


        val newWall = Wall(wallFile, direction, shouldBeFlipped,isPsych,shouldPsychDieWhenStopped) // Create a new wall

        upcomingWalls.add(newWall) // Add the new wall to the list of upcoming walls
    }

    fun bringWallToLife(wall: Wall) {
        // Make the wall exist in the world by loading the schematic
        wall.makeWallExist()
        // Add the new wall to the list of existing walls. the wall is added at the end of the list!
        existingWallsList.add(wall)
    }

    fun clearWalls() {
        while (existingWallsList.isNotEmpty()) {
            deleteWall(existingWallsList[0])
        }
    }

    fun getAliveMovingWalls(): List<Wall> {
        return existingWallsList.filter { !it.shouldBeStopped } // Return only the walls that are currently moving
    }
    fun getWallsThatAreStopped(): List<Wall> {
        return existingWallsList.filter { it.shouldBeStopped } // Return only the walls that are currently stopped
    }

    fun getLastRealWall(): Wall? {
        return existingWallsList.lastOrNull { !it.isPsych } // Return the last non-psych wall
    }

}

