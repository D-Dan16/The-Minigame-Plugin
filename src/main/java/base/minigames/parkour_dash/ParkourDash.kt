package base.minigames.parkour_dash

import base.MinigamePlugin
import base.annotations.CalledByCommand
import base.annotations.Mode
import base.minigames.MinigameSkeleton
import base.minigames.parkour_dash.PDConst.ParkourPath
import base.minigames.parkour_dash.types.PlayerParkourState
import base.minigames.parkour_dash.types.player_score.PlayerScore
import base.resources.Colors
import base.resources.Colors.TitleColors.AQUA
import base.utils.additions.Direction
import base.utils.additions.Direction.*
import base.utils.additions.createBoxOutline
import base.utils.additions.initFloor
import base.utils.extensions_for_classes.setOnClickListener
import base.utils.extensions_for_classes.showTitle
import base.utils.extensions_for_classes.toYaw
import base.utils.other.BuildLoader
import com.sk89q.worldedit.regions.CuboidRegion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.WeakHashMap
import kotlin.properties.Delegates

class ParkourDash(val plugin: MinigamePlugin) : MinigameSkeleton(), Listener {
    override val minigameName: String = this::class.simpleName ?: "Unknown"
    //<editor-fold desc="Properties"> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    //<editor-fold desc="Misc">
    var difficulty: ParkourDashCommands.Modes = ParkourDashCommands.Modes.NORMAL
        @CalledByCommand(Mode.EXCLUSIVE)
        set

    internal var scores: WeakHashMap<UUID, PlayerScore> = WeakHashMap()
    internal var totalPossibleScore = 0
    var playerBossBars: MutableMap<UUID, BossBar> = HashMap()

    //</editor-fold>

    //<editor-fold desc="Arena creation handlers">
    internal var hasSetUpArena: Boolean = false
    internal var locationToGeneratePath = mapOf(
        ParkourPath.LEFT to PDConst.Locations.START_GENERATION_LOCATION_OF_LEFT_PATH.clone(),
        ParkourPath.MIDDLE to PDConst.Locations.START_GENERATION_LOCATION_OF_MIDDLE_PATH.clone(),
        ParkourPath.RIGHT to PDConst.Locations.START_GENERATION_LOCATION_OF_RIGHT_PATH.clone(),
    )

    internal var hallwaysRegions: MutableList<CuboidRegion> = mutableListOf()
    internal var courseRegions: MutableList<CuboidRegion> = mutableListOf()
    //</editor-fold>

    //<editor-fold desc="Timers">
    internal var remainingTimeSeconds: Long = PDConst.Times.GAME_DURATION
    //</editor-fold>

    //<editor-fold desc="Teleporters">
    private val activePaths = ParkourPath.entries.filter { it != ParkourPath.UNDECIDED }

    /** All the endpoints of each pk course (i.e., the diamond blocks)*/
    internal var endOfCourses: Map<ParkourPath, MutableList<Location>> = mapOf(
        ParkourPath.LEFT to mutableListOf(PDConst.Locations.START_GENERATION_LOCATION_OF_LEFT_PATH),
        ParkourPath.MIDDLE to mutableListOf(PDConst.Locations.START_GENERATION_LOCATION_OF_MIDDLE_PATH),
        ParkourPath.RIGHT to mutableListOf(PDConst.Locations.START_GENERATION_LOCATION_OF_RIGHT_PATH)
    )

    /** Consolidated per-player state: current path, checkpoints, and courses completed */
    internal var playerParkourState: MutableMap<UUID, PlayerParkourState> = mutableMapOf()

    /** Points awarded per course, indexed by \[path]\[courseIndex] */
    internal var pointsForCourse: MutableMap<ParkourPath, MutableList<Int>> =
        activePaths.associateWith { mutableListOf<Int>() }.toMutableMap()
    //</editor-fold>

    //</editor-fold> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    override fun resetState() {
        //<editor-fold desc="Misc">
        difficulty = ParkourDashCommands.Modes.NORMAL
        scores = WeakHashMap()
        totalPossibleScore = 0
        players.forEach {
            playerBossBars[it.uniqueId]?.isVisible = false
        }
        playerBossBars = HashMap()
        //</editor-fold>

        //<editor-fold desc="Arena creation handlers">
        hasSetUpArena = false

        locationToGeneratePath = mapOf(
            ParkourPath.LEFT to PDConst.Locations.START_GENERATION_LOCATION_OF_LEFT_PATH.clone(),
            ParkourPath.MIDDLE to PDConst.Locations.START_GENERATION_LOCATION_OF_MIDDLE_PATH.clone(),
            ParkourPath.RIGHT to PDConst.Locations.START_GENERATION_LOCATION_OF_RIGHT_PATH.clone(),
        )


        hallwaysRegions = mutableListOf()
        courseRegions = mutableListOf()
        //</editor-fold>

        //<editor-fold desc="Timers">
        remainingTimeSeconds = PDConst.Times.GAME_DURATION
        //</editor-fold>

        //<editor-fold desc="Teleporters">
        endOfCourses = mapOf(
            ParkourPath.LEFT to mutableListOf(PDConst.Locations.START_GENERATION_LOCATION_OF_LEFT_PATH),
            ParkourPath.MIDDLE to mutableListOf(PDConst.Locations.START_GENERATION_LOCATION_OF_MIDDLE_PATH),
            ParkourPath.RIGHT to mutableListOf(PDConst.Locations.START_GENERATION_LOCATION_OF_RIGHT_PATH)
        )

        playerParkourState = mutableMapOf()
        pointsForCourse = activePaths.associateWith { mutableListOf(0) }.toMutableMap()
        //</editor-fold>

        super.resetState()
    }

    override fun initState() {
        players.forEach {
            scores[it.uniqueId] = PlayerScore(0)

            playerBossBars[it.uniqueId] = Bukkit.createBossBar(
                "Your Score: 0",
                BarColor.GREEN,
                BarStyle.SOLID
            ).apply {
                addPlayer(it)
                progress = 0.0
            }

            playerParkourState[it.uniqueId] = PlayerParkourState()
        }

        pointsForCourse.forEach { (_, ints) -> totalPossibleScore += ints.sum() }
    }

    override fun addScoreboardElements() {
        registerScoreboardLine("timeRemaining", "Time Remaining: ", suffix = remainingTimeSeconds)
    }

    override fun addTimeBasedEvents() {
        updateTimeRemaining()
    }

    override fun start(sender: Player) {
        super.start(sender)
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    override fun endGame() {
        nukeArena()

        //<editor-fold desc="General Player settings">
        players.forEach {
            it.gameMode = GameMode.SURVIVAL
            it.isInvulnerable = false
            it.inventory.clear()
        }
        //</editor-fold>

        super.endGame()

        players.forEach { it.showTitle(
            "Game over!",
            "Duration: ${scores[it.uniqueId]}s",
            Colors.TitleColors.CYAN
        ) }
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    override fun nukeArena() {
        courseRegions.forEach {
            BuildLoader.deleteSchematic(it)
        }
        courseRegions.clear()

        hallwaysRegions.forEach {
            BuildLoader.deleteSchematic(it)
        }
        hallwaysRegions.clear()

        initFloor(5, 5, Material.AIR, PDConst.Locations.START_LOCATION.clone())
        createBoxOutline(5, 5, 2, Material.AIR, PDConst.Locations.START_LOCATION.clone().apply { y++ })
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    override fun prepareArea() {
        if (hasSetUpArena) return

        createCoursePaths()
        generateStartingBox()
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    internal fun generatePreviewCourse() {
        createCoursePaths()
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    internal fun generateStartingBox() {
        initFloor(5, 5, Material.WHITE_STAINED_GLASS, PDConst.Locations.START_LOCATION.clone())
        createBoxOutline(5, 5, 2, Material.WHITE_STAINED_GLASS, PDConst.Locations.START_LOCATION.clone().apply { y++ })
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    override fun prepareGameSetting() {
        if (hasSetUpArena.not())
            setGeneralPlayerSettings()

        //<editor-fold desc="Init Checkpoints and Path Teleporters">

        // Create a checkpoint for each player at the start of each path.
        fun createTeleporter(itemStack: ItemStack, path: ParkourPath): ItemStack {
            return itemStack.clone().setOnClickListener { clicker ->
                val state = playerParkourState[clicker.uniqueId] ?: return@setOnClickListener

                state.currentPath = path

                val lastCheckpoint = state.checkpoints[state.currentPath]?.lastOrNull() ?: return@setOnClickListener

                clicker.teleport(lastCheckpoint)
                clicker.setRotation(EAST.toYaw(), 0.0F)
            }
        }

        // Teleporter items are identical for all players, so create once
        val leftPathTeleporter = createTeleporter(PDConst.Items.LEFT_PATH_TELEPORTER_ICON, ParkourPath.LEFT)
        val middlePathTeleporter = createTeleporter(PDConst.Items.MIDDLE_PATH_TELEPORTER_ICON, ParkourPath.MIDDLE)
        val rightPathTeleporter = createTeleporter(PDConst.Items.RIGHT_PATH_TELEPORTER_ICON, ParkourPath.RIGHT)

        players.forEach { player ->
            player.inventory.apply {
                setItem(0, leftPathTeleporter)
                setItem(1, middlePathTeleporter)
                setItem(2, rightPathTeleporter)

                setItem(5, PDConst.Items.SET_CUSTOM_CHECKPOINT.clone().setOnClickListener {
                    setCheckpointFor(player)
                })
                setItem(6, PDConst.Items.REMOVE_LATEST_CHECKPOINT.clone().setOnClickListener {
                    removeLatestCheckpoint(player)
                })
                setItem(7, PDConst.Items.GO_BACK_TO_LATEST_CHECKPOINT.clone().setOnClickListener {
                    sendToLastCheckpoint(player)
                })
            }
        }
        //</editor-fold>
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    internal fun setGeneralPlayerSettings() {
        super.prepareGameSetting()

        players.forEach {
            it.teleport(PDConst.Locations.START_LOCATION.clone().apply { y += 2 })
            it.gameMode = GameMode.ADVENTURE
            it.isInvulnerable = true
            it.setRotation(EAST.toYaw(), 0.0F)
        }
    }

    @CalledByCommand(Mode.EXCLUSIVE)
    internal fun tpPlayersToNextSection() {
        players.forEach {
            tpPlayerToNextSection(it)
        }
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    internal fun tpPlayerToNextSection(player: Player) {
        val state = playerParkourState[player.uniqueId] ?: throw IllegalStateException("Player has no parkour state")
        val parkourPath = state.currentPath
        val checkpoints = state.checkpoints[parkourPath] ?: throw IllegalStateException("Player is not at any parkour path")
        val lastCheckPointForPlayer = checkpoints.last()

        val nextCheckpoint = endOfCourses[parkourPath]?.first {
            lastCheckPointForPlayer.x < it.x
        } ?: return

        checkpoints += nextCheckpoint.apply { yaw = EAST.toYaw() }

        player.teleport(nextCheckpoint.clone().apply { y++ })
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    internal fun setCheckpointFor(player: Player) {
        val state = playerParkourState[player.uniqueId]
            ?: throw IllegalStateException("Player has no parkour state")
        val checkpoints = state.checkpoints[state.currentPath]
            ?: throw IllegalStateException("Player is not at any parkour path")

        @Suppress("DEPRECATION")
        if (player.isOnGround.not()) {
            announceMessage(
                subContent = "Stay still",
                color = AQUA,
                duration = 400L,
                toGameSender = true
            )
            return
        }

        checkpoints += player.location

        player.sendActionBar(Component
            .text("New checkpoint set")
            .color(NamedTextColor.AQUA)
        )
    }

    private fun removeLatestCheckpoint(player: Player) {
        val state = playerParkourState[player.uniqueId]
            ?: throw IllegalStateException("Player has no parkour state")
        val checkpoints = state.checkpoints[state.currentPath]
            ?: throw IllegalStateException("Player is not at any parkour path")

        if (checkpoints.size <= 1)
            return

        checkpoints.removeLast()

        player.sendActionBar(Component
            .text("Removed last checkpoint")
            .color(NamedTextColor.AQUA)
        )
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    internal fun sendToLastCheckpoint(player: Player) {
        val state = playerParkourState[player.uniqueId]
            ?: throw IllegalStateException("Player has no parkour state")
        if (state.currentPath == ParkourPath.UNDECIDED) return

        val locationToTp = state.checkpoints[state.currentPath]?.lastOrNull()
            ?: throw IllegalStateException("No checkpoints available for player")

        player.teleport(locationToTp)
    }

    /**
     * Prevents players from dropping items
     */
    @EventHandler
    fun onPlayerItemDrop(event: PlayerDropItemEvent) {
        if (!isGameRunning) return

        event.isCancelled = true
    }

    /**
     * Used to remove a player's score if they quit
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player: Player = event.player
        playerBossBars.remove(player.uniqueId)
        playerParkourState.remove(player.uniqueId)
    }
}
