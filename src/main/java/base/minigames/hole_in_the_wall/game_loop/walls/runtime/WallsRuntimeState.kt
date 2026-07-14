package base.minigames.hole_in_the_wall.game_loop.walls.runtime

import base.minigames.hole_in_the_wall.models.Wall
import base.minigames.hole_in_the_wall.models.WallsByDirections

internal object WallsRuntimeState {
    internal val existingWalls: WallsByDirections = WallsByDirections()
    internal var locationsOfWalls: WallAxisOccupancyGrid = buildWallAxisOccupancyGrid()

    /**
     * Psyche walls that: shouldRemoveWhenStopped=false
     */
    internal val stayingPsychWalls: MutableList<Wall> = mutableListOf()

    fun reset() {
        existingWalls.clear()
        locationsOfWalls = buildWallAxisOccupancyGrid()
        stayingPsychWalls.clear()
    }
}
