@file:Suppress("DEPRECATION")

package base.utils.additions

import base.MinigamePlugin.Companion.world
import base.utils.extensions_for_classes.getBlockAt
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import kotlin.random.Random

/** A simple wrapper class to hold an integer value by reference. Useful for passing integers to functions that need to modify them. */
class IntRef(var value: Int)

/**
 * Used as a method return type to indicate whether the method exited early for one reason or another or not.
 */
enum class ExitStatus {
    EARLY_EXIT,
    COMPLETED
}

enum class Direction {
    NORTH, SOUTH, EAST, WEST;

    fun getClockwise(): Direction {
        return when (this) {
            NORTH -> EAST
            SOUTH -> WEST
            EAST -> SOUTH
            WEST -> NORTH
        }
    }

    fun getCounterClockwise(): Direction {
        return when (this) {
            NORTH -> WEST
            SOUTH -> EAST
            EAST -> NORTH
            WEST -> SOUTH
        }
    }

    fun getOpposite(): Direction {
        return when (this) {
            NORTH -> SOUTH
            SOUTH -> NORTH
            EAST -> WEST
            WEST -> EAST
        }
    }
}

object Utils {

    fun nukeGameArea(center: Location, radius: Int) {
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val currentLocation = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    currentLocation.block.type = Material.AIR
                }
            }
        }
    }

    /**
     * Initializes the floor under the player to a specific material.
     * @param xLengthRad The x radius of the floor
     * @param zLengthRad The z radius of the floor
     * @param material The material to set the floor to
     * @param center The center of the floor
     * @param world The world to set the floor in
     */
    @JvmStatic
    fun initFloor(xLengthRad: Int, zLengthRad: Int, material: Material, center: Location, world: World?) {
        // Initialize the floor under the player to stone 1 block at a time. The floor is a rectangle with side lengths 2*xLengthRad+1 and 2*zLengthRad+1.
        for (x in -xLengthRad..xLengthRad) {
            for (z in -zLengthRad..zLengthRad) {
                val selectedLocation = Location(world, center.x + x, center.y, center.z + z)
                selectedLocation.block.type = material
            }
        }
    }

    /**
     * Returns true with the given probability (chance).
     * @param chance A double between 0.0 and 1.0 representing the probability of returning true.
     * @return true with the given probability, false otherwise.
     */
    fun successChance(chance: Double): Boolean {
        require(chance in 0.0..1.0) { "Chance must be between 0.0 and 1.0" }
        return Random.nextDouble() < chance
    }

    inline fun doActionByChance(probability: Double, action: () -> Unit) {
        if (successChance(probability)) action()
    }

}