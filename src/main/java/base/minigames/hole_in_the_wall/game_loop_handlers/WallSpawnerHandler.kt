package base.minigames.hole_in_the_wall.game_loop_handlers

import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop_handlers.GameLoopRuntimeState.curWallDifficultyInPack
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.SpawnerRuntimeState.existingWallsList
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.SpawnerRuntimeState.upcomingWalls
import base.minigames.hole_in_the_wall.objects.Wall
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.utils.additions.Direction
import base.utils.other.BuildLoader
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger
import org.bukkit.Bukkit
import java.io.File
import kotlin.random.Random


data class WallPack(val easy: List<File>, val medium: List<File>, val hard: List<File>, val very_hard: List<File>)

/** The selected wall pack for the current map, grouped by difficulty. */
internal lateinit var wallPackDifficulties: WallPack

/**
 * USED FOR ONLY DEBUGGING PURPOSES
 */
fun createNewWall() {
    val wallFile = pickWeightedWallFileForCurrentDifficulty()
    val direction = Direction.entries.toTypedArray().random() // Randomly select a direction for the wall to come from
    val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped
    val newWall = Wall(wallFile, direction, shouldBeFlipped) // Create a new wall

    bringWallToLife(newWall) // Make the wall exist in the world by loading the schematic


    newWall.showBlocks() // Show the corners of the wall for debugging purposes
    Bukkit.getServer().broadcast(Component.text("flipped: ${newWall.isFlipped}. DirectionWallCome: ${newWall.directionWallComesFrom}").color(
        NamedTextColor.DARK_AQUA))
}

fun HoleInTheWall.createNewWall(direction: Direction, wallTypes: Collection<WallType> = emptyList()) {
    val wallFile = pickWeightedWallFileForCurrentDifficulty()

    val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped


    val newWall = Wall(wallFile, direction, shouldBeFlipped, wallTypes) // Create a new wall

    upcomingWalls.add(newWall) // Add the new wall to the list of upcoming walls
}

fun bringWallToLife(wall: Wall) {
    // Make the wall exist in the world by loading the schematic
    wall.makeWallExist()
    // Add the new wall to the list of existing walls. the wall is added at the end of the list!
    existingWallsList.add(wall)
}

fun clearWalls() {
    while (existingWallsList.isNotEmpty()) {
        deleteWall(existingWallsList[0])
    }
}

fun deleteWall(wall: Wall) {
    BuildLoader.deleteSchematic(wall.wallRegion.minimumPoint, wall.wallRegion.maximumPoint)
    // delete the wall reference from the AliveWallsList
    val hasWallBeenDeleted = existingWallsList.remove(wall)

    if (!hasWallBeenDeleted) {
        logger().warn("HITW: Wall deletion failed, wall not found in the alive walls list")
    }
}

private fun pickWeightedWallFileForCurrentDifficulty(): File {
    fun pickFromPool(pool: List<File>): File? {
        return pool.randomOrNull()
    }

    fun fallbackPools(vararg pools: List<File>): File {
        for (pool in pools) {
            pickFromPool(pool)?.let { return it }
        }

        throw IllegalStateException("No wall schematics are available for the current difficulty")
    }

    return when (curWallDifficultyInPack) {
        base.minigames.hole_in_the_wall.HITWConst.WallDifficulty.EASY ->
            fallbackPools(wallPackDifficulties.easy)

        base.minigames.hole_in_the_wall.HITWConst.WallDifficulty.MEDIUM ->
            when (Random.nextInt(100)) {
                in 0..84 -> fallbackPools(wallPackDifficulties.medium, wallPackDifficulties.easy)
                else -> fallbackPools(wallPackDifficulties.easy, wallPackDifficulties.medium)
            }

        base.minigames.hole_in_the_wall.HITWConst.WallDifficulty.HARD ->
            when (Random.nextInt(100)) {
                in 0..84 -> fallbackPools(wallPackDifficulties.hard, wallPackDifficulties.medium, wallPackDifficulties.easy)
                in 85..94 -> fallbackPools(wallPackDifficulties.medium, wallPackDifficulties.hard, wallPackDifficulties.easy)
                else -> fallbackPools(wallPackDifficulties.easy, wallPackDifficulties.medium, wallPackDifficulties.hard)
            }

        base.minigames.hole_in_the_wall.HITWConst.WallDifficulty.VERY_HARD ->
            when (Random.nextInt(100)) {
                in 0..79 -> fallbackPools(wallPackDifficulties.very_hard, wallPackDifficulties.hard, wallPackDifficulties.medium, wallPackDifficulties.easy)
                in 80..89 -> fallbackPools(wallPackDifficulties.hard, wallPackDifficulties.very_hard, wallPackDifficulties.medium, wallPackDifficulties.easy)
                in 90..96 -> fallbackPools(wallPackDifficulties.medium, wallPackDifficulties.hard, wallPackDifficulties.very_hard, wallPackDifficulties.easy)
                else -> fallbackPools(wallPackDifficulties.easy, wallPackDifficulties.medium, wallPackDifficulties.hard, wallPackDifficulties.very_hard)
            }

        else -> fallbackPools(wallPackDifficulties.easy)
    }
}
