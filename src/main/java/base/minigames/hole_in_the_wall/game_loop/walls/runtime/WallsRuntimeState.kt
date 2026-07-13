package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.models.WallsByDirections

internal object WallsRuntimeState {
    internal val existingWalls: WallsByDirections = WallsByDirections()
    internal var locationsOfWalls: WallAxisOccupancyGrid = buildWallAxisOccupancyGrid()

    fun reset() {
        existingWalls.clear()
        locationsOfWalls = buildWallAxisOccupancyGrid()
    }
}
