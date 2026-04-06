@file:Suppress("DEPRECATION")

package base.utils.additions

import org.bukkit.Location
import org.bukkit.Material
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