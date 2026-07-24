package base.minigames.hole_in_the_wall.game_loop.walls.wall_creating

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.WallSpawnerState
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.wall_types.PsychWall
import base.minigames.hole_in_the_wall.wall_types.WallType
import base.minigames.hole_in_the_wall.wall_types.WallTypeDefinition
import base.minigames.hole_in_the_wall.models.wall.WallSpawnBatch
import base.utils.additions.Direction
import java.io.File
import kotlin.random.Random


data class WallDifficultyPack(val fileList: List<File>, val difficulty: HITWConst.WallDifficulty)
/** Files grouped by the difficulty tier they belong to. */
data class WallPack(
    val easy: WallDifficultyPack,
    val medium: WallDifficultyPack,
    val hard: WallDifficultyPack,
    val very_hard: WallDifficultyPack
)
/** The selected wall pack for the current map, grouped by difficulty. */
internal lateinit var wallPackDifficulties: WallPack

internal fun HoleInTheWall.designWallBehaviorAndCreateIt(directionToChooseFrom: MutableList<Direction>, wallSpawnerState: WallSpawnerState) {
    val wallsToSpawn = when (wallSpawnerState) {
        WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE -> GameLoopRuntimeState.multiWallSelectionRange.random()
        WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> 1
        else -> throw IllegalStateException("Unexpected state: $wallSpawnerState")
    }

    // This batch remains attached to its walls after the spawner has cleared `upcomingWalls`.
    val spawnBatch = WallSpawnBatch()
    var createdRealWall = false
    repeat(wallsToSpawn) {
        val wallTypes = mutableListOf<WallType>()

        if (!createdRealWall) {
            createdRealWall = true
        } else {
            wallTypes += GameLoopRuntimeState.availableWallTypes
                .first { it === WallTypeDefinition.PSYCH }
                .create()
        }

        addOptionalAvailableWallTypes(wallTypes)
        addMorphToPsychWallWhenAvailable(wallTypes)

        createNewWall(directionToChooseFrom.removeFirst(), wallTypes, spawnBatch)
    }
}

/** Adds at most one type from each available mutually exclusive group, preserving optional types. */
private fun addOptionalAvailableWallTypes(wallTypes: MutableList<WallType>) {
    GameLoopRuntimeState.availableWallTypes
        // Psych is assigned by the multi-wall-wave rule, and Morph is exclusively a
        // companion effect for Psych walls below.
        .filterNot { it === WallTypeDefinition.PSYCH || it === WallTypeDefinition.MORPH }
        .groupBy { it.mutuallyExclusiveGroup ?: it }
        .values
        .forEach { choices -> rollWallTypeAssignment(choices)
        ?.let { wallTypes += it.create() } }
}

/** Makes every Psych wall a Morph wall once Morph has entered the active pool. */
private fun addMorphToPsychWallWhenAvailable(wallTypes: MutableList<WallType>) {
    if (wallTypes.none { it is PsychWall })
        return
    if (WallTypeDefinition.MORPH !in GameLoopRuntimeState.availableWallTypes)
        return

    wallTypes += WallTypeDefinition.MORPH.create()
}

/** Selects one type by its percentage weights, or `null` when the roll lands in the unassigned range. */
private fun rollWallTypeAssignment(choices: Collection<WallTypeDefinition>): WallTypeDefinition? {
    require(choices.isNotEmpty()) { "Cannot roll a wall type from an empty set of choices" }
    require(choices.all { it.assignmentChance in 0..100 }) {
        "Wall type assignment chances must be between 0 and 100: ${choices.joinToString()}"
    }

    val totalChance = choices.sumOf { it.assignmentChance }
    require(totalChance <= 100) {
        "Wall type assignment chances must total at most 100: ${choices.joinToString()} total $totalChance"
    }

    val roll = Random.nextInt(100)
    var accumulatedChance = 0
    return choices.firstOrNull { choice ->
        accumulatedChance += choice.assignmentChance
        roll < accumulatedChance
    }
}

/** Chooses a schematic file using the current difficulty weighting rules. */
internal fun pickWeightedWallFileForCurrentDifficulty(dontInclude: File? = null): Pair<File, HITWConst.WallDifficulty> {
    val pool = when (GameLoopRuntimeState.wallDifficulty) {
        HITWConst.WallDifficulty.EASY -> wallPackDifficulties.easy

        HITWConst.WallDifficulty.MEDIUM -> when (Random.nextInt(100)) {
            in HITWConst.WallPackSelection.MEDIUM_PRIMARY_POOL_ROLL_RANGE -> wallPackDifficulties.medium
            else -> wallPackDifficulties.easy
        }

        HITWConst.WallDifficulty.HARD -> when (Random.nextInt(100)) {
            in HITWConst.WallPackSelection.HARD_PRIMARY_POOL_ROLL_RANGE -> wallPackDifficulties.hard
            in HITWConst.WallPackSelection.HARD_SECONDARY_POOL_ROLL_RANGE -> wallPackDifficulties.medium
            else -> wallPackDifficulties.easy
        }

        HITWConst.WallDifficulty.VERY_HARD -> when (Random.nextInt(100)) {
            in HITWConst.WallPackSelection.VERY_HARD_PRIMARY_POOL_ROLL_RANGE -> wallPackDifficulties.very_hard
            in HITWConst.WallPackSelection.VERY_HARD_SECONDARY_POOL_ROLL_RANGE -> wallPackDifficulties.hard
            in HITWConst.WallPackSelection.VERY_HARD_TERTIARY_POOL_ROLL_RANGE -> wallPackDifficulties.medium
            else -> wallPackDifficulties.easy
        }
    }

    return pool.fileList.filterNot { it == dontInclude }.random() to pool.difficulty
}

internal fun chooseNewSchematic(wall: Wall) {
    val pool = when (wall.difficultyOfWall) {
        HITWConst.WallDifficulty.EASY -> wallPackDifficulties.easy
        HITWConst.WallDifficulty.MEDIUM -> wallPackDifficulties.medium
        HITWConst.WallDifficulty.HARD -> wallPackDifficulties.hard
        HITWConst.WallDifficulty.VERY_HARD -> wallPackDifficulties.very_hard
    }

    wall.wallFile = pool.fileList.filterNot { it == wall.wallFile }.random()
}
