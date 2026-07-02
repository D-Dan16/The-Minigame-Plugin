package base.minigames.hole_in_the_wall

import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerMode
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.MinigameSkeleton
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.alternatingWallSpawnerModeRunnable
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.amountOfSpawnsSinceDirectionChange
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.amountOfSpawnsSinceSwitchedTheRealDirection
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.atTheProcessOfConsideringSwappingRealWallDirection
import base.minigames.hole_in_the_wall.game_loop_handlers.curWallDifficultyInPack
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.currentAvailableListOfModesToAlternateTo
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.existingWallsList
import base.minigames.hole_in_the_wall.game_loop_handlers.gameEvents
import base.minigames.hole_in_the_wall.game_loop_handlers.startRepeatingGameLoop
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.stateOfWallSpawner
import base.minigames.hole_in_the_wall.game_loop_handlers.tickCount
import base.minigames.hole_in_the_wall.game_loop_handlers.timeElapsed
import base.minigames.hole_in_the_wall.game_loop_handlers.timeLeft
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.upcomingWalls
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.wallSpawningMode
import base.minigames.hole_in_the_wall.game_loop_handlers.wallSpeed
import base.minigames.hole_in_the_wall.game_loop_handlers.wallSpeedIndex
import base.minigames.hole_in_the_wall.game_loop_handlers.wallsToDelete
import base.utils.additions.activateTaskAfterConditionIsMet
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration


class HoleInTheWall (val plugin: Plugin) : MinigameSkeleton() {
    override val minigameName: String = "HoleInTheWall"
    internal lateinit var mapName: String //the map name that is being played. gets a value on the start() method.

    fun setWallSpeed(speed: Int) {
        wallSpeed = speed
    }

    override fun resetState() {
        super.resetState()
        timeLeft = Timers.GAME_DURATION.toDouble()
        timeElapsed = 0.0
        wallSpeed = Timers.WALL_SPEED[0]
        wallSpeedIndex = 0
        curWallDifficultyInPack = HITWConst.WallDifficulty.EASY
        existingWallsList.clear()
        wallsToDelete.clear()
        stateOfWallSpawner = WallSpawnerState.DO_NO_ACTION
        wallSpawningMode = null
        currentAvailableListOfModesToAlternateTo.clear()
        upcomingWalls.clear()
        atTheProcessOfConsideringSwappingRealWallDirection = false
        amountOfSpawnsSinceSwitchedTheRealDirection = 0
        amountOfSpawnsSinceDirectionChange = mutableMapOf(
            WallSpawnerMode.WALL_CHAINER to 0,
            WallSpawnerMode.WALLS_FROM_2_OPPOSITE_DIRECTIONS to 0
        )
        tickCount = 0

        gameEvents?.cancel()
        gameEvents = null

//        alternatingWallSpawnerModeRunnable?.cancel()
//        alternatingWallSpawnerModeRunnable = null
    }

    @Throws(InterruptedException::class)
    fun start(player: Player, nameOfMap: String, wantedWallSpawnerMode: String? = null) {
        // if the player has specified a wanted game mode *in the /start command*, we will use it, otherwise, we will check if the player has specified a wall spawning mode via using the /set command.
        if (wantedWallSpawnerMode != null) {
            changeWallSpawningMode(wantedWallSpawnerMode)
        // if the player has not specified via /set a mode, we will check if the mode alternator is built, and if not, we will force the mode to be Alternating.
        } else if (wallSpawningMode == null && alternatingWallSpawnerModeRunnable == null) {
            player.sendMessage(Component.text("Wall Spawning Mode is not set! selecting Alternating").color(NamedTextColor.RED))
            changeWallSpawningMode("Alternating")
        }

        stateOfWallSpawner = WallSpawnerState.IDLE // Set the initial state of the wall spawner to IDLE

        this.mapName = nameOfMap
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
            //nothing to do here, we just want to make sure that the runnable is canceled if it was scheduled
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
                    if (!isGameRunning || isGamePaused) return

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
                action = { alternatingWallSpawnerModeRunnable?.runTaskTimer(plugin,0L,Timers.ALTERNATING_WALL_SPAWNER_MODES_DELAY)}
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
        nukeArena()
    }

    override fun nukeArena() {
        deleteArena()
    }

    override fun pauseGame() {
        super.pauseGame()

        // Cancel the periodic task that updates the game state and handles all game events - such as wall movement, wall spawning, and wall deletion.
        gameEvents?.cancel()

        try {
            alternatingWallSpawnerModeRunnable?.cancel()
        } catch (_: Exception) {
            // nothing to do here, we just want to make sure that the runnable is canceled if it was scheduled
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

    override fun prepareArea() {
        arenaPreparer()
    }

    override fun prepareGameSetting() {
        fun preparePlayer(player: Player) {
            player.gameMode = if (HITWConst.IS_IN_DEVELOPMENT) GameMode.CREATIVE else GameMode.ADVENTURE

            if (!HITWConst.IS_IN_DEVELOPMENT) {
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
}