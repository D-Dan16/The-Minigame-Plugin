package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.Locations.PlatformGeometry
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.models.wall.WallSpawnBatch
import base.minigames.hole_in_the_wall.models.WallsByDirections
import base.utils.additions.Direction

internal object WallsRuntimeState {
    /** Every wall that still belongs to the active round, grouped by its spawn direction. */
    internal val aliveWalls: WallsByDirections = WallsByDirections()

    /** A per-axis snapshot of [aliveWalls], used for proximity and middle-ring checks. */
    internal var locationsOfWalls: WallAxisOccupancyGrid = buildWallAxisOccupancyGrid()

    /**
     * Used by Repeater walls - after they teleport, they need their space for moving to the center again, so we don't allow other walls to go to step at middle
     */
    internal var forbidResumingPsyches: Boolean = false


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
                otherWall.isActivelyMoving(tickCount)
        }
    }
}

/** A retained random choice for the next stopped Psych wall to resume from a batch. */
internal data class PsychWallResumeSelection(
    val wall: Wall,
    val resumesAlongsideReturningWall: Boolean,
)


/**
 * Derived occupancy for one game-loop tick.
 *
 * This is rebuilt from the live wall set, so it should be treated as a snapshot rather than
 * a source of truth. Each array is indexed by the corresponding arena-relative coordinate and
 * lets proximity logic reason about walls that share the same travel line.
 *
 * @property xAxis occupancy of walls traveling on the east-west arena axis.
 * @property zAxis occupancy of walls traveling on the north-south arena axis.
 */
@Suppress("ArrayInDataClass")
internal data class WallAxisOccupancyGrid(
    val xAxis: Array<WallReference>,
    val zAxis: Array<WallReference>
) {
    /**
     * Records [wall] in the occupancy array for its travel axis.
     *
     * This is used only while constructing a fresh snapshot; it does not update a wall's actual
     * location or movement state.
     */
    fun markWall(wall: Wall) {
        wall.markOnAxisOccupancy(
            when (wall.axisLocation.axis) {
                HITWConst.Locations.ArenaAxis.X -> xAxis
                HITWConst.Locations.ArenaAxis.Z -> zAxis
            }
        )
    }


    fun getWallsAtMiddleOrOuterRing(): Set<Wall> {
        return wallsAtMiddle() + wallsAtOuterRing()
    }

    /**
     * Returns whether a wall occupies either the middle ring or the platform-side outer ring.
     *
     * This combined check is useful when the game needs to know whether the central approach is
     * occupied without caring which of the two relevant rings contains the wall.
     */
    fun hasWallsAtMiddleOrOuterRing(): Boolean {
        return hasWallAtMiddle() || hasWallsAtOuterRing()
    }

    /**
     * Returns the distinct walls currently intersecting the middle ring on either axis.
     *
     * A set prevents a wall from appearing twice if it spans multiple occupied coordinates.
     */
    fun wallsAtMiddle(): Set<Wall> {
        /** Collects all walls that fall within the middle-ring span on the given axis. */
        fun collectWallsWithinMiddleRange(
            axis: Array<WallReference>,
            arenaAxis: HITWConst.Locations.ArenaAxis,
            wallsAtMiddle: MutableSet<Wall>
        ) {
            val middleAxisIndices = middleAxisIndicesFor(arenaAxis)

            middleAxisIndices.forEach { index ->
                val wallRef = axis.getOrNull(index)?.wallRef ?: return@forEach
                wallsAtMiddle.add(wallRef)
            }
        }

        // Check both travel axes because the middle ring spans the whole platform.
        val wallsAtMiddle = mutableSetOf<Wall>()

        collectWallsWithinMiddleRange(xAxis, HITWConst.Locations.ArenaAxis.X, wallsAtMiddle)
        collectWallsWithinMiddleRange(zAxis, HITWConst.Locations.ArenaAxis.Z, wallsAtMiddle)

        return wallsAtMiddle
    }

    /** Returns whether any wall currently intersects the middle ring. */
    fun hasWallAtMiddle(): Boolean {
        return wallsAtMiddle().isNotEmpty()
    }

    /**
     * Returns the direction of the middle-ring wall when all occupying walls came from one side.
     *
     * Returns `null` when the middle ring is empty or contains walls arriving from different
     * directions.
     */
    fun getDirectionOfWallAtMid(): Direction? {
        return wallsAtMiddle()
            .map { it.directionWallComesFrom }
            .distinct()
            .singleOrNull()
    }

    /** Converts the middle-ring world-coordinate span into indices for [axis]'s occupancy array. */
    private fun middleAxisIndicesFor(axis: HITWConst.Locations.ArenaAxis): IntRange {
        val middleRange = when (axis) {
            HITWConst.Locations.ArenaAxis.X -> PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.x()..PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.x()
            HITWConst.Locations.ArenaAxis.Z -> PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.z()..PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.z()
        }

        val start = HITWConst.Locations.relativeCoordinateToAxisIndex(axis, middleRange.first)
        val end = HITWConst.Locations.relativeCoordinateToAxisIndex(axis, middleRange.last)

        return start..end
    }

    /** Returns whether a wall occupies one of the four platform-side outer-ring positions. */
    private fun hasWallsAtOuterRing(): Boolean = wallsAtOuterRing().isNotEmpty()

    /** Looks up the wall between each stop sign and the platform, if present. */
    private fun wallsAtOuterRing(): MutableSet<Wall> {
        val wallsAtOuterRing = mutableSetOf<Wall>()
        Direction.entries.forEach { direction ->
            wallsBetweenStopSignAndPlatform(direction)?.let {
                wallsAtOuterRing += it
            }
        }
        return wallsAtOuterRing
    }

    /** Returns the wall at the platform-side position for [direction]'s stop-sign corridor. */
    private fun wallsBetweenStopSignAndPlatform(direction: Direction): Wall? {
        return wallAt(
            axisFor(direction),
            platformSideAxisPosition(direction)
        )
    }

    /** Converts [direction]'s platform-side corridor position to an arena-relative coordinate. */
    private fun platformSideAxisPosition(direction: Direction): Int {
        val spawn = HITWConst.Locations.SPAWN

        return when (direction) {
            Direction.NORTH -> PlatformGeometry.NORTH_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.z() - spawn.blockZ
            Direction.SOUTH -> PlatformGeometry.SOUTH_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.z() - spawn.blockZ
            Direction.EAST -> PlatformGeometry.EAST_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.x() - spawn.blockX
            Direction.WEST -> PlatformGeometry.WEST_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.x() - spawn.blockX
        }
    }

    /** Maps a wall's spawn direction to the arena axis on which it travels. */
    private fun axisFor(direction: Direction): HITWConst.Locations.ArenaAxis = when (direction) {
        Direction.NORTH, Direction.SOUTH -> HITWConst.Locations.ArenaAxis.Z
        Direction.EAST, Direction.WEST -> HITWConst.Locations.ArenaAxis.X
    }

    /** Returns the wall stored at [coordinate] on [axis], or `null` outside the tracked range. */
    private fun wallAt(axis: HITWConst.Locations.ArenaAxis, coordinate: Int): Wall? {
        val occupancy = when (axis) {
            HITWConst.Locations.ArenaAxis.X -> xAxis
            HITWConst.Locations.ArenaAxis.Z -> zAxis
        }

        val index = HITWConst.Locations.relativeCoordinateToAxisIndex(axis, coordinate)
        return occupancy.getOrNull(index)?.wallRef
    }

    override fun toString(): String {
        return "xAxis=${xAxis.contentToString()}\n zAxis=${zAxis.contentToString()}"
    }

    /** Formats an occupancy array with its arena-relative coordinates for diagnostic output. */
    fun Array<WallReference>.contentToString(): String {
        val axis = when {
            this === xAxis -> HITWConst.Locations.ArenaAxis.X
            this === zAxis -> HITWConst.Locations.ArenaAxis.Z
            else -> return joinToString(prefix = "[", postfix = "]") { it.toString() }
        }

        val minimumDisplacement = when (axis) {
            HITWConst.Locations.ArenaAxis.X ->
                HITWConst.Locations.WEST_WALL_SPAWN.blockX - HITWConst.Locations.SPAWN.blockX

            HITWConst.Locations.ArenaAxis.Z ->
                HITWConst.Locations.NORTH_WALL_SPAWN.blockZ - HITWConst.Locations.SPAWN.blockZ
        }

        return indices.joinToString(prefix = "[", postfix = "]") { index ->
            "${minimumDisplacement + index}:${this[index]}"
        }
    }
}

/**
 * A nullable occupancy entry.
 *
 * Empty array slots are represented by a [WallReference] whose [wallRef] is `null`, which keeps
 * the occupancy arrays' element type uniform.
 */
data class WallReference(
    val wallRef: Wall?
) {
    /** Returns whether this occupancy entry contains a wall. */
    fun hasWall() = wallRef != null

    override fun toString(): String {
        return if (hasWall()) "T" else "F"
    }
}
