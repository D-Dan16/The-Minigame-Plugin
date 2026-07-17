package base.minigames.hole_in_the_wall.game_loop.walls.wall_creating

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.wall_types.EarlyDecayedWall
import base.minigames.hole_in_the_wall.wall_types.LateDecayedWall
import base.minigames.hole_in_the_wall.wall_types.PsychWall
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.utils.additions.Direction
import java.io.File
import kotlin.random.Random


/** Files grouped by the difficulty tier they belong to. */
data class WallPack(val easy: List<File>, val medium: List<File>, val hard: List<File>, val very_hard: List<File>)
/** The selected wall pack for the current map, grouped by difficulty. */
internal lateinit var wallPackDifficulties: WallPack

internal fun designWallBehaviorAndCreateIt(directionToChooseFrom: MutableList<Direction>, wallSpawnerState: WallSpawnerState) {
    val wallsToSpawn = when (wallSpawnerState) {
        WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE -> GameLoopRuntimeState.multiWallSelectionRange.random()
        WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> 1
        else -> throw IllegalStateException("Unexpected state: $wallSpawnerState")
    }

    var createdRealWall = false
    repeat(wallsToSpawn) {
        val wallTypes = mutableListOf<WallType>()

        if (!createdRealWall) {
            createdRealWall = true
        } else {
            wallTypes += PsychWall(Random.nextBoolean())
        }

        // Assign other wall types to the wall
//        if (Random.nextBoolean()) {
//                        wallTypes += setOf(EarlyDecayedWall(), LateDecayedWall()).random()
//            wallTypes += EarlyDecayedWall()
            wallTypes += LateDecayedWall()
//        }

        createNewWall(directionToChooseFrom.removeFirst(), wallTypes)
    }
}


/** Chooses a schematic file using the current difficulty weighting rules. */
internal fun pickWeightedWallFileForCurrentDifficulty(): File {
    fun fallbackPools(vararg pools: List<File>): File {
        for (pool in pools) {
            pool.randomOrNull()?.let { return it }
        }

        throw IllegalStateException("No wall schematics are available for the current difficulty")
    }

    return when (GameLoopRuntimeState.wallDifficulty) {
        HITWConst.WallDifficulty.EASY ->
            fallbackPools(wallPackDifficulties.easy)

        HITWConst.WallDifficulty.MEDIUM ->
            when (Random.nextInt(100)) {
                in 0..84 -> fallbackPools(wallPackDifficulties.medium, wallPackDifficulties.easy)
                else -> fallbackPools(wallPackDifficulties.easy, wallPackDifficulties.medium)
            }

        HITWConst.WallDifficulty.HARD ->
            when (Random.nextInt(100)) {
                in 0..84 -> fallbackPools(wallPackDifficulties.hard, wallPackDifficulties.medium, wallPackDifficulties.easy)
                in 85..94 -> fallbackPools(wallPackDifficulties.medium, wallPackDifficulties.hard, wallPackDifficulties.easy)
                else -> fallbackPools(wallPackDifficulties.easy, wallPackDifficulties.medium, wallPackDifficulties.hard)
            }

        HITWConst.WallDifficulty.VERY_HARD ->
            when (Random.nextInt(100)) {
                in 0..79 -> fallbackPools(wallPackDifficulties.very_hard, wallPackDifficulties.hard, wallPackDifficulties.medium, wallPackDifficulties.easy)
                in 80..89 -> fallbackPools(wallPackDifficulties.hard, wallPackDifficulties.very_hard, wallPackDifficulties.medium, wallPackDifficulties.easy)
                in 90..96 -> fallbackPools(wallPackDifficulties.medium, wallPackDifficulties.hard, wallPackDifficulties.very_hard, wallPackDifficulties.easy)
                else -> fallbackPools(wallPackDifficulties.easy, wallPackDifficulties.medium, wallPackDifficulties.hard, wallPackDifficulties.very_hard)
            }
    }
}
