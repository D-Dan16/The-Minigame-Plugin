package base.minigames.parkour_dash

import base.MinigamePlugin
import base.annotations.CalledByCommand
import base.annotations.Mode
import base.minigames.MinigameSkeleton
import base.minigames.hole_in_the_wall.HITWConst
import net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.io.File
import java.io.IOException
import java.util.Objects

class ParkourDash(val plugin: MinigamePlugin) : MinigameSkeleton() {
    override val minigameName: String = this::class.simpleName ?: "Unknown"

    //<editor-fold desc="Properties"> --------------------------------------------------------

    //<editor-fold desc="Game Modifiers"> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    var difficulty: ParkourDashCommands.Modes = ParkourDashCommands.Modes.NORMAL
    var courseLength: Int = 0
    //</editor-fold> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    //<editor-fold desc="Files">
    lateinit var coursesFolder: File
    //</editor-fold>
    //</editor-fold> --------------------------------------------------------

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

    fun generateCourse(): Nothing {
        TODO("Not yet implemented")
    }

    override fun prepareGameSetting() {
        super.prepareGameSetting()
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    override fun prepareArea() {
        //<editor-fold desc="obtain courses folder">
        if (this::coursesFolder.isInitialized.not()) {
            val baseFolder = plugin.getSchematicsBaseFolder(MinigamePlugin.Companion.MinigameType.PARKOUR_DASH)
            coursesFolder = File(baseFolder, PDConst.FilePaths.COURSES_FILE_PATH)

            if (coursesFolder.exists().not())
                throw IOException("Parkour Dash Courses folder does not exist")
        }
        //</editor-fold>

        generateCourses()
    }

    private fun generateCourses() {
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