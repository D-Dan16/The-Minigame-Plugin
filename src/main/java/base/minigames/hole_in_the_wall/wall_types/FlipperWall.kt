package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.deleteWallSchematic
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.getStopSignRegion
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.overlaps
import base.minigames.hole_in_the_wall.models.wall.cancelPendingMoves
import base.minigames.hole_in_the_wall.models.wall.initializeWallMotion
import org.bukkit.Material
import org.bukkit.Particle

/**
 * Flips itself when getting close to the platform, aiming still towards the middle
 *
 * If the wall is a psych wall, it'll flip when the wall resumes moving.
 *
 * Can flip itself multiple times if it goes back to that same stop sign (i.e., Teleports to there via Repeater Wall Type)
 */
class FlipperWall : WallType() {
    companion object {
        internal const val ID = "Flipper"
        internal const val DESCRIPTION = "Flips itself when getting close to the platform, aiming still towards middle"
    }

    override fun toString(): String = ID

    internal var allowedToFlip = true

    fun attemptFlip() {
        if (!allowedToFlip)
            return

        // only flip if it has reached its close stop sign
        if (!getStopSignRegion(thisWall.directionWallComesFrom).overlaps(thisWall.wallRegion))
            return

        // if it is a psych wall, then do not activate the flipping just yet.
        if (thisWall.getWallType<PsychWall>()?.hasDoneAPsych == false)
            return

        flipSchematic()
        allowedToFlip = false
    }


    private fun flipSchematic() {
        spawnParticlesOnWall(
            particle = Particle.BLOCK,
            data = Material.SLIME_BLOCK.createBlockData(),
            particleAmountOnBlock = 5
        )

        thisWall.cancelPendingMoves()
        deleteWallSchematic(thisWall)
        thisWall.isFlipped = thisWall.isFlipped.not()
        thisWall.rebuildSchematicAtThisLocationAndPaste()
        thisWall.initializeWallMotion()
    }


}
