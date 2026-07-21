package base.minigames.hole_in_the_wall

import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.MinigameSkeleton
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.GameLoopProgressionProfile
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.gameLoopRunnable
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.SpawnerRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.SpawnerRuntimeState.stateOfWallSpawner
import base.minigames.hole_in_the_wall.game_loop.startRepeatingGameLoop
import base.resources.Colors
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
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
        HITWDevLogger.log("~~~~~~~~~~~~~~~~~~~ New Game ~~~~~~~~~~~~~~~~~~~")
        startRepeatingGameLoop()
    }

    fun startFastMode(player: Player, mapName: String) {
        GameLoopRuntimeState.applyProgression(GameLoopProgressionProfile.FINAL)
        this.start(player, mapName)
        announceHardModeWallTypes()
    }

    /** Announces every wall type selected for this hard-mode game. */
    private fun announceHardModeWallTypes() {
        val selectedWallTypes = GameLoopRuntimeState.availableWallTypes.toList()
        val wallTypeNames = selectedWallTypes.joinToString { it.displayName }

        announceMessage(
            content = "Hard Mode Wall Types",
            subContent = wallTypeNames,
            color = Colors.TitleColors.ORANGE,
            duration = 3000L,
        )
        players.forEach { player ->
            selectedWallTypes.forEach { wallType ->
                player.sendMessage(
                    Component.text(
                        "${wallType.displayName}\n--> ${wallType.description}",
                        TextColor.fromHexString(Colors.TitleColors.ORANGE),
                    )
                )
            }
        }
    }

    override fun endGame() {
        HITWDevLogger.log("~~~~~~~~~~~~~~~~~~~ Game Over ~~~~~~~~~~~~~~~~~~~")
        super.endGame()
        nukeArena()
    }

    override fun nukeArena() {
        deleteArena()
    }

    override fun pauseGame() {
        HITWDevLogger.log("~~~~~~~~~~~~~~~~~~~ Game Paused ~~~~~~~~~~~~~~~~~~~")
        super.pauseGame()

        // Cancel the periodic task that updates the game state and handles all game events - such as wall movement, wall spawning, and wall deletion.
        gameLoopRunnable?.cancel()
    }


    override fun resumeGame() {
        HITWDevLogger.log("~~~~~~~~~~~~~~~~~~~ Game Resumed ~~~~~~~~~~~~~~~~~~~")
        super.resumeGame()
        // resume the periodic task that updates the game state and handles all game events - such as wall movement, wall spawning, and wall deletion.
        startRepeatingGameLoop()
    }

    override fun prepareArea() {
        arenaPreparer()
    }

    override fun prepareGameSetting() {
        fun preparePlayer(player: Player) {
            player.gameMode = if (HITWConst.Development.IS_IN_DEVELOPMENT) GameMode.CREATIVE else GameMode.ADVENTURE

            if (!HITWConst.Development.IS_IN_DEVELOPMENT) {
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
