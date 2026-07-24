package base.minigames.hole_in_the_wall.wall_types

import kotlin.random.Random

/**
 * A reusable description of a wall type that may be selected while designing a wall.
 *
 * Definitions create a fresh [WallType] for every wall because wall types hold per-wall runtime
 * state after they are attached.
 */
internal enum class WallTypeDefinition(
    val displayName: String,
    val mutuallyExclusiveGroup: WallTypeMutuallyExclusiveGroup? = null,
    /**
     * Percentage of the assignment roll occupied by this type.
     *
     * Weights in a mutually exclusive group must total at most 100. Any remaining percentage
     * means no type from that group is assigned to the wall.
     */
    val assignmentChance: Int = 50,
    private val createWallType: () -> WallType,
    val description: String,
) {
    // Always is chosen if in a wall wave there's move than a singular wall
    PSYCH(
        displayName = "Psych",
        createWallType = { PsychWall(Random.nextBoolean()) },
        description = "A wall that can stop before it reaches the platform. May decay, or stay. Always comes in groups of walls"
    ),
    EARLY_DECAYED(
        displayName = "Early Decayed",
        assignmentChance = 30,
        mutuallyExclusiveGroup = WallTypeMutuallyExclusiveGroup.TRAVEL_LIFESPAN_MODIFIER,
        createWallType = ::EarlyDecayedWall,
        description = "A wall with a shorter than normal lifespan, which makes it decay in the middle platform"
    ),
    RAMMING(
        displayName = "Ramming",
        assignmentChance = 35,
        mutuallyExclusiveGroup = WallTypeMutuallyExclusiveGroup.TRAVEL_LIFESPAN_MODIFIER,
        createWallType = ::RammingWall,
        description = "A long-lived wall that rams opposing walls out of the arena.",
    ),
    JUMPSCARE(
        displayName = "Jumpscare",
        createWallType = ::JumpscareWall,
        description = "A wall that spawns very close to the platform, emitting green particles to indicate its very soon presence"
    ),
    REPEATER(
        assignmentChance = 25,
        displayName = "Repeater",
        createWallType = ::RepeaterWall,
        description = "A wall that while at mid decides to teleport back to a place it already has been at."
    ),
    DOOMINATOR(
        displayName = "Doominator",
        assignmentChance = 25,
        createWallType = ::DoominatorWall,
        description = "A Wall fused with gunpowder - when it dies, every other wall will shortly die as well! Beware - will inflict blindness on players"
    ),
    ;

    /**
     * Whether this wall type can be selected for a new game.
     * Commented entries will make those wall types not be able to be selected
    */
    val isTurned: Boolean
        get() = name in setOf(
            "PSYCH",
//            "EARLY_DECAYED",
             "REPEATER",
            "JUMPSCARE",
//             "RAMMING",
            "DOOMINATOR"
        )

    fun create(): WallType = createWallType()
}

/** Types in the same group may not be assigned to the same wall together. */
internal enum class WallTypeMutuallyExclusiveGroup {
    TRAVEL_LIFESPAN_MODIFIER,
}