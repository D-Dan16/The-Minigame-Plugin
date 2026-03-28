package base.minigames.parkour_dash

import base.annotations.CalledByCommand
import base.annotations.Mode
import base.minigames.MinigameSkeleton
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

class ParkourDash(val plugin: Plugin) : MinigameSkeleton() {
    override val minigameName: String = this::class.simpleName ?: "Unknown"

    @CalledByCommand(Mode.EXCLUSIVE)
    override fun start(sender: Player) {
        super.start(sender)
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    override fun pauseGame() {
        super.pauseGame()
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    override fun resumeGame() {
        super.resumeGame()
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    override fun endGame() {
        super.endGame()
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun nukeArea() {
        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun generateCourse(): Nothing {
        TODO("Not yet implemented")
    }

    override fun prepareGameSetting() {
        super.prepareGameSetting()
    }

    override fun prepareArea() {
        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    fun setDifficulty(mode: ParkourDashCommands.Modes) {
        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun tpPlayerToNextSection(player: Player): Nothing {
        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun tpPlayersToNextSection(): Nothing {
        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    fun setCheckpointFor(player: Player): Nothing {
        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun sendToLastCheckpoint(player: Player): Nothing {
        TODO("Not yet implemented")
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun reset(): Nothing {
        TODO("Not yet implemented")
    }
}