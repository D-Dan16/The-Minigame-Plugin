package base.minigames.maze_hunt

import base.MinigamePlugin
import base.annotations.CalledByCommand
import base.annotations.ShouldBeReset
import base.minigames.MinigameSkeleton
import base.minigames.maze_hunt.MHConst.Locations.MAZE_ORIGIN
import base.minigames.maze_hunt.MHConst.Locations.WORLD
import org.bukkit.Location
import org.bukkit.entity.Player
import base.minigames.maze_hunt.MHConst.Locations
import base.minigames.maze_hunt.MHConst.MazeGen
import base.minigames.maze_hunt.MHConst.Spawns.Mobs
import base.minigames.maze_hunt.MHConst.MazeGen.BIT_SIZE
import base.minigames.maze_hunt.MHConst.MazeGen.MAZE_DIMENSION_X
import base.minigames.maze_hunt.MHConst.MazeGen.MAZE_DIMENSION_Z
import base.utils.additions.Direction
import base.utils.extensions_for_classes.getBlockAt
import org.bukkit.Material
import org.jetbrains.kotlinx.multik.api.d2array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import base.minigames.maze_hunt.MHConst.BitPoint
import base.minigames.maze_hunt.MHConst.Locations.Y_LEVEL_FOR_CRATE_OFFSET
import base.minigames.maze_hunt.MHConst.MazeGen.REGENERATE_MAZE_INITIAL_COOLDOWN_FOR_HARD_MODE
import base.minigames.maze_hunt.MHConst.Spawns.LootCrates.INITIAL_AMOUNTS_OF_CRATES_TO_SPAWN_IN_A_CYCLE_FOR_HARD_MODE
import base.minigames.maze_hunt.MHConst.Spawns.LootCrates.LootCrateType.*
import base.minigames.maze_hunt.MHConst.Spawns.LootCrates.applyRandomDurability
import base.minigames.maze_hunt.MHConst.Spawns.Mobs.INITIAL_AMOUNTS_OF_MOBS_TO_SPAWN_IN_A_CYCLE_FOR_HARD_MODE
import base.minigames.maze_hunt.MHConst.Spawns.Mobs.MOBS_BEING_PASSIVE_DURATION
import base.utils.extensions_for_classes.modifyDuraBy
import base.resources.Colors
import base.utils.additions.PausableBukkitRunnable
import base.utils.extensions_for_classes.getWeightedRandom
import base.utils.additions.Utils.initFloor
import base.utils.additions.Utils.successChance
import base.utils.additions.activateChain
import base.utils.additions.delayTheFollowing
import base.utils.extensions_for_classes.breakGradually
import base.utils.extensions_for_classes.fullyContains
import base.utils.extensions_for_classes.getItemStackByMaterial
import base.utils.extensions_for_classes.hasDurability
import base.utils.extensions_for_classes.remainingDurability
import base.utils.extensions_for_classes.returnQuantityOfEach
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.GameRule.DO_DAYLIGHT_CYCLE
import org.bukkit.entity.Creeper
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.MagmaCube
import org.bukkit.entity.Mob
import org.bukkit.entity.Slime
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.Team
import kotlin.collections.plusAssign
import kotlin.jvm.java

class MazeHunt(val plugin: Plugin) : MinigameSkeleton() , Listener {
    override val minigameName: String = this::class.simpleName ?: "Unknown"
    /** this set keeps track of all the indices of the bits that have been generated */
    @ShouldBeReset
    private val generatedBitsIndexes: MutableSet<BitPoint> = mutableSetOf()


    /** Number of mobs to spawn every mob spawning cycle. Gets increased as time goes on. */
    @ShouldBeReset
    private var amountOfMobsToSpawnPerInterval: Int = Mobs.INITIAL_AMOUNTS_OF_MOBS_TO_SPAWN_IN_A_CYCLE

    /** Number of Loot Crates to spawn every crate spawning cycle. Gets increased as time goes on. */
    @ShouldBeReset
    private var amountOfCratesToSpawnPerInterval: Int = MHConst.Spawns.LootCrates.INITIAL_AMOUNTS_OF_CRATES_TO_SPAWN_IN_A_CYCLE

    @ShouldBeReset
    var curTimeLeftTillNewMaze = MazeGen.REGENERATE_MAZE_INITIAL_COOLDOWN

    @CalledByCommand
    override fun start(sender: Player) {
        try {
            super.start(sender)

            pausableRunnables += PausableBukkitRunnable(plugin as JavaPlugin, remainingTicks = MHConst.STARTING_PLATFORM_LIFESPAN) {
                startGameLoop()
            }.apply { this.start() }

            // keep track of if players have fallen of the arena based on their Y level and kill them because they can cheat fall damage via slow-falling potions
            runnables += object : BukkitRunnable() {
                override fun run() {
                    players.forEach {
                        if (it.location.y < Locations.MIN_LEGAL_Y_LEVEL && it.gameMode !in listOf(GameMode.CREATIVE, GameMode.SPECTATOR))
                            it.health = 0.0
                    }
                }
            }.apply { runTaskTimer(MinigamePlugin.Companion.plugin, 0L, 10L) }
        } catch (e: InterruptedException) {
            pauseGame()
            throw e
        }
    }

    override fun startFastMode(player: Player) {
        amountOfMobsToSpawnPerInterval = INITIAL_AMOUNTS_OF_MOBS_TO_SPAWN_IN_A_CYCLE_FOR_HARD_MODE
        amountOfCratesToSpawnPerInterval = INITIAL_AMOUNTS_OF_CRATES_TO_SPAWN_IN_A_CYCLE_FOR_HARD_MODE
        curTimeLeftTillNewMaze = REGENERATE_MAZE_INITIAL_COOLDOWN_FOR_HARD_MODE

        super.startFastMode(player)
    }

    private fun startGameLoop() {
        //region new maze generation timer logic
        countDownToNewMazeGeneration(curTimeLeftTillNewMaze)

        val teamForTimeRemainingTillSwap: Team = scoreboard.getTeam("timeRemainingTillNewMaze") ?: scoreboard.registerNewTeam("timeRemainingTillNewMaze").apply {
            addEntry("Time Remaining Till New Maze: ")
            suffix(text(curTimeLeftTillNewMaze/20))
        }

        scoreboardObjective.getScore("Time Remaining Till New Maze: ").score = 13

        // Keep track of the timer for the length of the game and display it in the scoreboard
        pausableRunnables += PausableBukkitRunnable(plugin as JavaPlugin, remainingTicks = 20L, periodTicks = 20L) {
            curTimeLeftTillNewMaze -= 20L
            teamForTimeRemainingTillSwap.suffix(text(curTimeLeftTillNewMaze/20))
        }.apply { this.start() }

        //endregion

        //region Start Spawning Loot Crates
        //fixme: loot crates that are from previous instance of game, when those crates' lifespans are over, they'll also set the block to air in the current instance
        pausableRunnables += PausableBukkitRunnable(plugin, periodTicks = Mobs.SPAWN_CYCLE_DELAY) {
            repeat(amountOfCratesToSpawnPerInterval) {
                if (generatedBitsIndexes.isEmpty())
                    return@repeat

                val chosenCrateType = entries.random()

                val chosenLocationToSpawnAt: Location = generatedBitsIndexes.random().let {
                    getBitLocation(it.x, it.z)
                }.apply {
                    x += (-MazeGen.BIT_RADIUS..MazeGen.BIT_RADIUS).random()
                    y += Y_LEVEL_FOR_CRATE_OFFSET
                    z += (-MazeGen.BIT_RADIUS..MazeGen.BIT_RADIUS).random()
                }

                var blockAt = WORLD.getBlockAt(chosenLocationToSpawnAt)

                blockAt.type = chosenCrateType.material

                // We store metadata of the crate type so that only blocks with this metadata will drop loot when broken, and not just every block that has been broken.
                // The onLootCrateBreak will listen to the block break and check the metadata of the block.
                blockAt.setMetadata("isALootCrate", FixedMetadataValue(plugin, chosenCrateType.name))


                // ~ the lifespan of the loot crate ~
                blockAt.breakGradually(MHConst.Spawns.LootCrates.LIFESPAN)
            }
        }.apply { this.start() }

        // Gradually increase the number of crates spawned per interval
        pausableRunnables += PausableBukkitRunnable(plugin, periodTicks = MHConst.Spawns.LootCrates.NUM_OF_SPAWNS_INCREASER_TIMER
        ) {
            amountOfCratesToSpawnPerInterval += 1
        }.apply { this.start() }

        //endregion

        //region Start spawning mobs
        pausableRunnables += PausableBukkitRunnable(plugin, periodTicks = Mobs.SPAWN_CYCLE_DELAY) {
            repeat(amountOfMobsToSpawnPerInterval) {
                if (generatedBitsIndexes.isEmpty())
                    return@repeat

                val chosenMobToSpawn = Mobs.ALLOWED_MOB_TYPES.getWeightedRandom()

                val chosenLocationToSpawnAt: Location = generatedBitsIndexes.random().let {
                    getBitLocation(it.x, it.z)
                }.apply { y += 1 }

                val mob: Mob = (WORLD.spawnEntity(chosenLocationToSpawnAt, chosenMobToSpawn) as Mob).apply { isAware = false }

                if (mob is Creeper) mob.setAI(false)

                MOBS_BEING_PASSIVE_DURATION delayTheFollowing {
                    mob.isAware = true

                    if (mob is Creeper) mob.setAI(true)
                }

                //special case for mobs that are aggressive on contact
                if (mob is Slime || mob is MagmaCube) {
                    mobsToDisableContactDamage += mob
                    MOBS_BEING_PASSIVE_DURATION delayTheFollowing {
                        mobsToDisableContactDamage -= mob
                    }
                }
            }

            players.forEach {
                it.sendActionBar(text("New mob wave",TextColor.fromHexString(Colors.TitleColors.AQUA)))
            }

        }.apply { this.start() }


        // Gradually increase the number of mobs spawned per interval
        pausableRunnables += PausableBukkitRunnable(plugin, periodTicks = Mobs.NUM_OF_SPAWNS_INCREASER_TIMER) {
            amountOfMobsToSpawnPerInterval += 1
        }.apply { this.start() }

        //endregion
    }

    /**
     * List used for newly created mobs that trigger on contact with a player.
     * Each mob in this list will not trigger their action while on there.
     * Should include: Slimes, Magma Cubes
     */
    private val mobsToDisableContactDamage: MutableList<Mob> = mutableListOf()

    @EventHandler
    fun ignoreContactDamageOfSlimes(event: EntityDamageByEntityEvent) {
        if (event.damager in mobsToDisableContactDamage) {
            event.isCancelled = true
        }
    }

    private fun countDownToNewMazeGeneration(startedWaitTime: Long) {
        val chainElements = listOf(
            PausableBukkitRunnable(plugin as JavaPlugin, remainingTicks = startedWaitTime - 10 * 20L) {
                announceMessage(
                    "10s until Maze layout change!",
                    "be careful",
                    Colors.TitleColors.ORANGE,
                    duration = 1000L
                )
            },
            PausableBukkitRunnable(plugin, remainingTicks = 5 * 20L) {
                announceMessage(
                    "5s",
                    color = Colors.TitleColors.ORANGE,
                    duration = 1000L
                )
            },
            PausableBukkitRunnable(plugin, remainingTicks = 5 * 20L) {
                prepareArea()
                players.forEach {
                    it.teleport(Locations.PLAYERS_START_LOCATION)

                    // we clear potion effects since this is an easy way to nerf the busted potions that last forever and help so much (such as slow falling)
                    it.clearActivePotionEffects()
                }

                announceMessage(
                    "Maze SWAP!",
                    "All potion effects have been cleared",
                    Colors.TitleColors.AQUA,
                )


                WORLD.entities
                    .filter { it is LivingEntity && it !is Player }
                    .forEach { it.remove() }

                // kill any mobs that don't die from fall damage - such as blazes and slime
//                40L delayTheFollowing {
//                    WORLD.entities
//                        .filter { it is LivingEntity && it !is Player }
//                        .forEach { it.remove() }
//                }

                curTimeLeftTillNewMaze = startedWaitTime + MazeGen.INCREASE_IN_DURATION_FOR_MAZE_GENERATION
                countDownToNewMazeGeneration(curTimeLeftTillNewMaze)
            }
        )

        chainElements.activateChain(stopCondition = {!guardAlreadyRunning})
    }

    @EventHandler
    private fun onBlockPlace(event: BlockPlaceEvent) {
        val block = event.block

        // Do special action for tnt specifically and ignite the block instead of breaking it
        if (block.type == Material.TNT) {
            block.type = Material.AIR

            val tnt = WORLD.spawn(
                block.location.add(0.5, 0.0, 0.5),
                TNTPrimed::class.java
            )
            tnt.fuseTicks = 4 * 20

            return
        }

        val decayLifespan: Long = when (block.type) {
            Material.BRICKS -> MHConst.Spawns.LootCrates.BRICK_LIFESPAN
            Material.COBWEB -> MHConst.Spawns.LootCrates.COBWEB_LIFESPAN
            else -> {0L}
        }

        if (decayLifespan == 0L) return

        block.breakGradually(decayLifespan)
    }

    @EventHandler
    private fun onLootCrateBreak(event: BlockBreakEvent) {
        if (event.block.hasMetadata("isALootCrate").not())
            return

        val metaList = event.block.getMetadata("isALootCrate")

        val typeName = metaList.first { it.owningPlugin == plugin }.asString()

        // convert string back to enum
        val crateType = valueOf(typeName)

        // Now you have the enum instance and can access its pool.

        // Poll n number of rolls from the pool
        val itemsInside: List<ItemStack> = List(crateType.rolls.random()) { crateType.lootTable.getWeightedRandom() }

        val quantityOfEachItem = itemsInside.returnQuantityOfEach()

        // Combine all the same items into 1 item -
        // Let's say I have 3 ItemStack, each containing 4 apples -> return 1 ItemStack of 12 apples
        // Let's also say I have 2 ItemStacks of an Iron Helmet -> return 1 itemstack of the helmet where its durability is the combined durability of both helmets.
        val condensedItems: List<ItemStack> = quantityOfEachItem.map { collectionOfSameItemStack ->
            val itemStack = collectionOfSameItemStack.key
            val amountOfAllCopies = collectionOfSameItemStack.value * itemStack.amount

            if (itemStack.hasDurability()) {
                collectionOfSameItemStack.toPair().applyRandomDurability()
            } else {
                itemStack.clone().apply { amount = amountOfAllCopies }
            }
        }


        // add to the player the loot from the loot crate
        for (itemToAdd in condensedItems) {
            val inventory = event.player.inventory


            //Look for existing equipment and combine the existing equipment's dura with the added equipment's dura

            //if the item doesn't already exist in the player's inventory, just add the itemstack in
            if (!inventory.fullyContains(itemToAdd.type)) {
                inventory.addItem(itemToAdd)
            // otherwise, let's combine the added item to the existing one - combine the amounts or the durability
            } else {
                val itemStackAlreadyIn: ItemStack = inventory.getItemStackByMaterial(itemToAdd.type)!!

                //check if the item has dura or not
                if (itemStackAlreadyIn.hasDurability())  {
                    itemStackAlreadyIn modifyDuraBy itemToAdd.remainingDurability!!
                } else {
                    itemStackAlreadyIn.apply {
                        this.amount += itemToAdd.amount
                    }
                }
            }

        }

        // We'll delete the metadata to not accidentally think later on that there's still loot to obtain from a place where there isn't a loot crate
        event.block.removeMetadata("isALootCrate",plugin)

        // disable the block drop so that the physical block won't be used/exploited
        event.isDropItems = false
    }

    @EventHandler
    private fun onMobDeath(event: EntityDeathEvent) {
        event.drops.clear()
        event.droppedExp = 0
    }

    @CalledByCommand
    override fun endGame() {
        super.endGame()

        nukeArea()
        deleteStartingPlatform()// delete the starting platform for cases where it is still there

        WORLD.difficulty = Difficulty.PEACEFUL
        WORLD.setGameRule(GameRule.DO_FIRE_TICK,false)
        WORLD.setGameRule(GameRule.MOB_GRIEFING,false)

        //RESET GLOBAL VARIABLES
        generatedBitsIndexes.clear()
        amountOfMobsToSpawnPerInterval = Mobs.INITIAL_AMOUNTS_OF_MOBS_TO_SPAWN_IN_A_CYCLE
        amountOfCratesToSpawnPerInterval= MHConst.Spawns.LootCrates.INITIAL_AMOUNTS_OF_CRATES_TO_SPAWN_IN_A_CYCLE
        curTimeLeftTillNewMaze = MazeGen.REGENERATE_MAZE_INITIAL_COOLDOWN

        players.forEach { player ->
            player.inventory.clear()
            player.gameMode = GameMode.ADVENTURE
        }
    }

    private fun deleteStartingPlatform() {
        initFloor(
            MHConst.STARTING_PLATFORM_RADIUS,
            MHConst.STARTING_PLATFORM_RADIUS,
            Material.AIR,
            Locations.START_LOCATION_PLATFORM,
            WORLD
        )
    }

    @CalledByCommand
    fun nukeArea() {
        // Nuke the game area
        for (vector in Locations.MAZE_REGION) {
            var blockAt = WORLD.getBlockAt(vector)
            blockAt.type = Material.AIR

            if (blockAt.hasMetadata("isALootCrate"))
                blockAt.removeMetadata("isALootCrate",plugin)
        }
    }

    override fun prepareGameSetting() {
        super.prepareGameSetting()

        WORLD.time = 1000
        WORLD.setGameRule(DO_DAYLIGHT_CYCLE, false)
        WORLD.setGameRule(GameRule.DO_FIRE_TICK,false)
        WORLD.setGameRule(GameRule.MOB_GRIEFING,false)
        WORLD.difficulty = Mobs.WORLD_DIFFICULTY

        for (player in players) {
            player.teleport(Locations.PLAYERS_START_LOCATION)
            player.gameMode = GameMode.SURVIVAL
            player.clearActivePotionEffects()
        }
    }

    /** Disable mobs getting burned by the sun while Maze Hunt is running*/
    @EventHandler
    private fun onEntityCombust(event: EntityCombustEvent) {
        if (!guardAlreadyRunning) return

        event.isCancelled = true
    }


    private val _TRUE = 1.toByte()
    private val _FALSE = 0.toByte()
    override fun prepareArea() {
        nukeArea()

        // Create the starting platform for the players to stand on. It'll be deleted momentarily.
        initFloor(
            MHConst.STARTING_PLATFORM_RADIUS,
            MHConst.STARTING_PLATFORM_RADIUS,
            Material.GLASS,
            Locations.START_LOCATION_PLATFORM,
            WORLD
        )


        pausableRunnables += PausableBukkitRunnable(plugin as JavaPlugin, MHConst.STARTING_PLATFORM_LIFESPAN) {
            deleteStartingPlatform()
        }.apply { this.start() }


        //Code that creates the maze area

        20L delayTheFollowing {
            //reset the global tracker of physical bits generated, since this method is called multiple times, each time creating a new maze
            generatedBitsIndexes.clear()
            /** 2D array to keep track of generated bits*/
            val mazeMatrix: D2Array<Byte> = mk.d2array<Byte>(MAZE_DIMENSION_X, MAZE_DIMENSION_Z, { _FALSE })
            // Start generating from the center of the maze
            mazeMatrix[MAZE_DIMENSION_X / 2, MAZE_DIMENSION_Z / 2] = _TRUE
            physicallyCreateBit(MAZE_DIMENSION_X / 2, MAZE_DIMENSION_Z / 2, MazeGen.BIT_RADIUS)
            // -1 because we already placed the first bit in the center
            var numberOfBitsLeftToGenerate = MazeGen.AMOUNT_OF_BITS - 1
            // add the center bit to the set of generated bits
            generatedBitsIndexes += BitPoint(MAZE_DIMENSION_X / 2, MAZE_DIMENSION_Z / 2)
            // Limit the number of bit-snakes to prevent infinite loops
            var maxAmountOfTries = MazeGen.MAX_ATTEMPTS_TO_GENERATE
            while (numberOfBitsLeftToGenerate > 0 && maxAmountOfTries > 0) {
                maxAmountOfTries -= 1

                // Select a random spot already generated from the bits that have been generated. It stores the coordinates of the bit. Start a new chain of bits from there
                val newBitCreatorStartingSpot: BitPoint = generatedBitsIndexes.random()

                numberOfBitsLeftToGenerate = startNewChainOfBits(
                    newBitCreatorStartingSpot,
                    mazeMatrix,
                    generatedBitsIndexes,
                    numberOfBitsLeftToGenerate
                )
            }
        }
    }

    /**
     * Start a new chain of bits from the given starting spot in the maze matrix.
     * A chain of bits is a series of bits that are connected to each other in a mostly straight line, with a small chance to change the direction at each step.
     * The chain of bits will continue until it either runs out of bits to generate or it reaches a point where it can no longer safely generate a new bit in the current direction.
     * @param newBitCreatorStartingSpot The starting spot for the new chain of bits in the maze matrix.
     * @param mazeMatrix The maze matrix.
     * @param bitsLeft The number of bits left to generate in the maze.
     * @return The updated number of bits left to generate after generating the new chain of bits.
     */
    private fun startNewChainOfBits(
        newBitCreatorStartingSpot: BitPoint,
        mazeMatrix: D2Array<Byte>,
        generatedBitsIndexes: MutableSet<BitPoint>,
        bitsLeft: Int
    ) : Int {
        var returnedNumOfBitsLeft = bitsLeft

        // in the direction the new chain of bits will go towards mostly. we need to make sure that it is safe to travel in that direction, so we filter the directions first
        var potentialDirections = Direction.entries.filter { dir ->
            isSafeToTravelForwards(dir, newBitCreatorStartingSpot, mazeMatrix)
        }
        // If there is no safe direction to travel, return early
        if (potentialDirections.isEmpty()) return returnedNumOfBitsLeft


        var curDirectionOfChain = potentialDirections.random()

        var curLengthOfChain = 0

        //region Code for deciding the new Bit that will be generated and generating it
        while (
            returnedNumOfBitsLeft > 0 &&
            curLengthOfChain < MazeGen.MAX_LENGTH_OF_CHAIN &&
            isSafeToTravelForwards(curDirectionOfChain, newBitCreatorStartingSpot, mazeMatrix)
        ) {
            // calculate the position of the new bit to be generated
            when (curDirectionOfChain) {
                Direction.NORTH -> {newBitCreatorStartingSpot.z -= 1 }
                Direction.SOUTH -> { newBitCreatorStartingSpot.z += 1 }
                Direction.EAST -> { newBitCreatorStartingSpot.x += 1 }
                Direction.WEST -> { newBitCreatorStartingSpot.x -= 1 }
            }

            physicallyCreateBit(newBitCreatorStartingSpot.x,newBitCreatorStartingSpot.z, MazeGen.BIT_RADIUS)

            mazeMatrix[newBitCreatorStartingSpot.x, newBitCreatorStartingSpot.z] = _TRUE
            generatedBitsIndexes += newBitCreatorStartingSpot.copy()

            returnedNumOfBitsLeft -= 1
            curLengthOfChain += 1

            // have a small chance to change the predominant direction
            if (successChance(MazeGen.PROBABILITY_OF_CHANGING_DIRECTION)) {
                curDirectionOfChain = listOf(
                    curDirectionOfChain.getClockwise(),
                    curDirectionOfChain.getCounterClockwise()
                ).random()
            }
        }
        //endregion

        return returnedNumOfBitsLeft
    }

    /**
     * Check if it is safe to travel forwards in the current direction of the chain of bits.
     * It is safe to travel forwards if there is enough space in the maze matrix to generate a new bit in that direction, without touching any surrounding existing bit.
     * @param direction The current direction of the chain of bits.
     * @param point The current point in the maze matrix.
     * @param mazeMatrix The maze matrix.
     * @return True if it is safe to travel forwards, false otherwise.
     */
    private fun isSafeToTravelForwards(
        direction: Direction,
        point: BitPoint,
        mazeMatrix: D2Array<Byte>
    ) : Boolean {
        fun isAreaClear(point: BitPoint, xRange: IntRange, zRange: IntRange): Boolean {
            return zRange.all { zOffset ->
                xRange.all { xOffset ->
                    mazeMatrix[point.x + xOffset,point.z + zOffset] == _FALSE
                }
            }
        }

        // the tiles to check in the direction of travel.
        val directionTilesToCheck = -1..1

        // how far to check in the direction of travel
        val longReach = 2
        var smallReach = 1
        //

        return when (direction) {
            Direction.NORTH -> if (point.z >= longReach) {
                isAreaClear(point, directionTilesToCheck, -minOf(point.z, longReach)..-smallReach)
            } else false
            Direction.SOUTH -> if (point.z <= MAZE_DIMENSION_Z-1 - longReach) {
                isAreaClear(point, directionTilesToCheck, smallReach..minOf(point.z, longReach))
            } else false
            Direction.EAST -> if (point.x <= MAZE_DIMENSION_Z-1 - longReach) {
                isAreaClear(point, smallReach..minOf(point.x, longReach), directionTilesToCheck)
            } else false
            Direction.WEST -> if (point.x >= longReach) {
                isAreaClear(point,-minOf(point.x, longReach)..-smallReach, directionTilesToCheck)
            } else false
        }
    }

    /**
     * Physically create a maze Bit in the game world - this is a square-like platform defined by the size.
     * @param bitIndexX The x index of the bit in the maze matrix.
     * @param bitIndexZ The z index of the bit in the maze matrix.
     * @param radius The radius of the bit in blocks. The size of the bit will be (radius * 2 + 1) x (radius * 2 + 1)
     * Made from a predefined block type
     */
    private fun physicallyCreateBit(bitIndexX: Int, bitIndexZ: Int, @Suppress("SameParameterValue") radius: Int) {1
        val center = getBitLocation(bitIndexX, bitIndexZ)

        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val selectedLocation = Location(WORLD, center.x + x, center.y, center.z + z)
                selectedLocation.block.type = MazeGen.FLOOR_MATERIALS.getWeightedRandom()
            }
        }
    }

    private fun getBitLocation(bitIndexX: Int, bitIndexZ: Int): Location {
        val center = Location(
            WORLD,
            bitIndexX.toDouble() * BIT_SIZE + MAZE_ORIGIN.x,
            MAZE_ORIGIN.y,
            bitIndexZ.toDouble() * BIT_SIZE + MAZE_ORIGIN.z
        )
        return center
    }
}

