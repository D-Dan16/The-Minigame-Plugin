package base.minigames.parkour_dash

import base.MinigamePlugin
import base.annotations.CalledByCommand
import base.annotations.Mode
import base.minigames.MinigameSkeleton
import base.utils.additions.Direction.*
import base.utils.additions.PausableBukkitRunnable
import base.utils.additions.createBoxOutline
import base.utils.additions.initFloor
import base.utils.extensions_for_classes.setOnClickListener
import base.utils.extensions_for_classes.toYaw
import base.utils.other.BuildLoader
import com.sk89q.worldedit.util.Direction
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class ParkourDash(val plugin: MinigamePlugin) : MinigameSkeleton() {
    override val minigameName: String = this::class.simpleName ?: "Unknown"
    //<editor-fold desc="Properties"> ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    //<editor-fold desc="Game Modifiers">
    var difficulty: ParkourDashCommands.Modes = ParkourDashCommands.Modes.NORMAL
        @CalledByCommand(Mode.EXCLUSIVE)
        set

    //</editor-fold>

    //<editor-fold desc="Timers">
    private var remainingTimeSeconds: Long = PDConst.Times.GAME_DURATION
    //</editor-fold>

    //<editor-fold desc="Teleporters">
    private val rightPathLastCheckpoint: HashMap<Player, Location> = HashMap()
    private val middlePathLastCheckpoint: HashMap<Player, Location> = HashMap()
    private val leftPathLastCheckpoint: HashMap<Player, Location> = HashMap()
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
        nukeArea()

        //<editor-fold desc="General Player settings">
        players.forEach {
            it.gameMode = GameMode.SURVIVAL
            it.isInvulnerable = false
            it.inventory.clear()
        }
        //</editor-fold>

        // Reset timer for the next round
        remainingTimeSeconds = PDConst.Times.GAME_DURATION

        locationToGenerateLeftCourse = PDConst.Locations.START_GENERATION_LOCATION_OF_LEFT_PATH.clone()
        locationToGenerateMiddleCourse = PDConst.Locations.START_GENERATION_LOCATION_OF_MIDDLE_PATH.clone()
        locationToGenerateRightCourse = PDConst.Locations.START_GENERATION_LOCATION_OF_RIGHT_PATH.clone()

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
        players.forEach {
            leftPathLastCheckpoint[it] = PDConst.Locations.START_LOCATION_OF_PLAYER_LEFT_PATH.clone()
            middlePathLastCheckpoint[it] = PDConst.Locations.START_LOCATION_OF_PLAYER_MIDDLE_PATH.clone()
            rightPathLastCheckpoint[it] = PDConst.Locations.START_LOCATION_OF_PLAYER_RIGHT_PATH.clone()
        }

        fun createTeleporter(
            itemStack: ItemStack,
            trackerOfLastCheckpoints: HashMap<Player, Location>
        ): ItemStack {
            val item = itemStack.clone()
            item.setOnClickListener { clicker ->
                clicker.teleport(trackerOfLastCheckpoints[clicker]!!)
                clicker.setRotation(EAST.toYaw(), 0.0F)
            }
            item.itemMeta = item.itemMeta.apply {
                addItemFlags(ItemFlag.HIDE_ENCHANTS)
                addEnchant(Enchantment.MENDING, 1, true)
            }

            return item
        }

        players.forEach {
            // Add items for teleportation to each parkour path
            val leftPathTeleporter = createTeleporter(PDConst.Items.LEFT_PATH_TELEPORTER_ICON, leftPathLastCheckpoint)
            val middlePathTeleporter = createTeleporter(PDConst.Items.MIDDLE_PATH_TELEPORTER_ICON, middlePathLastCheckpoint)
            val rightPathTeleporter = createTeleporter(PDConst.Items.RIGHT_PATH_TELEPORTER_ICON, rightPathLastCheckpoint)

            it.inventory.setItem(3,leftPathTeleporter)
            it.inventory.setItem(4,middlePathTeleporter)
            it.inventory.setItem(5,rightPathTeleporter)
        }
        //</editor-fold>
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