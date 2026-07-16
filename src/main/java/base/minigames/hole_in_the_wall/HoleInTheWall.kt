package base.minigames.hole_in_the_wall

import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.MinigameSkeleton
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.gameLoopRunnable
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.startOnFinalPlatformStage
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.SpawnerRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.SpawnerRuntimeState.stateOfWallSpawner
import base.minigames.hole_in_the_wall.game_loop.startRepeatingGameLoop
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType


class HoleInTheWall (val plugin: Plugin) : MinigameSkeleton() {
    override val minigameName: String = "HoleInTheWall"
    /** The map name that is being played. Gets a value in `start()`. */
    internal lateinit var mapName: String

    fun setWallSpeed(speed: Int) {
        wallSpeed = speed
    }

    override fun resetState() {
        super.resetState()
        GameLoopRuntimeState.reset()
        WallsRuntimeState.reset()
        SpawnerRuntimeState.reset()
    }

    @Throws(InterruptedException::class)
    fun start(player: Player, nameOfMap: String) {
        stateOfWallSpawner = WallSpawnerState.IDLE // Set the initial state of the wall spawner to IDLE

        this.mapName = nameOfMap
        super.start(player)

        HITWDevLogger.initialize()
        startRepeatingGameLoop()
    }

    fun startFastMode(player: Player, mapName: String) {
        wallSpeed = Timers.WALL_SPEED.last()
        GameLoopRuntimeState.wallSpeedIndex = Timers.WALL_SPEED_UP_LANDMARKS.last()
        GameLoopRuntimeState.curWallDifficultyInPack = HITWConst.WallDifficulty.VERY_HARD
        startOnFinalPlatformStage = true

        this.start(player, mapName)
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
        gameLoopRunnable?.cancel()
    }


    override fun resumeGame() {
        super.resumeGame()
        // resume the periodic task that updates the game state and handles all game events - such as wall movement, wall spawning, and wall deletion.
        startRepeatingGameLoop()
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