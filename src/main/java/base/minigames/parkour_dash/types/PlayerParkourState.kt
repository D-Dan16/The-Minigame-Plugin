package base.minigames.parkour_dash.types

import base.minigames.parkour_dash.PDConst
import base.minigames.parkour_dash.PDConst.ParkourPath
import base.minigames.parkour_dash.GameEvents
import org.bukkit.Location

/**
 * Consolidated per-player state for Parkour Dash.
 * Merges checkpoint tracking, course completion counts, and current path into a single structure.
 */
data class PlayerParkourState(
    var currentPath: ParkourPath = ParkourPath.UNDECIDED,
    /** init to -1 since at the very start we don't even start at a course. it'll be updated to 0 when we do reach one via [GameEvents.checkIfPlayerCompleteCourse]*/
    val coursesCompleted: Map<ParkourPath, CourseIndex> = mapOf(ParkourPath.LEFT to CourseIndex(-1),ParkourPath.MIDDLE to CourseIndex(-1),ParkourPath.RIGHT to CourseIndex(-1)),
    val checkpoints: Map<ParkourPath, MutableList<Location>> = mapOf(
        ParkourPath.LEFT to mutableListOf(PDConst.Locations.START_LOCATION_OF_PLAYER_LEFT_PATH.clone()),
        ParkourPath.MIDDLE to mutableListOf(PDConst.Locations.START_LOCATION_OF_PLAYER_MIDDLE_PATH.clone()),
        ParkourPath.RIGHT to mutableListOf(PDConst.Locations.START_LOCATION_OF_PLAYER_RIGHT_PATH.clone())
    )
)

data class CourseIndex(var i: Int = -1)
