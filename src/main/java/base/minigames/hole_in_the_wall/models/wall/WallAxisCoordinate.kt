package base.minigames.hole_in_the_wall.models.wall

import base.minigames.hole_in_the_wall.HITWConst

/** A wall coordinate relative to the player spawn, together with the arena axis it lies on. */
data class WallAxisCoordinate(
    val coordinate: Int,
    val axis: HITWConst.Locations.ArenaAxis
) {
    override fun toString(): String {
        return "(axis:$axis | cord:$coordinate)"
    }
}
