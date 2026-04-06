package base.minigames.maze_hunt

import base.minigames.maze_hunt.MHConst.Spawns.LootCrates.applyRandomDurability
import base.utils.extensions_for_classes.breakGradually
import base.utils.extensions_for_classes.fullyContains
import base.utils.extensions_for_classes.getItemStackByMaterial
import base.utils.extensions_for_classes.getWeightedRandom
import base.utils.extensions_for_classes.hasDurability
import base.utils.extensions_for_classes.modifyDuraBy
import base.utils.extensions_for_classes.remainingDurability
import base.utils.extensions_for_classes.returnQuantityOfEach
import org.bukkit.Material
import org.bukkit.entity.Mob
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack

@EventHandler
private fun onBlockPlace(event: BlockPlaceEvent) {
    val block = event.block

    // Do special action for tnt specifically and ignite the block instead of breaking it
    if (block.type == Material.TNT) {
        block.type = Material.AIR

        val tnt = MHConst.Locations.WORLD.spawn(
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

/**
 * List used for newly created mobs that trigger on contact with a player.
 * Each mob in this list will not trigger their action while on there.
 * Should include: Slimes, Magma Cubes
 */
internal val mobsToDisableContactDamage: MutableList<Mob> = mutableListOf()

@EventHandler
fun ignoreContactDamageOfSlimes(event: EntityDamageByEntityEvent) {
    if (event.damager in mobsToDisableContactDamage) {
        event.isCancelled = true
    }
}

@EventHandler
private fun onMobDeath(event: EntityDeathEvent) {
    event.drops.clear()
    event.droppedExp = 0
}

/** Disable mobs getting burned by the sun while Maze Hunt is running*/
@EventHandler
private fun onEntityCombust(mazeHunt: MazeHunt, event: EntityCombustEvent) {
    if (!mazeHunt.isGameRunning) return

    event.isCancelled = true
}

@EventHandler
private fun onLootCrateBreak(mazeHunt: MazeHunt, event: BlockBreakEvent) {
    if (event.block.hasMetadata("isALootCrate").not())
        return

    val metaList = event.block.getMetadata("isALootCrate")

    val typeName = metaList.first { it.owningPlugin == mazeHunt.plugin }.asString()

    // convert string back to enum
    val crateType: MHConst.Spawns.LootCrates.LootCrateType = MHConst.Spawns.LootCrates.LootCrateType.valueOf(typeName)

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
    event.block.removeMetadata("isALootCrate", mazeHunt.plugin)

    // disable the block drop so that the physical block won't be used/exploited
    event.isDropItems = false
}