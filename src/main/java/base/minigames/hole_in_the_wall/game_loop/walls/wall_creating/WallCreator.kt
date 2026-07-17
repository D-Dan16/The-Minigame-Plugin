package base.minigames.hole_in_the_wall.game_loop.walls.wall_creating

import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.SpawnerRuntimeState
import base.minigames.hole_in_the_wall.models.Wall
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.utils.additions.Direction
import base.utils.other.BuildLoader
import java.io.File
import kotlin.random.Random
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState

/** Creates a wall immediately for debugging, bypassing the normal spawn flow. */
fun createNewWall() {
    val wallFile = pickWeightedWallFileForCurrentDifficulty()
    val direction = Direction.entries.toTypedArray().random() // Randomly select a direction for the wall to come from
    val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped
    val newWall = Wall(wallFile, direction, shouldBeFlipped) // Create a new wall

    bringWallToLife(newWall) // Make the wall exist in the world by loading the schematic


    newWall.showBlocks() // Show the corners of the wall for debugging purposes
    HITWDevLogger.wall(newWall, "debug spawned via createNewWall(); $newWall)")
}

/** Queues a new wall of the given direction for the normal spawn flow. */
fun createNewWall(direction: Direction, wallTypes: Collection<WallType> = emptyList()) {
    val wallFile = pickWeightedWallFileForCurrentDifficulty()

    val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped

    val newWall = Wall(wallFile, direction, shouldBeFlipped, wallTypes.toMutableList()) // Create a new wall

    SpawnerRuntimeState.upcomingWalls.add(newWall) // Add the new wall to the list of upcoming walls
    HITWDevLogger.wall(newWall, "queued for spawn; $newWall")
}

/** Loads a wall into the arena and registers it as an active wall. */
fun bringWallToLife(wall: Wall) {
    // Make the wall exist in the world by loading the schematic
    wall.makeWallExist()
    // Add the new wall to the bucket for the direction it came from.
    WallsRuntimeState.existingWalls.add(wall)
    HITWDevLogger.wall(wall, "brought to life; region=${wall.wallRegion.minimumPoint}..${wall.wallRegion.maximumPoint}")
}

/** Deletes every active wall from the arena. */
fun clearWalls() {
    WallsRuntimeState.existingWalls.allWalls().forEach { deleteWall(it) }
}

/** Removes the wall's schematic from the world and unregisters it from active walls. */
fun deleteWall(wall: Wall) {
    wall.markDeleted()
    BuildLoader.deleteSchematic(wall.wallRegion.minimumPoint, wall.wallRegion.maximumPoint)
    // delete the wall reference from the alive wall buckets
    val hasWallBeenDeleted = WallsRuntimeState.existingWalls.remove(wall)

    if (!hasWallBeenDeleted) {
        HITWDevLogger.warn("wall#${wall.debugId} deletion failed; wall not found in the alive walls list")
    } else {
        HITWDevLogger.wall(wall, "deleted from existingWalls")
    }
}

