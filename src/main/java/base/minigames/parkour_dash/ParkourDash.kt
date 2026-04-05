package base.minigames.parkour_dash

import base.MinigamePlugin
import base.annotations.CalledByCommand
import base.annotations.Mode
import base.minigames.MinigameSkeleton
import base.utils.additions.PausableBukkitRunnable
import base.utils.other.BuildLoader
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class ParkourDash(val plugin: MinigamePlugin) : MinigameSkeleton() {
    override val minigameName: String = this::class.simpleName ?: "Unknown"
    //<editor-fold desc="Properties"> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    //<editor-fold desc="Game Modifiers">
    var difficulty: ParkourDashCommands.Modes = ParkourDashCommands.Modes.NORMAL
        @CalledByCommand(Mode.EXCLUSIVE)
        set

    var courseLength: Int = 0
    //</editor-fold>

    //<editor-fold desc="Timers">
    private var remainingTimeSeconds: Long = PDConst.Times.GAME_DURATION
    //</editor-fold>

    //</editor-fold> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    

    override fun addScoreboardElements() {
        // Add Parkour Dash specific scoreboard line: Time Remaining
        registerScoreboardLine(
            key = "timeRemaining",
            entryText = "Time Remaining: ",
            suffix = remainingTimeSeconds
        )
    }

    override fun addTimeBasedEvents() {
        // Tick down every second and update suffix. When it reaches 0, end the game.
        pausableRunnables += PausableBukkitRunnable(
            plugin as JavaPlugin,
            remainingTicks = 20L,
            periodTicks = 20L
        ) {
            remainingTimeSeconds = (remainingTimeSeconds - 1).coerceAtLeast(0)
            updateScoreboardLineSuffix("timeRemaining", remainingTimeSeconds)

            // Auto end the game when time runs out
            if (remainingTimeSeconds == 0L) endGame()
        }
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    override fun endGame() {
        super.endGame()

        nukeArea()

        // Reset timer for the next round
        remainingTimeSeconds = PDConst.Times.GAME_DURATION
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun nukeArea() {
        courseRegions.forEach {
            BuildLoader.deleteSchematic(it)
        }
        courseRegions.clear()
    }

    override fun prepareGameSetting() {
        super.prepareGameSetting()
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    override fun prepareArea() {
        createCoursePaths(this)
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun tpPlayerToNextSection(player: Player) {
//        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun tpPlayersToNextSection() {
//        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    fun setCheckpointFor(player: Player) {
//        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun sendToLastCheckpoint(player: Player) {
//        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun reset() {
//        TODO("Not yet implemented")
    }
}