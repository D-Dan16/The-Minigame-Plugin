package base.minigames.blueprint_bazaar

import base.MinigamePlugin.Companion.plugin
import base.minigames.blueprint_bazaar.BPBConst.WORLD
import base.utils.extensions_for_classes.*
import com.sk89q.worldedit.regions.CuboidRegion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Bisected
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.*
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.BoundingBox
import org.bukkit.util.VoxelShape
import java.time.Duration
import kotlin.math.abs
import kotlin.math.floor

/** *
 * Represents a build in the Blueprint Bazaar minigame.
 * It tracks how complete the build is at.
 * This Object also holds data related to the build and used to manage a build's logic within itself
 */
class BuildBlueprint(
    val game: BlueprintBazaar,
    val regionOfBuildDisplayed: CuboidRegion,
) : Listener {

    //region Properties
    val materialList: MutableSet<Material> = mutableSetOf() // List of materials used in the build
    val region: CuboidRegion

    var numOfBlocksBuildDisplayedHas = 0
    var correctNumOfBlocksInRegion: Int = 0
        set(blocks) {
            // if we have placed a block in the region, the message will be of the color green.
            // if we have removed a block from the region, the message will be of the color red
            val color: NamedTextColor = when {
                blocks > field -> NamedTextColor.GREEN
                blocks < field -> NamedTextColor.RED
                else -> NamedTextColor.YELLOW
            }

            field = 0.coerceAtLeast(blocks)
            completionPercentage = floor((field / numOfBlocksBuildDisplayedHas).toDouble() * 100)
            val title = Title.title(
                Component.empty(),
                Component.text("${field}/${numOfBlocksBuildDisplayedHas}").color(color),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofMillis(100))
            )

            game.players.forEach { player ->
                player.showTitle(title)
            }
        }

    // List of blocks that are placed but not yet correct
    var listOfBlocksToKeepTrackOf: MutableList<Block> = mutableListOf()

    var completionPercentage: Double = 0.0


    var curBuildTime = 0

    var timeElapsedRunnable: BukkitTask
    val orientationBlocksTracker: BukkitRunnable
    //endregion

    init {
        //region -- Initialize the material list with the materials used in the build
        for (block in regionOfBuildDisplayed) {
            // get the material of the block and compare it to existing materials

            // If the block is not air and not already in the material list, add it
            val blockMaterial = WORLD.getBlockAt(
                block.x,
                block.y,
                block.z
            ).type

            if (blockMaterial != Material.AIR && !materialList.contains(blockMaterial)) {

                // Now, before we add the material, we want to see if this material can be crafted from a more basic material
                getRawMaterialsFromThisMaterial(blockMaterial)

            }
        }
        //endregion

        //region The region of the build that is being replicated.
        region = CuboidRegion(
            regionOfBuildDisplayed.minimumPoint + BPBConst.Locations.CENTER_BUILD_PLOT_OFFSET,
            regionOfBuildDisplayed.maximumPoint + BPBConst.Locations.CENTER_BUILD_PLOT_OFFSET
        )
        //endregion


        //region calc the total number of blocks the build to copy from has.
        regionOfBuildDisplayed.forEach { vector ->
            val block = WORLD.getBlockAt(vector)

            if (block.type != Material.AIR) {
                // we don't want to count multiple times blocks that occupy more than 1 block, such as doors,so let's check if the block is bisected.
                if (block.blockData is Bisected) {
                    val bisected = block.blockData as Bisected
                    if (bisected.half == Bisected.Half.TOP) {
                        // Skip the top half, we'll only count the bottom half
                        return@forEach
                    }
                }

                numOfBlocksBuildDisplayedHas++
            }
        }
        //endregion

        //region A timer that tracks the time elapsed for the current build. gets called every second. it gets canceled when the build is completed.
        timeElapsedRunnable = object : BukkitRunnable() {
            override fun run() {
                game.players.forEach { player ->
                    curBuildTime++
                    player.sendActionBar(Component
                        .text("Time Elapsed: $curBuildTime seconds")
                        .color(NamedTextColor.AQUA)
                    )
                }

            }
        }.runTaskTimer(plugin,20L,20L)
        //endregion

        //region checker for blocks that are being tracked are oriented correctly.
        orientationBlocksTracker = object : BukkitRunnable() {
            override fun run() {
                // check if the blocks that are being tracked are oriented correctly
                listOfBlocksToKeepTrackOf.removeIf { block ->
                    val comparedBlock = getComparedBlockAt(block)

                    if (compareShape(block, comparedBlock)) {
                        incrementCorrectNumOfBlocksInRegion()
                        true // remove the block from the list
                    } else {
                        false // keep the block in the list
                    }
                }
            }
        }
        game.runnables += orientationBlocksTracker

        orientationBlocksTracker.runTaskTimer(plugin, 0L, 8L)
        //endregion
    }

    private fun getRawMaterialsFromThisMaterial(blockMaterial: Material,hasVisitedThisMaterial: MutableSet<Material> = mutableSetOf()) {

        // if we have reached a basic material, return that.
        if (BPBConst.BasicMaterials.contains(blockMaterial)) {
            materialList += blockMaterial
            return
        // Special case: if the material is a striped log, we want to get the base log material. (since striped logs don't have a crafting recipe)
        } else if (blockMaterial in BPBConst.StripedLogsToLogs.keys) {
            materialList += BPBConst.StripedLogsToLogs[blockMaterial]!!
            return
        }

        // list all recipes that yield to this material
        val recipes: Set<Recipe> = Bukkit.recipeIterator().asSequence()
            .filter { recipe -> recipe.result.type == blockMaterial }
            .toSet()


        // put into here all the ingredients that help towards making said material
        val potentialMaterialsToInclude: MutableSet<Material> = mutableSetOf()

        for (recipe in recipes) {
            potentialMaterialsToInclude += recipe.ingredients()
        }

        // if we managed to get into a material that can't be crafted from anything at all, then it can NOT be a basic material *which* is from *our* basic material list.
        if (potentialMaterialsToInclude.none()) return

        if (hasVisitedThisMaterial.containsAll(potentialMaterialsToInclude.toSet())) {
            return
        }

        //tick into the hasVisited set this material, so we won't get into infinite loops
        for (potentialMaterial in potentialMaterialsToInclude) {
            hasVisitedThisMaterial.add(potentialMaterial)
        }

        //now delve one step deeper and search for that material's ancestors...
        for (potentialMaterial in potentialMaterialsToInclude) {
            getRawMaterialsFromThisMaterial(potentialMaterial, hasVisitedThisMaterial)
        }

    }

    /**
     * Get the ingredients as Materials that this recipe includes.
     * Only checks for Crafting Recipes and Smelting Recipes.
     */
    fun Recipe.ingredients() : MutableSet<Material> {
        val setOfMaterials = mutableSetOf<Material>()

        when (this) {
            is ShapedRecipe, is ShapelessRecipe -> {
                val choices: Collection<RecipeChoice> = when (this) {
                    is ShapedRecipe -> this.choiceMap.values
                    is ShapelessRecipe -> this.choiceList
                    else -> listOf()
                }

                val matChoices: List<RecipeChoice.MaterialChoice> = choices.filterIsInstance<RecipeChoice.MaterialChoice>()

                val materials: Set<Material> = matChoices.flatMap {
                    matChoice -> matChoice.choices
                }.toSet()

                val basicMaterials = materials.filter { BPBConst.BasicMaterials.contains(it) }

                if (basicMaterials.isNotEmpty()) {
                    setOfMaterials.addAll(basicMaterials)
                } else {
                    // if we don't have any basic materials, then we will add the first material from each mat choice that is suggested.
                    setOfMaterials += matChoices.map { it.choices[0]}
                }
            }
            is FurnaceRecipe -> {
                val matChoice = this.inputChoice as RecipeChoice.MaterialChoice

                setOfMaterials.add(matChoice.choices[0])
            }

            else -> {/*nothing*/}
        }

        return setOfMaterials
    }


    private fun getComparedBlockAt(block: Block): Block {
        return WORLD.getBlockAt(block.toBlockVector3() - BPBConst.Locations.CENTER_BUILD_PLOT_OFFSET)
    }


    //region For comparing voxel shapes of blocks

    fun compareShape(blockA: Block, blockB: Block): Boolean {
        if (blockA.blockData == blockB.blockData) return true

        return voxelShapesEqual(blockA.collisionShape, blockB.collisionShape)
    }

    private fun voxelShapesEqual(shapeA: VoxelShape, shapeB: VoxelShape, epsilon: Double = 1e-6): Boolean {
        val boxesA: List<BoundingBox> = shapeA.boundingBoxes.sortedWith(
            compareBy({ it.minX }, { it.minY }, { it.minZ }, { it.maxX }, { it.maxY }, { it.maxZ })
        )
        val boxesB: List<BoundingBox> = shapeB.boundingBoxes.sortedWith(
            compareBy({ it.minX }, { it.minY }, { it.minZ }, { it.maxX }, { it.maxY }, { it.maxZ })
        )
        if (boxesA.size != boxesB.size) return false
        for (i: Int in boxesA.indices) {
            if (!boxesEqual(boxesA[i], boxesB[i], epsilon)) return false
        }
        return true
    }

    private fun boxesEqual(b1: BoundingBox, b2: BoundingBox, epsilon: Double): Boolean {
        fun almostEqual(a: Double, b: Double, epsilon: Double): Boolean {
            return abs(a - b) <= epsilon
        }

        return almostEqual(b1.minX, b2.minX, epsilon) &&
                almostEqual(b1.minY, b2.minY, epsilon) &&
                almostEqual(b1.minZ, b2.minZ, epsilon) &&
                almostEqual(b1.maxX, b2.maxX, epsilon) &&
                almostEqual(b1.maxY, b2.maxY, epsilon) &&
                almostEqual(b1.maxZ, b2.maxZ, epsilon)
    }
    //endregion

    private fun incrementCorrectNumOfBlocksInRegion(player: Player? = null) {
        correctNumOfBlocksInRegion++
        if (completionPercentage == 100.0) {
            prepareForCompletion(player)
        }
    }

    @EventHandler
    private fun onBlockPlaced(event: BlockPlaceEvent) {
        if (!game.isGameRunning || !game.isPlayerInGame(event.player)) return

        // Not allow block placing outside the designated plot.

        if (event.block.toBlockVector3() !in region) {
            event.isCancelled = true
            return
        }

        val block = event.block
        val comparedBlock = getComparedBlockAt(block)

        if (block.type == comparedBlock.type) {

            // if the block is oriented the same way as the block in the display build, then we will increase the correct number of blocks in the region.
            // otherwise, we will keep track of the block, but not increment the correct number of blocks in the region, until the block is oriented correctly.
            if (compareShape(block, comparedBlock))
                incrementCorrectNumOfBlocksInRegion(event.player)
            else {
                listOfBlocksToKeepTrackOf += block
            }
        }
    }

    @EventHandler
    private fun onBlockTouched(event: BlockDamageEvent) {
        if (!game.isGameRunning || !game.isPlayerInGame(event.player)) return

        // Not allow block breaking outside the designated plot.
        if (event.block.toBlockVector3() !in region) {
            event.isCancelled = true
            return
        }

        val block = event.block
        val comparedBlock = getComparedBlockAt(block)

        // remove block manually at a delay
        Bukkit.getServer().scheduler.runTaskLater(plugin, Runnable{
            // check if the block was related to the progression of the build.
            if (block.type == comparedBlock.type) {
                if (compareShape(block, comparedBlock))
                    correctNumOfBlocksInRegion--
                else
                    listOfBlocksToKeepTrackOf.remove(block)
            }

            block.world.dropItemNaturally(block.location, ItemStack(block.type))
            // remove the block
            block.type = Material.AIR
        },3L)
    }

    @EventHandler
    private fun onBlockBreak(event: BlockBreakEvent) {
        event.isCancelled = true
    }

    /**
     * Prepares the build for completion.
     *
     * This method stops the timer for the current build and shows a title to the player who completed the build if a player is designated as the finisher, otherwise it just completes the build.
     * Also clears the arena by removing items that are on the ground and unregisters the listener for this build.
     */
    fun prepareForCompletion(buildFinisher: Player? = null) {
        // let's first clear the arena by clearing items that are on the ground
        WORLD.removeItemsInRegion(BPBConst.Locations.ARENA_REGION)

        // stop the timer for the current build
        timeElapsedRunnable.cancel()

        // unregister the listener for this build
        HandlerList.unregisterAll(this)


        val successMessage = Component.text("Build has been completed in $curBuildTime seconds!")
            .color(NamedTextColor.GREEN)

        // if we have called this method with a player to designate as the finisher of the build, then we will show a title to that player and send a message to that player.
        if (buildFinisher != null) {
            // Show a title to all players the time elapsed for the build
            val title = Title.title(
                successMessage,
                Component.empty(),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1000), Duration.ofMillis(300))
            )

            buildFinisher.showTitle(title)
        }

        game.players.forEach { player ->
            player.sendMessage(successMessage)
        }

        game.completeBuild(this)
    }
}


