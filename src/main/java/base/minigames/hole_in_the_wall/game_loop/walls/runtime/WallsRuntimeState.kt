package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.models.WallsByDirections
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.models.wall.WallSpawnBatch

internal object WallsRuntimeState {
    /** Every wall that still belongs to the active round, grouped by its spawn direction. */
    internal val aliveWalls: WallsByDirections = WallsByDirections()

    /** A per-axis snapshot of [aliveWalls], used for proximity and middle-ring checks. */
    internal var locationsOfWalls: WallAxisOccupancyGrid = buildWallAxisOccupancyGrid()

    /**
     * Used by Repeater walls - after they teleport, they need their space for moving to the center again, so we don't allow other walls to go to step at middle
     */
    internal var forbidResumingPsyches: Boolean = false
    internal var forbidAnyLifecycleProgression: Boolean = false


    /**
     * Psych walls that remain in the arena after stopping.
     *
     * A wall is added here when its type specifies `shouldRemoveWhenStopped = false`; the
     * lifecycle code later decides when it may move again.
     */
    internal val stayingPsychWalls: MutableList<Wall> = mutableListOf()

    /**
     * The retained Psych-wall resume choice for each batch.
     *
     * A selection also records whether a compatible wall may follow a Returning batch mate
     * immediately or must wait for that wall to leave the arena.
     */
    internal val selectedPsychWallToResumeByBatch:
        MutableMap<WallSpawnBatch, PsychWallResumeSelection> = mutableMapOf()

    /**
     * The next game tick on which we are allowed to try resuming a staying psych wall.
     *
     * This keeps the resume cadence explicit instead of tying it to a global tick modulus.
     */
    internal var nextPsychWallResumeAttemptTick: Int = 0

    /**
     * Deletes all live walls and restores this state to its empty-round defaults.
     *
     * This must be called before a new round starts so no wall entities or Psych-wall scheduling
     * state leak into it.
     */
    fun reset() {
        // Mark first so each wall can clean up its entity before its collection is discarded.
        aliveWalls.allWalls().forEach {
            it.markDeleted()
        }
        aliveWalls.clear()
        locationsOfWalls = buildWallAxisOccupancyGrid()
        stayingPsychWalls.clear()
        selectedPsychWallToResumeByBatch.clear()
        nextPsychWallResumeAttemptTick = 0
        forbidAnyLifecycleProgression = false
        forbidResumingPsyches = false
    }

    /**
     * A batch mate that is actively moving owns the batch's current center-crossing turn.
     * Stopped Psych walls must wait for that wall to finish before one of them can resume.
     */
    fun hasActivelyMovingBatchMate(wall: Wall): Boolean {
        return aliveWalls.allWalls().any { otherWall ->
            otherWall !== wall &&
                otherWall.spawnBatch === wall.spawnBatch &&
                otherWall.isActivelyMoving(GameLoopRuntimeState.tickCount)
        }
    }
}