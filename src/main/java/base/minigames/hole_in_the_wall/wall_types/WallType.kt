package base.minigames.hole_in_the_wall.wall_types

/**
 * A permanent identity tag for a wall.
 *
 * A wall can carry multiple wall types at once, and each type stays attached for
 * the wall's entire lifespan. A type may still choose to act only during a small
 * window of that lifespan once its behavior is implemented.
 */
interface WallType {
    /** Stable identifier for this wall type. */
    val id: String
}
