package base.minigames.hole_in_the_wall.models

import base.minigames.hole_in_the_wall.models.wall.Wall
import base.utils.additions.Direction
import java.util.EnumMap

internal class WallsByDirections {
    internal val byDirection: EnumMap<Direction, MutableList<Wall>> =
        EnumMap<Direction, MutableList<Wall>>(Direction::class.java).apply {
            Direction.entries.forEach { direction ->
                this[direction] = mutableListOf()
            }
        }

    /** Total number of walls stored across all directions. */
    val size: Int
        get() = byDirection.values.sumOf { it.size }

    /** Returns `true` when no direction currently contains any walls. */
    fun isEmpty(): Boolean = byDirection.values.all { it.isEmpty() }

    /** Removes every wall from every direction bucket. */
    fun clear() {
        byDirection.values.forEach { it.clear() }
    }

    /** Adds the wall to the bucket for the direction it came from. */
    fun add(wall: Wall) {
        byDirection.getValue(wall.directionWallComesFrom).add(wall)
    }

    /** Removes the wall from the bucket for the direction it came from. */
    fun remove(wall: Wall): Boolean {
        return byDirection.getValue(wall.directionWallComesFrom).remove(wall)
    }

    /**
     * Moves [wall] to the bucket for [newDirection] while updating its direction-dependent
     * orientation. A wall that reverses must be re-indexed before any direction-based spawning
     * or collision logic observes it.
     */
    fun changeDirection(wall: Wall, newDirection: Direction) {
        val oldDirection = wall.directionWallComesFrom
        if (oldDirection == newDirection) return

        check(byDirection.getValue(oldDirection).remove(wall)) {
            "Wall#${wall.debugId} was not registered under $oldDirection"
        }

        wall.updateDirection(newDirection)
        byDirection.getValue(newDirection).add(wall)
    }

    /** Returns the set of directions that currently have at least one wall. */
    fun directionsInUse(): Set<Direction> {
        return byDirection.filterValues { it.isNotEmpty() }.keys
    }

    /** Returns all stored walls in a single flattened list. */
    fun allWalls(): List<Wall> {
        return byDirection.values.flatten()
    }

    /** Returns the number of unique batches present among the alive walls. */
    fun amountOfBatches(): Int {
        return byDirection.values.flatten().map { it.spawnBatch }.distinct().size
    }

    /** Returns the most recently added wall for the given direction, if any. */
    fun lastWallFrom(direction: Direction): Wall? {
        return byDirection.getValue(direction).lastOrNull()
    }
}
