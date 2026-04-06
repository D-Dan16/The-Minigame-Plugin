package base.utils.extensions_for_classes

import base.MinigamePlugin
import base.utils.additions.Direction
import com.sk89q.worldedit.math.BlockVector3
import kotlinx.coroutines.SupervisorJob
import org.bukkit.Bukkit
import org.bukkit.Bukkit.*
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import kotlin.collections.iterator
import kotlin.random.Random

fun <Type> Random.getNextWeighted(weights: Map<Type, Int>): Type {
    val totalWeight = weights.values.sum()
    var randomValue = nextInt(totalWeight)

    for ((item, weight) in weights) {
        if (randomValue < weight) {
            return item
        }
        randomValue -= weight
    }

    throw IllegalStateException("Should not reach here, weights are not set up correctly.")
}

fun World.getBlockAt(vector: BlockVector3): Block {
    return this.getBlockAt(vector.x(),vector.y(), vector.z())
}

fun World.getMaterialAt(vector: BlockVector3): Material {
    return this.getBlockAt(vector.x(),vector.y(), vector.z()).type
}

operator fun BlockVector3.plus(other: BlockVector3): BlockVector3 {
    return BlockVector3.at(
        this.x() + other.x(),
        this.y() + other.y(),
        this.z() + other.z()
    )
}

operator fun BlockVector3.minus(other: BlockVector3): BlockVector3 {
    return BlockVector3.at(
        this.x() - other.x(),
        this.y() - other.y(),
        this.z() - other.z()
    )
}

fun Block.toBlockVector3() : BlockVector3 {
    return BlockVector3.at(this.x,this.y,this.z)
}

fun Player.clearInvAndGiveItems(
    materialList: Collection<Material>,
    sizeForEachItemSlot: Int = 1
) {
    this.inventory.clear()

    if (materialList.size >= this.inventory.size)
        this.sendMessage("The amount of items that were attempted to give to u exceed the space u have. Giving only what could have been given")

    materialList.forEachIndexed { index, material ->

        //if we have filled in the entire inventory of the player, stop the action.
        if (index >= this.inventory.size) return

        this.inventory.setItem(index, ItemStack(material,sizeForEachItemSlot))
    }
}

fun World.removeItemsInRegion(region: BoundingBox) {
    this.getNearbyEntities( region).forEach { entity ->
        if (entity is Item) {
            entity.remove()
        }
    }
}

fun Direction.toYaw(): Float {
    return when (this) {
        Direction.SOUTH -> 0f
        Direction.WEST  -> 90f
        Direction.NORTH -> 180f
        Direction.EAST  -> -90f
    }
}

inline fun <reified T: Number> T.randomlyFlipSign(): T {
    if (Random.Default.nextBoolean()) return this

    return when (this) {
        is Int -> -this as T
        is Double -> -this as T
        is Float -> -this as T
        is Long -> -this as T
        is Short -> -this as T
        else -> this
    }
}

fun Block.breakGradually(decayDuration: Long) {
    var curCrackStage = 0
    val maxCrackStage = 9 // crack animation has 0–9

    val waitTimePerStage = (decayDuration.toDouble() / maxCrackStage).toLong().coerceAtLeast(1)

    // this is used for registering the crack states for the block in the sendBlockDamage()
    val source = world.spawn(location, ArmorStand::class.java) { stand ->
        stand.isInvisible = true
        stand.isMarker = true
        stand.isInvulnerable = true
        stand.isSilent = true
        stand.setAI(false)
    }

    getScheduler().runTaskTimer(MinigamePlugin.Companion.plugin, Runnable {
        // Block already gone, stop animation
        if (type.isAir) { return@Runnable }

        if (curCrackStage < maxCrackStage) {
            val progress = (curCrackStage + 1).toFloat() / maxCrackStage

            for (player: Player in world.players) {
                player.sendBlockDamage(location, progress, source)
            }
            curCrackStage++
        } else {
            this.type = Material.AIR
            source.remove()
        }
    },0L, waitTimePerStage)
}
