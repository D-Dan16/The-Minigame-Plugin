@file:Suppress("DEPRECATION")

package base.minigames

import base.MinigamePlugin.Companion.plugin
import base.annotations.CalledByCommand
import base.annotations.ShouldBeReset
import base.resources.Colors
import base.resources.Colors.TitleColors.LIME_GREEN
import base.utils.additions.PausableBukkitRunnable
import base.utils.additions.Utils.nukeGameArea
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Team
import java.time.Duration

@Suppress("NOTHING_TO_INLINE")
abstract class MinigameSkeleton

protected constructor() {

    //region Fields
    var isGameRunning: Boolean = false
    var isGamePaused: Boolean = false
    var sender: Player? = null
    var players: MutableList<Player> = mutableListOf()

    abstract val minigameName: String

    @ShouldBeReset
    var gameTimeElapsed = 0

    val scoreboard = Bukkit.getScoreboardManager().newScoreboard
    val scoreboardObjective: Objective = scoreboard.registerNewObjective("The Minigame Plugin","dummy","The Minigame Plugin")


    /**
     *
     * This list tracks all scheduled tasks that are made via the help of BukkitRunnables. Used to cancel the scheduling when desired.
     * This list is automatically called and canceled upon in the endGame() method.
     * A good use for using this is whenever the game is paused, and you would want to stop all the tasks.
     */
    val runnables: MutableList<BukkitRunnable> = mutableListOf()

    /**
     * This list tracks all scheduled tasks that are made via the help of [PausableBukkitRunnable]. Used to pause and resume the scheduling when desired.
     * This list is automatically called and canceled upon in the endGame() method.
     * When the game is paused, all the runnables in this list are paused, and when the game is resumed, all the runnables in this list are resumed.
     */
    val pausableRunnables: MutableList<PausableBukkitRunnable> = mutableListOf()

    enum class WorldSettingsToTrack {
        TIME_OF_DAY,
        RANDOM_TICK_SPEED,
        GAMEMODE,
        HASTE,
        DIFFICULTY
    }

    protected val trackerOfWorldSettingsBeforeStartingGame: MutableMap<WorldSettingsToTrack, Any?> = WorldSettingsToTrack.entries.associateWith { null }.toMutableMap()
    //endregion

    /**
     * Starts the minigame.
     * If the game is already running, it should not start the game again.
     *
     * The method calls methods that prepare the area ([prepareArea]) and the game settings ([prepareGameSetting]) which are abstract and should be implemented in the subclass.
     * @param sender the player that started the minigame
     * @throws InterruptedException if the game is interrupted
     */
    @Throws(InterruptedException::class)
    @CalledByCommand
    open fun start(sender: Player) {
        this@MinigameSkeleton.sender = sender
        players += Bukkit.getServer().onlinePlayers
        isGameRunning = true
        isGamePaused = false


        //Construct the scoreboard info for the minigame.
        scoreboardObjective.displaySlot = DisplaySlot.SIDEBAR

        //region ScoreBoard init bullshit :)
        val teamForTimeElapsed: Team = scoreboard.getTeam("timeElapsed") ?: scoreboard.registerNewTeam("timeElapsed").apply {
            addEntry("")
            prefix(text("Time Elapsed: "))
            suffix(text(gameTimeElapsed))
        }


        // Add the team's line to the scoreboard
        scoreboardObjective.getScore("${ChatColor.YELLOW}Name: $minigameName").score = 15
        scoreboardObjective.getScore("").score = 14

        // display the minigame's scoreboard to the players.
        players.forEach { it.scoreboard = scoreboard }
        //endregion

        // Keep track of the timer for the length of the game and display it in the scoreboard
        pausableRunnables += PausableBukkitRunnable(plugin as JavaPlugin, remainingTicks = 20L, periodTicks = 20L) {
            gameTimeElapsed++
            teamForTimeElapsed.suffix(text(gameTimeElapsed))
        }.apply { this.start() }

        announceMessage("Minigame $minigameName started!", "Good Luck", LIME_GREEN)


        //----- List Of Actions To Be Done When The Game Starts -----//
        prepareArea()
        prepareGameSetting()

        //----------------------------------------------------------------//
    }

    /**
     * Starts the minigame in fast mode. This is essentially the same as [start], but it is the hard mode of the minigame.
     * The increased difficulty should be handled in the minigame itself.
     * Should call [start] as well.
     * @param player the player that started the minigame
     * @throws InterruptedException if the game is interrupted
     */
    @Throws(InterruptedException::class)
    @CalledByCommand
    open fun startFastMode(player: Player) {
        start(player)
    }

    /**
     * Pauses the game. Paused games can be resumed, and they keep certain logic and game logic. Should be overridden and followed with code that pauses the game, like stopping timers, freezing entities...
     */
    @CalledByCommand
    open fun pauseGame() {
        isGamePaused = true

        pausableRunnables.removeIf { it.shouldNotBeUsed }

        for (runnable in pausableRunnables) {
            runnable.pause()
        }

        announceMessage(
            "Minigame paused!",
            "To resume do: /[minigame] resume",
            Colors.TitleColors.CYAN,
            toGameSender = true
        )
    }

    /**
     * Resumes the game. Resumed games should be able to continue from where they were paused. should be overridden and followed with code that resumes the game, like starting timers, unfreezing entities...
     */
    @CalledByCommand
    open fun resumeGame() {
        isGamePaused = false

        for (runnable in pausableRunnables) {
            runnable.start()
        }

        announceMessage("Minigame resumed!", "All frozen Actions have been reactivated", Colors.TitleColors.CYAN)
    }

    /**
     * Ends the game. Should be overridden and followed with code that cleans up the arena, the gamerules... Should also be called when the game is interrupted.
     * Should also call [endGame] at the start of it
     */
    @CalledByCommand
    open fun endGame() {
        pauseGame()
        // copy the list so that we don't get ConcurrentModificationException via adding new runnables to the list while iterating over it
        runnables.toList().forEach { it.cancel()}
        runnables.clear()

        players.forEach {
            it.scoreboard.clearSlot(DisplaySlot.SIDEBAR)
        }

        // say length of game
        announceMessage("Game over!", "Duration: ${gameTimeElapsed}s", Colors.TitleColors.CYAN)

        gameTimeElapsed = 0

        isGameRunning = false
        isGamePaused = false
        sender = null
        players.clear()
    }
    /**
     * Checks if a player is in the minigame. This will be used for event handling, such as player death.
     * @param player The player to check
     * @return True if the player is in the minigame, false otherwise
     */
    fun isPlayerInGame(player: Player?): Boolean {
        return isGameRunning && players.contains(player)
    }

    /**
     * Nukes an area. Should be overridden and followed with code that clears the physical area. Typically, it should be called in [endGame].
     * @param center the center of the nuke
     * @param radius the radius of the nuke
     */
    open fun nukeArea(center: Location, radius: Int) {
        // Delete the surrounding area.
        nukeGameArea(center, radius)

//        announceMessage("Area nuked!", "hope everyone's safe...", Colors.TitleColors.RED)
    }
    /**
     * Prepares the area. Should be followed with code that prepares the physical area. Typically, it should be called in [start].
     */
    abstract fun prepareArea()

    /**
     * Prepares the game rules.
     * This method is called when the game starts and is responsible for setting up the game environment.
     * This includes setting the weather, time of day, inventory, health, saturation, and other game-related settings.
     *
     * Should be overridden with extra settings for the minigame.
     * For example, tping the player to a specific location.
     */
    open fun prepareGameSetting() {
        //clear the weather and set the time to day
        // ~~~~ note: the dimension got is hardcoded, and it is the overworld.
        Bukkit.getWorld("world")!!.setStorm(false)
        Bukkit.getWorld("world")!!.time = 1000
        Bukkit.getWorld("world")!!.isThundering = false

        for (player in players) {
            player.inventory.clear() // Clear the player's inventory
            player.saturation = 20f // Set the player's saturation to full
            player.health = 20.0 // Set the player's health to full
        }
    }

    /**
     * Broadcasts a message and displays a [Title] to either all players or just the game sender.
     *
     * @param content The main text content to be displayed in both the broadcast and title
     * @param subContent The subtitle text to be displayed in the title
     * @param color The hex color string to be used for both the message and title text
     * @param duration The time the message lasts as a [Title] on the players' screen, not including the time it fades in and out. Defaults to 3s.
     * @param toGameSender If true, sends it only to the game sender; if false, sends it to all players (default: false)
     *
     * The title is displayed with the following timing:
     * - Fade in: 500 milliseconds
     * - Stay time: 3000 milliseconds (3 seconds)
     * - Fade out: 500 milliseconds
     */
    protected fun announceMessage(
        content: String = "",
        subContent: String = "",
        color: String,
        duration: Long = 3000,
        toGameSender: Boolean = false
    ) {
        val isContentNotEmpty = content.isEmpty().not()

        val message = text(
            content, TextColor.fromHexString(color)
        )
        val title = Title.title(
            message,
            text(subContent, TextColor.fromHexString(color)),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(duration), Duration.ofMillis(500))
        )

        when {
            // if the player list is empty, it suggests that the minigame just hasn't started yet. with that in mind, we'll announce a message to each player that is available
            players.isEmpty() -> {
                if(isContentNotEmpty) Bukkit.getServer().broadcast(message)
                Bukkit.getServer().onlinePlayers.forEach { it.showTitle(title) }
            }
            toGameSender -> {
                if(isContentNotEmpty) sender?.sendMessage(message)
                sender?.showTitle(title)
            }
            toGameSender.not() -> {
                players.forEach {
                    if(isContentNotEmpty) it.sendMessage(message)
                    it.showTitle(title)
                }
            }
        }
    }

    //region Game State Guards
    val commandNotExecutedMessage = "Command has not been executed"
    /**
     *  Guard clause for executing the method that the command called.
     *  Used for when wanting to call [start].
     *
     *  If the clause is stopping the exception, the game will notify that the command has not been expected.
     *
     *  @return true if the guard has stopped the command from calling the [start] method, otherwise, false.
     *  */
    fun isGameRunning() : Boolean {
        if (isGameRunning) {
            announceMessage(
                "Minigame is already running!",
                commandNotExecutedMessage,
                Colors.TitleColors.ORANGE,
                toGameSender = true
            )
            return true
        }
        return false
    }

    /**
     *  Guard clause for executing the method that the command called.
     *  Used for when wanting to call [resumeGame].
     *
     *  If the clause is stopping the exception, the game will notify that the command has not been expected.
     *
     *  @return true if the guard has stopped the command from calling the [resumeGame] method, otherwise, false.
     *  */
    fun isGameNotPaused() : Boolean {
        if (!isGameRunning || !isGamePaused ) {
            announceMessage(
                "Minigame is not paused!",
                commandNotExecutedMessage,
                Colors.TitleColors.ORANGE,
                toGameSender = true
            )
            return true

        }
        return false
    }

    /**
     *  Guard clause for executing the method that the command called.
     *  Used for when wanting to call [pauseGame].
     *
     *  if the clause is stopping the exception, the game will notify that the command has not been expected.
     *
     *  @return true if the guard has stopped the command from calling the [pauseGame] method, otherwise, false.
     *  */
    fun isGamePaused() : Boolean {
        if (!isGameRunning || isGamePaused) {
            announceMessage(
                "Minigame already paused!",
                commandNotExecutedMessage,
                Colors.TitleColors.ORANGE,
                toGameSender = true
            )
            return true

        }
        return false
    }

    /**
     *  Guard clause for executing the method that the command called.
     *  Used for when wanting to call [endGame].
     *
     *  If the clause is stopping the exception, the game will notify that the command has not been expected.
     *
     *  @return true if the guard has stopped the command from calling the [endGame] method, otherwise, false.
     *  */
    fun isGameNotRunning() : Boolean {
        if (!isGameRunning) {
            announceMessage(
                "Minigame is not running!",
                commandNotExecutedMessage,
                Colors.TitleColors.ORANGE,
                toGameSender = true
            )
            return true

        }
        return false
    }
    //endregion
}
