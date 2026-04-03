package base.minigames.hole_in_the_wall

import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import base.utils.other.BuildLoader
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockState
import org.bukkit.block.data.Powerable
import java.io.File
import org.bukkit.Bukkit

import base.utils.additions.Direction
import base.MinigamePlugin
import base.MinigamePlugin.Companion.plugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.block.data.type.Piston
import org.bukkit.block.data.type.Switch

class Wall(
    val wallFile: File,
    directionWallComesFrom: Direction, // The direction the wall is coming from. This is used to determine the direction the wall is facing and how it should be moved.
    val isFlipped: Boolean = false,
    var isPsych: Boolean = false,
    val shouldRemovePsychThatStopped: Boolean = true // If the psych wall continues to move after it has reached its lifespan and has stopped. This is only relevant for psych walls and is set to true by default for regular walls. (doesn't mean anything)
) {
    constructor(wallFile: File,directionWallComesFrom: Direction) : this(wallFile,directionWallComesFrom,false,false)


    //region -- Properties --

    var directionWallComesFrom: Direction = directionWallComesFrom
        set(direction) {
            field = direction

            // Update the direction the wall is facing based on the direction it comes from.
            directionWallIsFacing = when (direction) {
                Direction.SOUTH -> Direction.NORTH
                Direction.NORTH -> Direction.SOUTH
                Direction.WEST -> Direction.EAST
                Direction.EAST -> Direction.WEST
            }

            // Update the spawn location based on the new direction.
            spawnLocation = when (direction) {
                Direction.SOUTH -> HITWConst.Locations.SOUTH_WALL_SPAWN.clone()
                Direction.NORTH -> HITWConst.Locations.NORTH_WALL_SPAWN.clone()
                Direction.WEST -> HITWConst.Locations.WEST_WALL_SPAWN.clone()
                Direction.EAST -> HITWConst.Locations.EAST_WALL_SPAWN.clone()
            }

            // We will gather the schematic as a Clipboard from the wall file.
            // This is to easily and conveniently manipulate the schematic based on the characteristics of the wall.
            holder = BuildLoader.getClipboardHolderFromFile(wallFile, spawnLocation)

            // update the holder to reflect the new direction the wall is facing.
            BuildLoader.applyDirectionToClipboardHolder(holder, directionWallIsFacing)

            // Create the wall region based on the clipboard's dimensions.
            wallRegion = BuildLoader.getRotatedRegion(holder)
        }
    private lateinit var directionWallIsFacing: Direction
    private var spawnLocation: Location = when (directionWallComesFrom) {
        Direction.SOUTH -> HITWConst.Locations.SOUTH_WALL_SPAWN.clone()
        Direction.NORTH -> HITWConst.Locations.NORTH_WALL_SPAWN.clone()
        Direction.WEST -> HITWConst.Locations.WEST_WALL_SPAWN.clone()
        Direction.EAST -> HITWConst.Locations.EAST_WALL_SPAWN.clone()
    }


    lateinit var wallRegion: CuboidRegion
    lateinit var locationOfPistons: MutableList<Location>

    //How many blocks the wall travels before it stops moving.
    val initialLifespan =
        if (!isPsych) HITWConst.DEFAULT_WALL_TRAVEL_LIFESPAN
        else HITWConst.DEFAULT_PSYCH_WALL_TRAVEL_LIFESPAN

    var lifespanRemaining = initialLifespan
    var lifespanTraveled = 0

    val minimumLifespanTraveledWhereWallsCanSpawnBehindIt = HITWConst.MINIMUM_SPACE_BETWEEN_2_WALLS_FROM_THE_SAME_DIRECTION


    var shouldBeRemoved: Boolean = false // If the wall should be removed from the game when it has stopped moving.
    var shouldBeStopped: Boolean = false // If the wall should be stopped from moving. It doesn't mean it should be removed from the game, but it has the possibility (for example - Psych walls)

    var isBeingHandled: Boolean = false // a special value that serves to prevent executing logic on the wall if this is flagged. this can be general purpose, however it is heavily discouraged. its actual use is to prevent logic trying to be repeated on psych walls that are stopped.
    //endregion

    lateinit var holder: ClipboardHolder // This is the ClipboardHolder that will be used to hold the schematic of the wall.

    init {
        // Load the wall file and validate its contents if necessary
        if (wallFile.isDirectory) {
            throw IllegalArgumentException("Wall file cannot be a directory: ${wallFile.path}")
        }
        if (!wallFile.exists()) {
            throw IllegalArgumentException("Wall file does not exist: ${wallFile.path}")
        }

        // we will set the direction of the wall, and update the holder to reflect the direction the wall is facing.
        this.directionWallComesFrom = directionWallComesFrom

        // mirror the schematic if the wall is flipped.
        if (isFlipped) {
            BuildLoader.mirrorClipboardHolder(holder, directionWallIsFacing)
        }

        // -------------------------------------------------------------------------------------------- //
    }

    fun makeWallExist() {
        // Now we have the schematic ready to be pasted into the world.
        // after modifying the schematic, now we can finally paste the schematic into the world at the spawn location.
        BuildLoader.loadSchematic(holder)

        // Get the locations of all pistons in the wall region. important that this is done after the wall region is set (which it is only after loading the schem), since the method relies on the wall region to get the piston locations.
        locationOfPistons = getPistonLocations()
    }


    private fun getPistonLocations(): MutableList<Location> {
        // Get the locations of all piston blocks within the bounding box of the wall
        val locations = mutableListOf<Location>()

        for (x in wallRegion.minimumPoint.x..wallRegion.maximumPoint.x) {
            for (y in wallRegion.minimumPoint.y..wallRegion.maximumPoint.y) {
                for (z in wallRegion.minimumPoint.z..wallRegion.maximumPoint.z) {
                    val block = HITWConst.Locations.WORLD.getBlockAt(x, y, z)
                    // Only check blocks that are pistons
                    if (block.type == Material.PISTON) {
                        locations.add(
                            Location(
                                HITWConst.Locations.WORLD,
                                x.toDouble(),
                                y.toDouble(),
                                z.toDouble()
                            )
                        )
                    }
                }
            }
        }
        return locations
    }


    private fun updateLifespans() {
        lifespanRemaining--
        lifespanTraveled++
    }


    /**
     * Moves the wall in the specified direction by a singular block via activating the pistons.
     * Each wall has a lifespan, which is the number of blocks it can travel before it stops moving.
     * Each time the wall moves, its lifespan is decremented by 1.
     * If the wall has a lifespan of 0, it will be stopped, but not necessarily removed from the game. The game logic handles the logic for removing the wall.
     */
    fun move() {
        fun powerOnAndOffButton(block: Block) {
            val state: BlockState = block.state

            val powerableState: Powerable = state.blockData as Powerable

            // Change the state of the button to powered if it is not already powered
            if (!powerableState.isPowered) {
                powerableState.isPowered = true // Power the button
                state.blockData = powerableState
                state.update(true, true) // Update the block state

                // Now we turn off the button after a short delay of X ticks in order to simulate the button being pressed which activates the piston.


            }
        }

        // -------------------------------------------------------------------------------------------- //

        if (lifespanRemaining <= 0) {
            this.shouldBeStopped = true // If the wall has reached its lifespan, it should be stopped (it'll be determined by the game logic if it should be removed or continue living on for later).

            // We will not continue with the logic of moving the wall, since it has reached its lifespan.
            return
        }

        //region ----Moving Wall Logic - add and Press Buttons on Pistons---------------------------------------------------


        // We'll create a list of locations where the buttons will be placed. this will be used when we will want to eventually remove the buttons.
        val buttonLocations: MutableList<Location> = mutableListOf()

        // We'll iterate through the locations of all pistons. we'll add behind them a stone button and activate the buttons on their faces.
        locationOfPistons.forEach { loc ->
            // the direction the wall is facing is the same as the direction the piston is facing. calculate the button location based on the direction the wall is facing.
            val buttonLocation: Location = when (directionWallIsFacing) {
                Direction.SOUTH -> loc.clone().add(0.0, 0.0, -1.0)
                Direction.NORTH -> loc.clone().add(0.0, 0.0, 1.0)
                Direction.WEST -> loc.clone().add(1.0, 0.0, 0.0)
                Direction.EAST -> loc.clone().add(-1.0, 0.0, 0.0)
            }

            // Check if the block behind the piston is air, if it is not, then we can't place a button there.
            // this will typically happen if two walls have collided with each other.
            if (buttonLocation.block.type != Material.AIR) {
                val game = plugin.getInstanceOfMinigame(MinigamePlugin.Companion.MinigameType.HOLE_IN_THE_WALL) as HoleInTheWall

                //game.clearWalls()
                game.pauseGame()

                Bukkit.getServer().broadcast(Component.text("Two walls have seemed to collide. Cleaning the arena and pausing.").color(NamedTextColor.YELLOW))
            }

            // Update the button location to the list of button locations.
            buttonLocations.add(buttonLocation)

            // Get the block behind the piston where we will place the button.
            val buttonBlock: Block = buttonLocation.block
            buttonBlock.type = Material.STONE_BUTTON

            // now we need the button to lay flat against the piston, so we need to set the block data of the button to face *against* the piston.
            val data = buttonBlock.blockData as Switch

            data.facing = when (directionWallIsFacing) {
                Direction.SOUTH -> BlockFace.NORTH
                Direction.NORTH -> BlockFace.SOUTH
                Direction.WEST -> BlockFace.EAST
                Direction.EAST -> BlockFace.WEST
            }
            // set the direction of the button to face the piston.
            buttonBlock.blockData = data

            // Now we can power the button to activate the piston.
            powerOnAndOffButton(buttonBlock)
        }
        //endregion


        // IMPORTANT: We need to let the pistons extend before we move the wall region, so we will wait for a lil before excecuting the entire logic of this function..

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
        // region ---Update the region of the wall based on the wall direction, since in the physical world, the slime wall has moved.

        //shift the wall region in the direction it is facing by 1 block.
        when (directionWallIsFacing) {
            Direction.SOUTH -> wallRegion.shift(BlockVector3.at(0, 0, 1))
            Direction.NORTH -> wallRegion.shift(BlockVector3.at(0, 0, -1))
            Direction.WEST -> wallRegion.shift(BlockVector3.at(-1, 0, 0))
            Direction.EAST -> wallRegion.shift(BlockVector3.at(1, 0, 0))
        }
        //endregion

        //region --- Update the Pistons' location so that they match the new wall location and aren't left behind.


        // First things first, we want to remove the buttons that were placed behind the pistons, since if we will move the pistons, the buttons will be dropped as items.
        buttonLocations.forEach { location ->
            location.block.type = Material.AIR
        }


        locationOfPistons.forEach { location ->
            // First we need to remove the pistons from their current locations, so that they can be moved to their new locations.
            location.block.type = Material.AIR

            //then we need to update the location of the piston in the list so that it matches the new wall location.
            when (directionWallIsFacing) {
                Direction.SOUTH -> location.add(0.0, 0.0, 1.0)
                Direction.NORTH -> location.add(0.0, 0.0, -1.0)
                Direction.WEST -> location.add(-1.0, 0.0, 0.0)
                Direction.EAST -> location.add(1.0, 0.0, 0.0)
            }

            // If the lifespan is greater than 0, we will move the pistons to their new locations. this is to ensure that no weird scenarios happen - such as pistons being left behind when the wall is being deleted. (recall this method is inside a BukkitRunnable, so it is delayed and independent of the main thread actions).
            if (lifespanRemaining > 0) {
                // Now we physically move the pistons to their new locations.
                location.block.type = Material.PISTON
                // Set the piston block data to face the direction the wall is facing.
                val pistonData = location.block.blockData as Piston
                pistonData.facing = when (directionWallIsFacing) {
                    Direction.SOUTH -> BlockFace.SOUTH
                    Direction.NORTH -> BlockFace.NORTH
                    Direction.WEST -> BlockFace.WEST
                    Direction.EAST -> BlockFace.EAST
                }

                location.block.blockData = pistonData
            }
        }
        //endregion

        updateLifespans()

        } , 2L)
    }

    fun showBlocks() {
        fun putBlock(location: Location) {
            location.block.type = Material.DIAMOND_BLOCK
        }

        val min = Location(
            HITWConst.Locations.WORLD,
            wallRegion.minimumPoint.x.toDouble(),
            wallRegion.minimumPoint.y.toDouble(),
            wallRegion.minimumPoint.z.toDouble()
        )
        val max = Location(
            HITWConst.Locations.WORLD,
            wallRegion.maximumPoint.x.toDouble(),
            wallRegion.maximumPoint.y.toDouble(),
            wallRegion.maximumPoint.z.toDouble()
        )

        putBlock(min)
        putBlock(max)
    }
}
