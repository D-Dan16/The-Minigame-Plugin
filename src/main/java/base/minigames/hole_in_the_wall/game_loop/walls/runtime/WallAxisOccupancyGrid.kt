package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.utils.additions.Direction

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
            HITWConst.Locations.ArenaAxis.X -> HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.x()..HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.x()
            HITWConst.Locations.ArenaAxis.Z -> HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.z()..HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.z()
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
            Direction.NORTH -> HITWConst.Locations.PlatformGeometry.NORTH_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.z() - spawn.blockZ
            Direction.SOUTH -> HITWConst.Locations.PlatformGeometry.SOUTH_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.z() - spawn.blockZ
            Direction.EAST -> HITWConst.Locations.PlatformGeometry.EAST_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.x() - spawn.blockX
            Direction.WEST -> HITWConst.Locations.PlatformGeometry.WEST_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.x() - spawn.blockX
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