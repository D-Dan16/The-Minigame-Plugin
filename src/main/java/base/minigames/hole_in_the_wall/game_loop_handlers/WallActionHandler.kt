package base.minigames.hole_in_the_wall.game_loop_handlers

import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.existingWallsList
import base.minigames.hole_in_the_wall.game_loop_handlers.state_machine.upcomingWalls
import base.minigames.hole_in_the_wall.objects.Wall
import base.utils.additions.Direction
import base.utils.other.BuildLoader
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger
import org.bukkit.Bukkit
import java.io.File
import kotlin.random.Random

//DO MODIFY THIS FOR DEBUGGING PURPOSES
internal lateinit var wallPackSchematics: Array<File> //the wallpack selected from a given map. each element features a group of files of walls, whose grouped via difficulty.

fun createNewWall() {
    val wallFile = wallPackSchematics.random() // Randomly select a wall from the wall pack
    val direction = Direction.entries.toTypedArray().random() // Randomly select a direction for the wall to come from
    val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped
    val newWall = Wall(wallFile, direction, shouldBeFlipped) // Create a new wall

    bringWallToLife(newWall) // Make the wall exist in the world by loading the schematic


    newWall.showBlocks() // Show the corners of the wall for debugging purposes
    Bukkit.getServer().broadcast(Component.text("flipped: ${newWall.isFlipped}. DirectionWallCome: ${newWall.directionWallComesFrom}").color(
        NamedTextColor.DARK_AQUA))
}

// DO NOT MODIFY THIS FOR DEBUGGING PURPOSES
fun createNewWall(direction: Direction, isPsych: Boolean, shouldPsychDieWhenStopped: Boolean = true) {
    val wallFile = wallPackSchematics.random() // Randomly select a wall from the wall pack
    val shouldBeFlipped: Boolean = Random.nextBoolean() // Randomly decide if the wall should be flipped


    val newWall = Wall(wallFile, direction, shouldBeFlipped, isPsych, shouldPsychDieWhenStopped) // Create a new wall

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