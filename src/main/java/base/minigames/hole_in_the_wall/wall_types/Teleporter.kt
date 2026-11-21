package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.models.wall.WallAxisCoordinate

abstract class Teleporter : WallType() {
    var finishedTeleportation: Boolean = false
    var currentlyTeleporting = false
    val teleportDestinationReservedSpace: MutableList<Int> = mutableListOf()
    lateinit var teleportFromLocation: WallAxisCoordinate
}
