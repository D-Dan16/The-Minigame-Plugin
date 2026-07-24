package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.chooseNewSchematic
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWallSchematic
import base.minigames.hole_in_the_wall.models.wall.WallState
import base.minigames.hole_in_the_wall.models.wall.cancelPendingMoves
import base.minigames.hole_in_the_wall.models.wall.initializeWallMotion
import base.minigames.hole_in_the_wall.models.wall.worldLocationFor
import base.utils.other.BuildLoader
import org.bukkit.Particle

/**
 * Walls equipping this insane wall type will go mad and constantly morph into different walls when they get close to mid
 *
 * Morph walls morph only if they are Psych walls as well, and a Psych wall will always be a morph wall if the wall type is active in the pool
 *
 * Morph walls stop changing shapes as soon as it is their turn to pass through the middle
 */
class MorphWall : WallType() {
    companion object {
        internal const val ID = "morph"
        internal const val DESCRIPTION = "Walls equipping this insane wall type will go mad and constantly morph into different walls when they get close to mid. Only with Psych walls"
    }


    override fun toString(): String = ID

    fun morphWhileAtStopSign() {
        val psychWall = thisWall.getWallType<PsychWall>()!!
        if (!psychWall.hasStoppedAtStopSign || psychWall.hasDoneAPsych)
            return

        // We make sure that the wall is live and well - if it has been deleted by external causes, we wouldn't want to spawn a wall
        if (thisWall.state == WallState.Deleted)
            return

        morph()
    }

    fun morph() {
        thisWall.cancelPendingMoves()
        deleteWallSchematic(thisWall)

        val prevWallFile = thisWall.wallFile
        chooseNewSchematic(thisWall)
        thisWall.rebuildSchematicAt(thisWall.worldLocationFor(thisWall.axisLocation, thisWall.directionWallComesFrom))
        BuildLoader.loadSchematic(thisWall.holder)

        spawnParticlesOnWall(particle = Particle.ENCHANTED_HIT, particleAmountOnBlock = 10)

        HITWDevLogger.wall(thisWall, "wall morphs from $prevWallFile -> ${thisWall.wallFile}")
        thisWall.updateDebugIdDisplayLocation()
        thisWall.initializeWallMotion()
    }
}
