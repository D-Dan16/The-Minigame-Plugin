package base.minigames.parkour_dash

import base.minigames.parkour_dash.PDConst.ParkourPath
import base.minigames.parkour_dash.types.CourseIndex
import base.utils.additions.PausableBukkitRunnable
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import kotlin.collections.plusAssign

class ParkourDashGameEventsHandler(private val pd: ParkourDash) : Listener {
    /**
     * Check if the player has completed a course.
     * If so, add the checkpoint to the list of checkpoints the player has and increment how many courses in that path the player has completed
     */
    @EventHandler
    fun checkIfPlayerCompleteCourse(event: PlayerMoveEvent) {
        val player = event.player
        val uid = player.uniqueId

        val state = pd.playerParkourState[uid] ?: return
        val parkourPath: ParkourPath = state.currentPath

        var curCoursesCompleted: CourseIndex = state.coursesCompleted[parkourPath]!!
        val courseCheckpointToCheck = pd.endOfCourses[parkourPath]!![curCoursesCompleted.i+1]

        if (courseCheckpointToCheck.x <= player.location.x) {
            // we have completed a course since we have reached the diamond block checkpoint of the prev course
            state.checkpoints[parkourPath]!! += player.location.clone()
            state.coursesCompleted[parkourPath]!!.i += 1

            player.playSound(player.location,Sound.BLOCK_NOTE_BLOCK_BELL,1f,1f)
            pd.scores[uid]!!.value += pd.pointsForCourse[parkourPath]!![curCoursesCompleted.i]

            val playerScore = pd.scores[uid]!!.value
            val bossBar = pd.playerBossBars[uid] ?: return
            bossBar.apply {
                setTitle("Your Score: $playerScore")
                progress = (playerScore.toDouble() / pd.totalPossibleScore)
            }
        }
    }

}

// Tick down every second and update suffix. When it reaches 0, end the game.
fun ParkourDash.updateTimeRemaining() {
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