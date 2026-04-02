// src/main/java/me/stavgordeev/plugin/Minigame.java
package base.minigames.disco_mayhem

import base.minigames.MinigameSkeleton
import base.utils.extensions_for_classes.getBlockAt
import base.utils.additions.Utils.initFloor
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.time.Duration
import java.util.*
import kotlin.math.max

@Suppress("DEPRECATION")
class DiscoMayhem (val plugin: Plugin) : MinigameSkeleton() {
    override val minigameName: String = "DiscoMayhem"


    //--Game Modifiers that change as the game progresses to scale difficulty-//
    private var upperBound__startingIntervalForChangingFloor = 0
    private var lowerBound__startingIntervalForChangingFloor = 0
    private var upperBound__stopChangingFloorInterval = 0
    private var lowerBound__stopChangingFloorInterval = 0

    /**
     * Starts the minigame. The player is teleported to the starting location, and the game is initialized.
     * @param sender The player that starts the minigame
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
    override fun start(sender: Player) {
        super.start(sender)

        initModifiers() // Initialize the modifiers for the game

        // Wait a lil before starting game events.
        object : BukkitRunnable() {
            override fun run() {
                activateGameEvents()
                cancel()
            }
        }.runTaskLater(plugin, 40)
    }

    /**
     * Starts the minigame in fast mode. The player is teleported to the starting location, and the game is initialized.
     * @param player The player that starts the minigame
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
    override fun startFastMode(player: Player) {
        start(player)

        upperBound__startingIntervalForChangingFloor = 10
        lowerBound__startingIntervalForChangingFloor = 10
        upperBound__stopChangingFloorInterval = 1
        lowerBound__stopChangingFloorInterval = 1
    }

    /**
     * Activates the game events.
     * Game events range from taking care of changing floor logic to decreasing the interval for changing the floor.
     */
    fun activateGameEvents() {
        preppingForAFloorCycle(DiscoMayhemConst.GAME_START_LOCATION)
        decreaseStartingIntervalForChangingFloorTimer()
    }

    /**
     * Resumes the minigame. The game is resumed and the player is notified.
     */
    //fixme: some parts of the game are not resumed- the game is not resumed, but the floor is not changed nor old floors aren't removed.
    override fun resumeGame() {
        super.resumeGame()

        activateGameEvents() // Resume the game events
        // Add more actions here
    }

    /**
     * Ends the minigame. The game is ended and the player is notified. The area is cleared.
     */
    override fun endGame() {
        super.endGame()

        nukeArea(DiscoMayhemConst.GAME_START_LOCATION, DiscoMayhemConst.NUKE_AREA_RADIUS)

        initModifiers() // Reset the modifiers for the game

        if (intervalTask != null && !intervalTask!!.isCancelled) intervalTask!!.cancel() // Cancel the task that decreases the interval for changing the floor as time goes on


        //player.teleport(MinigameConstants.GAME_START_LOCATION.clone().add(0, -70, 0));
    }

    /**
     * Initializes the modifiers that CAN be tempered with for the game.
     * Modifiers change throughout the game to scale difficulty.
     * This method is called when the game starts and when the game ends.
     */
    fun initModifiers() {
        upperBound__startingIntervalForChangingFloor =
            DiscoMayhemConst.FloorLogic.ChangingFloor.UPPER_BOUND_START_INTERVAL
        lowerBound__startingIntervalForChangingFloor =
            DiscoMayhemConst.FloorLogic.ChangingFloor.LOWER_BOUND_START_INTERVAL
        upperBound__stopChangingFloorInterval = DiscoMayhemConst.FloorLogic.ChangingFloor.UPPER_BOUND_STOP_INTERVAL
        lowerBound__stopChangingFloorInterval = DiscoMayhemConst.FloorLogic.ChangingFloor.LOWER_BOUND_STOP_INTERVAL
    }

    /**
     * Removes all blocks in a radius around a location.
     * @param center The center of the area to nuke
     * @param radius The radius of the area
     */
    override fun nukeArea(center: Location, radius: Int) {
        val minX: Int = center.blockX - radius
        val maxX: Int = center.blockX + radius
        val minY: Int = center.blockY - radius/3
        val maxY: Int = center.blockY + radius/3
        val minZ: Int = center.blockZ - radius
        val maxZ: Int = center.blockZ + radius

        val region = CuboidRegion(
            BlockVector3.at(minX, minY, minZ),
            BlockVector3.at(maxX, maxY, maxZ)
        )

        for (vector in region) {
            DiscoMayhemConst.WORLD.getBlockAt(vector).type = Material.AIR
        }
    }

    override fun prepareArea() {
        nukeArea(DiscoMayhemConst.GAME_START_LOCATION, DiscoMayhemConst.NUKE_AREA_RADIUS) // Clear the area before starting the game

        val floorCenter = DiscoMayhemConst.INIT_FLOOR_LOCATION // The center of the floor
        initFloor(
            7,
            7,
            Material.GLASS,
            floorCenter,
            DiscoMayhemConst.WORLD
        ) // Initialize the floor under the player to glass

        // Wait a lil before removing the initial floor.
        object : BukkitRunnable() {
            override fun run() {
                initFloor(7, 7, Material.AIR, floorCenter, DiscoMayhemConst.WORLD)
            }
        }.runTaskLater(plugin, 100)
    }

    override fun prepareGameSetting() {
        DiscoMayhemConst.WORLD.time = 6000 // Set the time to day
        DiscoMayhemConst.WORLD.setStorm(false) // Disable rain
        DiscoMayhemConst.WORLD.isThundering = false // Disable thunder

        super.prepareGameSetting()

        for (player in players) {
            player.teleport(DiscoMayhemConst.PLAYER_TP_LOCATION)
            player.gameMode = GameMode.ADVENTURE // Set the player's game mode to adventure
        }
    }

    /**
     * Prepares for a floor cycle. Initializes the new floor and gives it randomized values.
     * After that starts the floor change logic cycle.
     * @param referenceLocation The location to reference for the new floor. This is the location of the last floor. This location will be used to calculate the new floor's center.
     */
    private fun preppingForAFloorCycle(referenceLocation: Location) {
        if (!isAlreadyRunning() || isAlreadyPaused()) {
            return
        }
        Bukkit.getServer().broadcast(Component.text("prepping for change floor").color(NamedTextColor.DARK_AQUA))

        val radiusRandomizer = Random()
        val intervalRandomizer = Random()

        // Randomize the radius of the floor and the interval between floor changes.
        val xRad = radiusRandomizer.nextInt(
            DiscoMayhemConst.FloorLogic.FloorSize.LOWER_BOUND_X_RADIUS,
            DiscoMayhemConst.FloorLogic.FloorSize.UPPER_BOUND_X_RADIUS + 1
        )
        val zRad = radiusRandomizer.nextInt(
            DiscoMayhemConst.FloorLogic.FloorSize.LOWER_BOUND_Z_RADIUS,
            DiscoMayhemConst.FloorLogic.FloorSize.UPPER_BOUND_Z_RADIUS + 1
        )
        val interval = intervalRandomizer.nextInt(
            lowerBound__startingIntervalForChangingFloor,
            upperBound__startingIntervalForChangingFloor + 1
        )
        val stopInterval =
            intervalRandomizer.nextInt(lowerBound__stopChangingFloorInterval, upperBound__stopChangingFloorInterval + 1)

        // Randomize the center of the new floor. For the z and x coordinates, the min value represents the min distance compared to the last floor reference. For the y coordinate, there is a min and max value.
        val newCenterCoordinatesRandomizer = Random()
        var randomisedXDiff = newCenterCoordinatesRandomizer.nextInt(
            DiscoMayhemConst.FloorLogic.NewFloorSpawnBoundaries.LOWER_BOUND_X_CENTER,
            DiscoMayhemConst.FloorLogic.NewFloorSpawnBoundaries.UPPER_BOUND_X_CENTER + 1
        )
        randomisedXDiff = randomlyChangeSign(randomisedXDiff)
        var randomisedZDiff = newCenterCoordinatesRandomizer.nextInt(
            DiscoMayhemConst.FloorLogic.NewFloorSpawnBoundaries.LOWER_BOUND_Z_CENTER,
            DiscoMayhemConst.FloorLogic.NewFloorSpawnBoundaries.UPPER_BOUND_Z_CENTER + 1
        )
        randomisedZDiff = randomlyChangeSign(randomisedZDiff)
        val randomisedYDiff = newCenterCoordinatesRandomizer.nextInt(
            DiscoMayhemConst.FloorLogic.NewFloorSpawnBoundaries.LOWER_BOUND_Y_CENTER,
            DiscoMayhemConst.FloorLogic.NewFloorSpawnBoundaries.UPPER_BOUND_Y_CENTER + 1
        )

        // center of the new floor. the new center is tied to the reference location.
        val center = referenceLocation.clone().add(
            Location(
                DiscoMayhemConst.WORLD,
                randomisedXDiff.toDouble(),
                randomisedYDiff.toDouble(),
                randomisedZDiff.toDouble()
            )
        )

        Bukkit.broadcastMessage(ChatColor.BLUE.toString() + "Diff in centers: " + randomisedXDiff + " " + randomisedYDiff + " " + randomisedZDiff)
        Bukkit.broadcastMessage(ChatColor.BLUE.toString() + "new floor center: " + formatLocation(center))

        // Start the floor change logic cycle.
        changeFloor(center, xRad, zRad)
        activateChangeFloorTimerWithGrowingFrequency(center, interval, stopInterval, xRad, zRad)
    }

    /**
     * Randomly changes the sign of a value. The value can be positive or negative.
     * @param value The value to change the sign of
     * @return The value with a randomly changed sign
     */
    private fun randomlyChangeSign(value: Int): Int {
        var value = value
        val random = Random()
        val isFlipped = random.nextBoolean()
        if (isFlipped) value = -value

        return value
    }

    /**
     * Recursively calls the changeFloor method with a decreasing interval. The interval is decremented by 1 each time the method is called.
     * @param interval The interval between floor changes
     * @param stopInterval The interval at which the recursion stops
     * @param xRad The x radius of the floor
     * @param zRad The z radius of the floor
     */
    private fun activateChangeFloorTimerWithGrowingFrequency(
        center: Location,
        interval: Int,
        stopInterval: Int,
        xRad: Int,
        zRad: Int
    ) {
        if (!isAlreadyRunning() || isAlreadyPaused()) {
            return
        }

        object : BukkitRunnable() {
            override fun run() {
                if (interval == stopInterval || interval == DiscoMayhemConst.MIN_INTERVAL) {
                    Bukkit.broadcastMessage("recursion stopped. interval is $interval")

                    chooseFloorBlockType(center, xRad, zRad)

                    cancel()
                    return
                }

                changeFloor(center, xRad, zRad)

                // Recursively call the method with the new interval
                activateChangeFloorTimerWithGrowingFrequency(center, interval - 1, stopInterval, xRad, zRad)
            }
        }.runTaskLater(plugin, interval.toLong())
    }


    /**
     * Changes the floor to random materials. Needs a center location and the x and z radius of the floor since it doesn't know physically what floor we are talking about.
     * @param center The center of the floor
     * @param xLengthRad The x radius of the floor
     * @param zLengthRad The z radius of the floor
     */
    fun changeFloor(center: Location, xLengthRad: Int, zLengthRad: Int) {
        val blockTypeRandomizer = Random()

        //Bukkit.broadcastMessage("floor changed");
        val blockTypes = DiscoMayhemConst.FloorLogic.DEFAULT_FLOOR_BLOCK_TYPES

        // Change the floor under the player to random materials. The floor is a rectangle with side lengths 2*xLengthRad+1 and 2*zLengthRad+1. Goes over 1 block at a time.
        for (x in -xLengthRad..xLengthRad) {
            for (z in -zLengthRad..zLengthRad) {
                val material = blockTypeRandomizer.nextInt(blockTypes.size)
                val selectedLocation =
                    Location(DiscoMayhemConst.WORLD, center.x + x, center.y, center.z + z)
                selectedLocation.block.type = blockTypes[material]
            }
        }
    }


    /**
     * Decreases the interval for changing the floor as time goes on. The interval is decreased by 2 every a certain amount of time seconds.
     * The interval is decreased for both the starting interval and the interval at which the recursion stops. This is true for both the upper and lower bounds.
     *
     * This is done to make the game more difficult as time goes on.
     *
     * When the game ends, the intervalTask is immediately canceled. endGame() method takes care of canceling the task.
     */
    private var intervalTask: BukkitTask?
        get() = null
        set(value) = TODO()

    private fun decreaseStartingIntervalForChangingFloorTimer() {
        if (!isAlreadyRunning() || isAlreadyPaused()) {
            return
        }

        // Decrease the interval for changing the floor as time goes on. The interval is decreased by 2 every a certain amount of time seconds.
        intervalTask = object : BukkitRunnable() {
            override fun run() {
                if (!isAlreadyRunning() || isAlreadyPaused()) {
                    cancel()
                    return
                }

                if (upperBound__startingIntervalForChangingFloor == DiscoMayhemConst.MIN_INTERVAL) {
                    Bukkit.broadcastMessage(ChatColor.RED.toString() + "The interval for changing the floor has reached the minimum value.")
                    cancel()
                    return
                }

                upperBound__startingIntervalForChangingFloor =
                    max(upperBound__startingIntervalForChangingFloor - 2, DiscoMayhemConst.MIN_INTERVAL)
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE.toString() + "upperBound__startingIntervalForChangingFloor: " + upperBound__startingIntervalForChangingFloor)
                lowerBound__startingIntervalForChangingFloor =
                    max(lowerBound__startingIntervalForChangingFloor - 2, DiscoMayhemConst.MIN_INTERVAL)
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE.toString() + "lowerBound__startingIntervalForChangingFloor: " + lowerBound__startingIntervalForChangingFloor)
                upperBound__stopChangingFloorInterval =
                    max(upperBound__stopChangingFloorInterval - 2, DiscoMayhemConst.MIN_INTERVAL)
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE.toString() + "upperBound__stopChangingFloorInterval: " + upperBound__stopChangingFloorInterval)
                lowerBound__stopChangingFloorInterval =
                    max(lowerBound__stopChangingFloorInterval - 2, DiscoMayhemConst.MIN_INTERVAL)
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE.toString() + "lowerBound__stopChangingFloorInterval: " + lowerBound__stopChangingFloorInterval)
            }
        }.runTaskTimer(
            plugin,
            DiscoMayhemConst.FloorLogic.ChangingFloor.DELAY_TO_DECREASE_INTERVAL.toLong(),
            DiscoMayhemConst.FloorLogic.ChangingFloor.DELAY_TO_DECREASE_INTERVAL.toLong()
        )
    }

    /**
     * Removes the floor except for a chosen material.
     * After that the method automatically takes care of the remaining parts of the floor, and it deletes them later, after a specified amount of time.
     * The player has a limited time to go from the old floor to the new floor.
     * @param center The center of the floor
     * @param xLengthRad The x radius of the floor
     * @param zLengthRad The z radius of the floor
     * @param materialToKeep The material to keep
     */
    fun removeFloorExceptForChosenMaterial(
        center: Location,
        xLengthRad: Int,
        zLengthRad: Int,
        materialToKeep: Material?
    ) {
        if (!isAlreadyRunning() || isAlreadyPaused()) {
            return
        }

        Bukkit.broadcastMessage("floor removal")

        // Take the current floor and remove all the materials except for the materialToKeep. Go through 1 block at a time. The size of the floor is 2*xLengthRad+1 and 2*zLengthRad+1.
        for (x in -xLengthRad..xLengthRad) {
            for (z in -zLengthRad..zLengthRad) {
                val selectedLocation =
                    Location(DiscoMayhemConst.WORLD, center.x + x, center.y, center.z + z)

                // Only change the block if it is not the material to keep
                if (selectedLocation.block.type != materialToKeep) selectedLocation.block.type = Material.AIR
            }
        }

        // At this stage, a new floor is set elsewhere. The player will have a limited time to go from the old floor to the new floor. The timer and the logic
        // can be seen in the bukkit runnable below.
        preppingForAFloorCycle(center)

        // Remove the remaining parts of the floor after a certain amount of time. This is the time the player has to go from the old floor to the new floor.
        //fixme: if the new floor is too close to the old one, this runnable will remove blocks from the new floor that their material is the same
        // as the old chosen material from , if they are in the bounds of the old floor.
        object : BukkitRunnable() {
            override fun run() {
                if (!isAlreadyRunning() || isAlreadyPaused()) {
                    cancel()
                    return
                }

                // Go over the material that isn't deleted and remove it as well.
                for (x in -xLengthRad..xLengthRad) {
                    for (z in -zLengthRad..zLengthRad) {
                        val selectedLocation =
                            Location(DiscoMayhemConst.WORLD, center.x + x, center.y, center.z + z)

                        // Remove the selected Material
                        if (selectedLocation.block.type == materialToKeep) selectedLocation.block.type = Material.AIR
                    }
                }
                cancel()
            }
        }.runTaskLater(
            plugin,
            DiscoMayhemConst.FloorLogic.DURATION_OF_STAYING_IN_A_FLOOR_WITH_ONLY_CHOSEN_MATERIAL.toLong()
        )
    }

    /**
     * Chooses a material for the floor. The material is chosen randomly from a list of materials.
     * The material is given to all players in their 5th hotbar slot.
     * After a certain amount of time, the floor is removed except for the chosen material.
     * @param center The center of the floor
     * @param xRad The x radius of the floor
     * @param zRad The z radius of the floor
     */
    private fun chooseFloorBlockType(center: Location, xRad: Int, zRad: Int) {
        val blockTypeRandomizer = Random()
        val floorBlockTypes = DiscoMayhemConst.FloorLogic.DEFAULT_FLOOR_BLOCK_TYPES

        val material =
            floorBlockTypes[blockTypeRandomizer.nextInt(floorBlockTypes.size)] // get a random material from the list of floor block types
        Bukkit.getServer()
            .broadcast(Component.text(ChatColor.RED.toString() + "floor type chosen: " + material.toString()))
        // Give the material to all players in their 5th hotbar slot and send a title to all players of the chosen block type.
        for (player in Bukkit.getOnlinePlayers()) {
            player.inventory.setItem(4, ItemStack(material))

            // Send a title to the player with the chosen material with a color that corresponds to the material.
            val title = Title.title(
                Component.empty(),
                Component.text(material.toString()).color(getColorOfMaterial(material)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))
            )
            player.showTitle(title)
        }


        //TODO: as the game progresses, the time to remove the floor should be shortened.

        // Remove all the floor except for the chosen material. The time given is the time to remove the floor. Overtime this will be shortened as the game progresses and gets more difficult.
        object : BukkitRunnable() {
            override fun run() {
                removeFloorExceptForChosenMaterial(center, xRad, zRad, material)

                //remove the material from the players' hotbar, so it won't confuse them.
                for (player in Bukkit.getOnlinePlayers()) {
                    player.inventory.clear(4)
                }

                cancel()
            }
        }.runTaskLater(plugin, DiscoMayhemConst.FloorLogic.DELAY_TO_SELECT_A_FLOOR_MATERIAL.toLong())
    }

    companion object {
        /**
         * gives the color equivalent of a material (works for wool blocks).
         * @return The chosen material
         */
        fun getColorOfMaterial(material: Material): TextColor {
            return when (material) {
                Material.RED_WOOL -> NamedTextColor.RED
                Material.BLUE_WOOL -> NamedTextColor.BLUE
                Material.LIME_WOOL -> NamedTextColor.GREEN
                Material.PURPLE_WOOL -> NamedTextColor.DARK_PURPLE
                Material.ORANGE_WOOL -> NamedTextColor.GOLD
                Material.YELLOW_WOOL -> NamedTextColor.YELLOW
                Material.GREEN_WOOL -> NamedTextColor.DARK_GREEN
                Material.CYAN_WOOL -> NamedTextColor.DARK_AQUA
                Material.LIGHT_BLUE_WOOL -> NamedTextColor.AQUA
                else -> NamedTextColor.WHITE
            }
        }

        /**
         * Formats a location to a string.
         * @param location The location to format
         * @return The formatted location
         */
        private fun formatLocation(location: Location): String {
            return location.getWorld()
                .name + ". (" + location.x + "," + location.y + "," + location.z + ")"
        }
    }
}