package base.minigames.blueprint_bazaar

import base.MinigamePlugin
import base.annotations.CalledByCommand
import base.minigames.MinigameSkeleton
import base.minigames.MinigameSkeleton.WorldSettingsToTrack.GAMEMODE
import base.minigames.MinigameSkeleton.WorldSettingsToTrack.RANDOM_TICK_SPEED
import base.minigames.blueprint_bazaar.BPBConst.Locations
import base.minigames.blueprint_bazaar.BPBConst.WORLD
import base.utils.other.BuildLoader
import base.utils.other.BuildLoader.loadSchematicByFileAndCoordinates
import base.utils.other.BuildLoader.loadSchematicByFileAndDirection
import base.utils.additions.Direction
import base.utils.additions.Utils.initFloor
import base.utils.extensions_for_classes.*
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.block.Furnace
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.io.IOException

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS",
    "SameParameterValue", "DEPRECATION"
)
class BlueprintBazaar(plugin: Plugin) : MinigameSkeleton() {
    override val minigameName: String = "BlueprintBazaar"


    //region vars
    val plugin: MinigamePlugin = plugin as MinigamePlugin
    /** The builds.*/
    private lateinit var allSchematics: Array<File>
    /** The list of available builds. When a build is chosen, it is removed from this list*/
    private val availableSchematics: MutableSet<File?> = mutableSetOf()

    private var arena: File? = null
    private var curBuild: BuildBlueprint? = null
        set(value) {
            if (curBuild?.timeElapsedRunnable?.isCancelled == false) curBuild?.timeElapsedRunnable?.cancel()
            field = value
        }


    //endregion

    @Throws(InterruptedException::class)
    @CalledByCommand
    override fun start(sender: Player) {
        initSchematics()

        super.start(sender)

        // start the cycle of builds
        prepareNewBuild()
    }

    @CalledByCommand
    override fun endGame() {
        super.endGame()

        // set the settings of the world to how they were prior to the start of the minigame.
        trackerOfWorldSettingsBeforeStartingGame.apply {
            WORLD.setGameRule(GameRule.RANDOM_TICK_SPEED, this[RANDOM_TICK_SPEED] as Int)
            for (player in players) {
                player.gameMode = this[GAMEMODE] as GameMode
                player.activePotionEffects.clear()
                player.allowFlight = false
                player.isFlying = false
            }
        }

        curBuild = null

        nukeArea(Locations.GAME_START_LOCATION,25)
    }

    override fun prepareArea() {
        nukeArea(Locations.GAME_START_LOCATION, Locations.GAME_AREA_RADIUS)
        val arenaRegion: CuboidRegion = BuildLoader.loadSchematicByFile(arena!!, Locations.GAME_START_LOCATION) as CuboidRegion

        // put in furnaces, Coal blocks, and in chests axes to strip logs
        for (vector in arenaRegion) {
            val block = WORLD.getBlockAt(vector)
            val state = block.state

            if (state is Container) {
                val inventory = state.inventory
                when (block.state) {
                    is Chest -> {
                        inventory.addItem(ItemStack(Material.IRON_AXE, 1))
                        inventory.addItem(ItemStack(Material.HONEYCOMB, 64))
                    }
                    is Furnace -> {
                        inventory.setItem(1, ItemStack(Material.COAL, 64))
                    }
                    else -> {}
                }
            }

        }
    }

    override fun prepareGameSetting() {
        super.prepareGameSetting()

        //tracking state
        trackerOfWorldSettingsBeforeStartingGame.apply {
            put(RANDOM_TICK_SPEED, WORLD.getGameRuleValue(GameRule.RANDOM_TICK_SPEED))
            put(GAMEMODE, players[0].gameMode)
        }

        //setting state
        WORLD.setGameRule(GameRule.RANDOM_TICK_SPEED,0)

        for (player in players) {
            // Teleport the player to the start location
            player.teleport(Locations.GAME_START_LOCATION.clone().add(0.0, 8.0, 0.0))
            player.setRotation(Direction.EAST.toYaw(),0f)
            player.gameMode = GameMode.SURVIVAL
            player.allowFlight = true
        }
    }

    /**
     * Initializes the availableSchematics list with all the builds in the schematics folder, as well as some extras.
     */
    @CalledByCommand
    fun initSchematics() {
        if (isAlreadyRunning()) {
            sender!!.sendMessage("Cannot initialize schematics while the game is running.")
            return
        }

        // Gets the schematics folder from the MinigamePlugin.java. This is where the builds are stored.
        val schematicsFolder = plugin.getSchematicsBaseFolder(MinigamePlugin.Companion.MinigameType.BLUEPRINT_BAZAAR)

        schematicsFolder.listFiles().forEach { path ->
            when (path.name.substringBefore('.')) {
                "BlueprintBazaarBuilds" -> this.allSchematics = path.listFiles()
                "arena" -> arena = path
                else -> IOException("can't find file in blueprint bazaar")
            }
        }

        availableSchematics += allSchematics.toList()
    }

    /**
     * Chooses a new build from the availableSchematics list. The build is taken out of the available list
     * @return The chosen build
     */
    private fun chooseNewBuild(): File? {
        if (availableSchematics.isEmpty()) {
            // Handle the case where there are no available schematics
            plugin.logger.severe("No available schematics to choose from.")
            return null
        }
        // Choose a random build from the schematics folder and delete it from the list
        val chosenBuild = availableSchematics.random()
        availableSchematics.remove(chosenBuild)

        return chosenBuild
    }

    fun prepareNewBuild() {
        val chosenBuild = chooseNewBuild()

        if (chosenBuild == null) {
            Bukkit.getServer().broadcast(Component.text("No more builds available!").color(NamedTextColor.AQUA))
            endGame()
            return
        }

        // Randomly decide if the build should be mirrored //fixme: false for now bcuz mirroring does more than wanted and moves the entire pos of the build plot
        val shouldBeMirrored = false//Random().nextBoolean()

        // Create the new build
        curBuild = createNewBuild(chosenBuild, Locations.CENTER_BUILD_SHOWCASE_PLOT, shouldBeMirrored)

        // register the player block placing listener for this build.
        MinigamePlugin.plugin.server.pluginManager.registerEvents(curBuild!!, plugin)

        val message = "List of ingredients for build: \n ${chosenBuild.name} \n ${curBuild?.materialList.toString()} "
        Bukkit.getServer().broadcast(Component.text(message).color(NamedTextColor.AQUA))

        for (player in players) {
            player.clearInvAndGiveItems(curBuild!!.materialList,64)
        }

    }

    private fun createNewBuild(chosenBuild: File, location: Location, shouldBeMirrored: Boolean = false): BuildBlueprint {
        Bukkit.getServer().broadcast(Component.text("New building created!"))

        // physically load the schematic at the given location

        // store the region of the loaded schematic in this
        val region: CuboidRegion = loadSchematicByFileAndDirection(
            chosenBuild,
            location,
            BPBConst.Build.buildFacingDirection,
            shouldBeMirrored,
        ) as CuboidRegion

        val minP = region.minimumPoint
        val maxP = region.maximumPoint


        //copy the floor from the displayed build plot to the build plot the player is gonna recreate the build at. also clear any blocks in the area of the recreated plot.
        for (vector in region) {
            when (vector.y) {
                minP.y -> WORLD.getBlockAt(vector + Locations.CENTER_BUILD_PLOT_OFFSET).type = WORLD.getMaterialAt(vector)
                else -> WORLD.getBlockAt(vector + Locations.CENTER_BUILD_PLOT_OFFSET).type = Material.AIR
            }
        }

        val buildRegion = CuboidRegion(
            BlockVector3.at(minP.x,minP.y+1,minP.z),
            maxP
        )


        //now that the schematic is loaded, we can create a new Build object

        // Create a new Build object with the loaded schematic
        return BuildBlueprint(
            this,
            buildRegion
        )
    }

    private fun deleteBuild(build: BuildBlueprint) {
        // we need to delete the blocks in both the build display plot and the build plot.

        // Delete the blocks in the build display plot
        for (vector in build.regionOfBuildDisplayed) {
            WORLD.getBlockAt(vector).type = Material.AIR
        }
        // Delete the blocks in the build plot
        for (vector in build.region) {
            WORLD.getBlockAt(vector).type = Material.AIR
        }

        curBuild = null
    }

    fun completeBuild(build: BuildBlueprint) {
        deleteBuild(build)
        prepareNewBuild()
    }

    @CalledByCommand
    fun skipToNextBuild() {
        if (!isAlreadyRunning()) {
            sender!!.sendMessage("can't execute this if the game isn't running.")
            return
        }

        curBuild?.prepareForCompletion()
    }

    /**
     * Loads all the builds in the schematics folder. The builds are loaded in a grid pattern.
     */
    @CalledByCommand
    fun loadAllSchematics() {
        var index = 0
        // Load all the builds in the schematics folder
        // Load the schematic relative to the center build plot. The x and z coordinates are Modified in a way that makes the builds appear in a grid.
        for (schematic in allSchematics) {
            // Calculate the x, y, and z coordinates for the build
            val curX = Locations.CENTER_BUILD_SHOWCASE_PLOT.x().toInt() + (10 * (index % 6))
            val curY = (Locations.CENTER_BUILD_SHOWCASE_PLOT.y()).toInt()
            val curZ = (Locations.CENTER_BUILD_SHOWCASE_PLOT.z() + 10 * (index / 6)).toInt()

            // Initialize the floor for this build
            initFloor(
                6,
                6,
                Material.RED_WOOL,
                Location(WORLD, (curX - 3).toDouble(), (curY - 2).toDouble(), curZ.toDouble()),
                WORLD
            )
            // Load the schematic
            loadSchematicByFileAndCoordinates(schematic, curX, curY, curZ)

            // Increment the index for the position of the next build
            index++
        }
    }

    @CalledByCommand
    fun cycleThroughSchematics() {
        if (!isAlreadyRunning() || isAlreadyPaused()) {
            sender!!.sendMessage("Game is not currently alive to do this.")
            return
        }

        val runnable = object : BukkitRunnable() {
            var index: Int = 0

            override fun run() {
                // Check if we've gone through all schematics
                if (index >= allSchematics.size) {
                    cancel()
                    return
                }
                // Delete the previous build if it exists
                curBuild?.prepareForCompletion()

                // Move to the next schematic
                index++
            }
        }
        runnable.runTaskTimer(plugin, 0L, BPBConst.Timers.DELAY_BETWEEN_SHOWCASING_BUILDS)
        runnables.add(runnable)
    }
}



