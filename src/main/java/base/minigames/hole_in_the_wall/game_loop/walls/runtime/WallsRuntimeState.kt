package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.models.wall.Wall
import base.minigames.hole_in_the_wall.models.wall.WallAxisCoordinate
import base.minigames.hole_in_the_wall.models.WallsByDirections
import base.minigames.hole_in_the_wall.wall_types.PsychWall
import base.utils.additions.Direction

internal object WallsRuntimeState {
    internal val existingWalls: WallsByDirections = WallsByDirections()
    internal var locationsOfWalls: WallAxisOccupancyGrid = buildWallAxisOccupancyGrid()

    /**
     * Psych walls that: shouldRemoveWhenStopped=false
     */
    internal val stayingPsychWalls: MutableList<Wall> = mutableListOf()

    /**
     * The next game tick on which we are allowed to try resuming a staying psych wall.
     *
     * This keeps the resume cadence explicit instead of tying it to a global tick modulus.
     */
    internal var nextPsychWallResumeAttemptTick: Int = 0

    fun reset() {
        existingWalls.allWalls().forEach {
            it.markDeleted()
        }
        existingWalls.clear()
        locationsOfWalls = buildWallAxisOccupancyGrid()
        stayingPsychWalls.clear()
        nextPsychWallResumeAttemptTick = 0
    }

    /**
     * A batch mate that is actively moving owns the batch's current centre-crossing turn.
     * Stopped Psych walls must wait for that wall to finish before one of them can resume.
     */
    fun hasActivelyMovingBatchMate(wall: Wall): Boolean {
        return existingWalls.allWalls().any { otherWall ->
            otherWall !== wall &&
                otherWall.spawnBatch === wall.spawnBatch &&
                otherWall.isActivelyMoving(tickCount)
        }
    }
}


/**
 * Derived occupancy for the current tick.
 *
 * This is rebuilt from the live wall set, so it should be treated as a snapshot rather than
 * a source of truth. The axes let us reason about walls that share the same travel line.
 */
@Suppress("ArrayInDataClass")
internal data class WallAxisOccupancyGrid(
    val xAxis: Array<WallReference>,
    val zAxis: Array<WallReference>
) {
    override fun toString(): String {
        return "xAxis=${xAxis.contentToString()}\n zAxis=${zAxis.contentToString()}"
    }

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

    /** Marks the wall on the axis array that matches its arena axis. */
    fun markWall(wall: Wall) {
        when (wall.axisLocation.axis) {
            HITWConst.Locations.ArenaAxis.X -> wall.markOnAxisOccupancy(xAxis)
            HITWConst.Locations.ArenaAxis.Z -> wall.markOnAxisOccupancy(zAxis)
        }
    }

    /** Returns the walls that currently occupy the middle ring on either axis. */
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

        val wallsAtMiddle = mutableSetOf<Wall>()

        collectWallsWithinMiddleRange(xAxis, HITWConst.Locations.ArenaAxis.X, wallsAtMiddle)
        collectWallsWithinMiddleRange(zAxis, HITWConst.Locations.ArenaAxis.Z, wallsAtMiddle)

        return wallsAtMiddle
    }
    /** Returns `true` when at least one wall currently occupies the middle ring. */
    fun hasWallAtMiddle(): Boolean {
        return wallsAtMiddle().isNotEmpty()
    }

    /** Returns the single direction represented by the wall at the middle ring, if unambiguous. */
    fun getDirectionOfWallAtMid(): Direction? {
        return wallsAtMiddle()
            .map { it.directionWallComesFrom }
            .distinct()
            .singleOrNull()
    }

    /** Converts the middle-ring world coordinates into axis-array indices. */
    private fun middleAxisIndicesFor(axis: HITWConst.Locations.ArenaAxis): IntRange {
        val middleRange = when (axis) {
            HITWConst.Locations.ArenaAxis.X ->
                HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.x()..
                    HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.x()

            HITWConst.Locations.ArenaAxis.Z ->
                HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.minimumPoint.z()..
                    HITWConst.Locations.PlatformGeometry.ABOVE_PLATFORM_REGION.maximumPoint.z()
        }

        val start = HITWConst.Locations.relativeCoordinateToAxisIndex(axis, middleRange.first)
        val end = HITWConst.Locations.relativeCoordinateToAxisIndex(axis, middleRange.last)

        return start..end
    }

    /**
     * Returns `true` when a psych wall is waiting at a stop sign and some other wall is still
     * occupying the corridor between that stop sign and the platform.
     */
    fun isAWallCloseToMid(): Boolean {
        val corridors = stopSignCorridors()
        val waitingPsychWalls = corridors
            .mapNotNull { it.wallAtStopSign() }
            .filter {
                it.isMovementHalted &&
                    it.getWallType<PsychWall>()?.let { psychWall ->
                        psychWall.canResume && !psychWall.hasDoneAPsych
                    } == true
            }
            .toSet()

        return corridors.any { corridor ->
            corridor.walls().any { it !in waitingPsychWalls }
        }
    }

    private fun stopSignCorridors(): List<WallCorridor> {
        val geometry = HITWConst.Locations.PlatformGeometry
        val x = HITWConst.Locations.ArenaAxis.X
        val z = HITWConst.Locations.ArenaAxis.Z

        fun relativeToSpawn(axis: HITWConst.Locations.ArenaAxis, worldCoordinate: Int): Int {
            val spawnCoordinate = when (axis) {
                HITWConst.Locations.ArenaAxis.X -> HITWConst.Locations.SPAWN.blockX
                HITWConst.Locations.ArenaAxis.Z -> HITWConst.Locations.SPAWN.blockZ
            }
            return worldCoordinate - spawnCoordinate
        }

        return listOf(
            WallCorridor(zAxis, WallAxisCoordinate(relativeToSpawn(z, geometry.NORTH_STOP_SIGN_REGION.minimumPoint.z()), z), WallAxisCoordinate(relativeToSpawn(z, geometry.NORTH_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.z()), z)),
            WallCorridor(xAxis, WallAxisCoordinate(relativeToSpawn(x, geometry.EAST_STOP_SIGN_REGION.minimumPoint.x()), x), WallAxisCoordinate(relativeToSpawn(x, geometry.EAST_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.x()), x)),
            WallCorridor(zAxis, WallAxisCoordinate(relativeToSpawn(z, geometry.SOUTH_STOP_SIGN_REGION.minimumPoint.z()), z), WallAxisCoordinate(relativeToSpawn(z, geometry.SOUTH_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.z()), z)),
            WallCorridor(xAxis, WallAxisCoordinate(relativeToSpawn(x, geometry.WEST_STOP_SIGN_REGION.minimumPoint.x()), x), WallAxisCoordinate(relativeToSpawn(x, geometry.WEST_LINE_BETWEEN_STOP_SIGN_AND_PLATFORM.minimumPoint.x()), x))
        )
    }

    private class WallCorridor(
        private val occupancy: Array<WallReference>,
        private val stopSign: WallAxisCoordinate,
        platformSide: WallAxisCoordinate
    ) {
        init {
            require(stopSign.axis == platformSide.axis) { "A wall corridor must stay on one axis." }
        }

        private val coordinates =
            minOf(stopSign.coordinate, platformSide.coordinate)..maxOf(stopSign.coordinate, platformSide.coordinate)

        fun wallAtStopSign(): Wall? = wallAt(stopSign.coordinate)

        fun walls(): List<Wall> = coordinates.mapNotNull(::wallAt)

        private fun wallAt(coordinate: Int): Wall? {
            val index = HITWConst.Locations.relativeCoordinateToAxisIndex(stopSign.axis, coordinate)
            return occupancy.getOrNull(index)?.wallRef
        }
    }
}

data class WallReference(
    val wallRef: Wall?
) {
    fun hasWall() = wallRef != null

    override fun toString(): String {
        return if (hasWall()) "T" else "F"
    }
}
