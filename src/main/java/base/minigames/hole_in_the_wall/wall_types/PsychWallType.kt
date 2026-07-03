package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.HITWConst

/**
 * Wall type for a wall that can stop before it reaches the platform.
 *
 * The type itself is permanent. The wall can still choose to stay visually
 * inactive for most of its lifespan and only act during a short window.
 */
data class PsychWallType(
    val shouldRemoveWhenStopped: Boolean = true
) : WallType {
    override val id: String = "psych"

    fun initialLifespan(): Int {
        return HITWConst.DEFAULT_PSYCH_WALL_TRAVEL_LIFESPAN
    }

    fun shouldRemoveWhenStopped(): Boolean {
        return shouldRemoveWhenStopped
    }
}
