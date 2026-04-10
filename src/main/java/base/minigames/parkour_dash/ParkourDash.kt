package base.minigames.parkour_dash

import base.MinigamePlugin
import base.annotations.CalledByCommand
import base.annotations.Mode
import base.minigames.MinigameSkeleton
import base.minigames.parkour_dash.PDConst.ParkourPath
import base.resources.Colors.TitleColors.AQUA
import base.utils.additions.Direction.*
import base.utils.additions.PausableBukkitRunnable
import base.utils.additions.createBoxOutline
import base.utils.additions.initFloor
import base.utils.extensions_for_classes.setOnClickListener
import base.utils.extensions_for_classes.toYaw
import base.utils.other.BuildLoader
import com.sk89q.worldedit.regions.CuboidRegion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import java.util.WeakHashMap
import kotlin.collections.plusAssign

class ParkourDash(val plugin: MinigamePlugin) : MinigameSkeleton() {
    override val minigameName: String = this::class.simpleName ?: "Unknown"
    //<editor-fold desc="Properties"> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    //<editor-fold desc="Game Modifiers">
    var difficulty: ParkourDashCommands.Modes = ParkourDashCommands.Modes.NORMAL
        @CalledByCommand(Mode.EXCLUSIVE)
        set

    //</editor-fold>

    //<editor-fold desc="Arena creation handlers">
    internal var locationToGeneratePath = mapOf(
        ParkourPath.LEFT to PDConst.Locations.START_GENERATION_LOCATION_OF_LEFT_PATH.clone(),
        ParkourPath.MIDDLE to PDConst.Locations.START_GENERATION_LOCATION_OF_MIDDLE_PATH.clone(),
        ParkourPath.RIGHT to PDConst.Locations.START_GENERATION_LOCATION_OF_RIGHT_PATH.clone(),
    )

    internal var hallwaysRegions: MutableList<CuboidRegion> = mutableListOf()
    internal var courseRegions: MutableList<CuboidRegion> = mutableListOf()
    //</editor-fold>

    //<editor-fold desc="Timers">
    private var remainingTimeSeconds: Long = PDConst.Times.GAME_DURATION
    //</editor-fold>

    //<editor-fold desc="Teleporters">
    private var parkourPathCheckpointsFor: Map<ParkourPath, HashMap<Player, MutableList<Location>>> = mapOf(
        ParkourPath.LEFT to HashMap(),
        ParkourPath.MIDDLE to HashMap(),
        ParkourPath.RIGHT to HashMap()
    )

    /** All the endpoints of each pk course (i.e., the diamond blocks)*/
    internal var pathCheckpointsNoter: Map<ParkourPath, MutableList<Location>> = mapOf(
        ParkourPath.LEFT to mutableListOf(),
        ParkourPath.MIDDLE to mutableListOf(),
        ParkourPath.RIGHT to mutableListOf()
    )
    
    private var pathPlayerIsAt: WeakHashMap<Player, ParkourPath> = WeakHashMap()
    //</editor-fold>

    //</editor-fold> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    override fun resetState() {
        super.resetState()
        //<editor-fold desc="Game Modifiers">
        difficulty = ParkourDashCommands.Modes.NORMAL

        //</editor-fold>

        //<editor-fold desc="Arena creation handlers">
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
        parkourPathCheckpointsFor = mapOf(
            ParkourPath.LEFT to HashMap(),
            ParkourPath.MIDDLE to HashMap(),
            ParkourPath.RIGHT to HashMap()
        )

        pathCheckpointsNoter = mapOf(
            ParkourPath.LEFT to mutableListOf(),
            ParkourPath.MIDDLE to mutableListOf(),
            ParkourPath.RIGHT to mutableListOf()
        )

        pathPlayerIsAt = WeakHashMap()
        //</editor-fold>
    }


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

    override fun start(sender: Player) {
        super.start(sender)

        players.forEach {
            pathPlayerIsAt[it] = ParkourPath.UNDECIDED
        }

    }

    @CalledByCommand(Mode.EXCLUSIVE)
    override fun endGame() {
        nukeArea()

        //<editor-fold desc="General Player settings">
        players.forEach {
            it.gameMode = GameMode.SURVIVAL
            it.isInvulnerable = false
            it.inventory.clear()
        }
        //</editor-fold>

        super.endGame()
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    fun nukeArea() {
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
        createCoursePaths(this)
        initFloor(5, 5, Material.WHITE_STAINED_GLASS, PDConst.Locations.START_LOCATION.clone())
        createBoxOutline(5, 5, 2, Material.WHITE_STAINED_GLASS, PDConst.Locations.START_LOCATION.clone().apply { y++ })
    }

    override fun prepareGameSetting() {
        super.prepareGameSetting()

        //<editor-fold desc="General Player settings">
        players.forEach {
            it.teleport(PDConst.Locations.START_LOCATION.clone().apply { y+=2 })
            it.gameMode = GameMode.ADVENTURE
            it.isInvulnerable = true
            it.setRotation(EAST.toYaw(), 0.0F)
        }
        //</editor-fold>

        //<editor-fold desc="Init Checkpoints and Path Teleporters">

        // Create a checkpoint for each player at the start of each path.
        players.forEach {
            parkourPathCheckpointsFor[ParkourPath.LEFT]!![it] = mutableListOf()
            parkourPathCheckpointsFor[ParkourPath.MIDDLE]!![it] = mutableListOf()
            parkourPathCheckpointsFor[ParkourPath.RIGHT]!![it] = mutableListOf()

            parkourPathCheckpointsFor[ParkourPath.LEFT]!![it]!! += PDConst.Locations.START_LOCATION_OF_PLAYER_LEFT_PATH.clone()
            parkourPathCheckpointsFor[ParkourPath.MIDDLE]!![it]!! += PDConst.Locations.START_LOCATION_OF_PLAYER_MIDDLE_PATH.clone()
            parkourPathCheckpointsFor[ParkourPath.RIGHT]!![it]!! += PDConst.Locations.START_LOCATION_OF_PLAYER_RIGHT_PATH.clone()
        }

        fun createTeleporter(
            itemStack: ItemStack,
            trackerOfPlayerCheckpoints: HashMap<Player, MutableList<Location>>,
            path: ParkourPath
        ): ItemStack {
            return itemStack.clone().setOnClickListener { clicker ->
                val lastCheckpoint = trackerOfPlayerCheckpoints[clicker]!!.last()

                clicker.teleport(lastCheckpoint)
                clicker.setRotation(EAST.toYaw(), 0.0F)
                pathPlayerIsAt[clicker] = path
            }
        }

        players.forEach { player ->
            // Add items for teleportation to each parkour path
            val leftPathTeleporter = createTeleporter(
                PDConst.Items.LEFT_PATH_TELEPORTER_ICON,
                parkourPathCheckpointsFor[ParkourPath.LEFT]!!,
                ParkourPath.LEFT
            )
            val middlePathTeleporter = createTeleporter(
                PDConst.Items.MIDDLE_PATH_TELEPORTER_ICON,
                parkourPathCheckpointsFor[ParkourPath.MIDDLE]!!,
                ParkourPath.MIDDLE
            )
            val rightPathTeleporter = createTeleporter(
                PDConst.Items.RIGHT_PATH_TELEPORTER_ICON,
                parkourPathCheckpointsFor[ParkourPath.RIGHT]!!,
                ParkourPath.RIGHT
            )

            player.inventory.apply {
                setItem(0,leftPathTeleporter)
                setItem(1,middlePathTeleporter)
                setItem(2,rightPathTeleporter)

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

    @CalledByCommand(Mode.EXCLUSIVE)
    internal fun tpPlayersToNextSection() {
        players.forEach {
            tpPlayerToNextSection(it)
        }
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    internal fun tpPlayerToNextSection(player: Player) {
        val parkourPath: ParkourPath? = pathPlayerIsAt[player]
        val checkpoints = parkourPathCheckpointsFor[parkourPath]?.get(player) ?: throw IllegalStateException("Player is not at any parkour path")
        val lastCheckPointForPlayer = checkpoints.last()

        val nextCheckpoint = pathCheckpointsNoter[parkourPath]?.last {
            lastCheckPointForPlayer.x < it.x
        } ?: return

        checkpoints += nextCheckpoint

        player.teleport(nextCheckpoint.clone().apply { y++ })
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    internal fun setCheckpointFor(player: Player) {
        val checkpoints = parkourPathCheckpointsFor[pathPlayerIsAt[player]]!![player]
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
        val checkpoints = parkourPathCheckpointsFor[pathPlayerIsAt[player]]!![player]
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
        val parkourPath = pathPlayerIsAt[player] ?: throw IllegalStateException("Player is not at any parkour path")
        val locationToTp: Location = when (parkourPath) {
            ParkourPath.LEFT -> parkourPathCheckpointsFor[ParkourPath.LEFT]!![player]!!.last()
            ParkourPath.MIDDLE -> parkourPathCheckpointsFor[ParkourPath.MIDDLE]!![player]!!.last()
            ParkourPath.RIGHT -> parkourPathCheckpointsFor[ParkourPath.RIGHT]!![player]!!.last()
            ParkourPath.UNDECIDED -> return
        } ?: throw IllegalStateException("Location to teleport to is null")

        player.teleport(locationToTp)
    }

    @CalledByCommand(Mode.NON_EXCLUSIVE)
    internal fun reset() {
//        TODO("Not yet implemented")
    }
}

