package base.minigames.hole_in_the_wall.game_loop.walls.wall_creating

import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.SpawnerRuntimeState
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.models.wall.WallDecayCause
import base.minigames.hole_in_the_wall.models.wall.WallSpawnBatch
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.utils.additions.Direction
import base.utils.other.BuildLoader
import kotlin.random.Random
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState

/** Queues a new wall of the given direction for the normal spawn flow. */
fun HoleInTheWall.createNewWall(
    direction: Direction,
    wallTypes: Collection<WallType> = emptyList(),
    spawnBatch: WallSpawnBatch,
) {
    val wallFile = pickWeightedWallFileForCurrentDifficulty()

    val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped

    val newWall = Wall(this,wallFile, direction, shouldBeFlipped, wallTypes.toMutableList(), spawnBatch)

    SpawnerRuntimeState.upcomingWalls.add(newWall) // Add the new wall to the list of upcoming walls
    HITWDevLogger.wall(newWall, "queued for spawn; $newWall")
}

/** Loads a wall into the arena and registers it as an active wall. */
fun spawnWall(wall: Wall) {
    // Make the wall exist in the world by loading the schematic
    wall.spawn()
    // Add the new wall to the bucket in the direction it came from.
    WallsRuntimeState.existingWalls.add(wall)
    HITWDevLogger.wall(wall, "brought to life; region=${wall.wallRegion.minimumPoint}..${wall.wallRegion.maximumPoint}")
}

/** Removes a queued wall before its schematic is pasted, cancelling any pre-spawn effects. */
fun discardQueuedWall(wall: Wall, reason: String) {
    if (!SpawnerRuntimeState.upcomingWalls.remove(wall)) return

    wall.markDeleted()
    HITWDevLogger.wall(wall, "discarded before spawn; $reason")
}

/** Deletes every active wall from the arena. */
fun clearWalls() {
    WallsRuntimeState.existingWalls.allWalls().forEach { deleteWall(it) }
}

/** Removes the wall's schematic from the world and unregisters it from active walls. */
fun deleteWall(wall: Wall) {
    wall.markDeleted()
    BuildLoader.deleteSchematic(wall.wallRegion.minimumPoint, wall.wallRegion.maximumPoint)
    if (wall.decayCause != WallDecayCause.DOOMINATOR_NUKE) {
        wall.actionsWhenDecayed.forEach(Runnable::run)
    }
    // delete the wall reference from the alive wall buckets
    val hasWallBeenDeleted = WallsRuntimeState.existingWalls.remove(wall)

    if (!hasWallBeenDeleted) {
        HITWDevLogger.warn("wall#${wall.debugId} deletion failed; wall not found in the alive walls list")
    } else {
        HITWDevLogger.wall(wall, "deleted from existingWalls")
    }
}
