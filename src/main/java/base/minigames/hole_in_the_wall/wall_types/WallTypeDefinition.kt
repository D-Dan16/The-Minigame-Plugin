package base.minigames.hole_in_the_wall.wall_types

import kotlin.random.Random

/**
 * A reusable description of a wall type that may be selected while designing a wall.
 *
 * Definitions create a fresh [WallType] for every wall because wall types hold per-wall runtime
 * state after they are attached.
 */
internal enum class WallTypeDefinition(
    val id: String,
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
        id = PsychWall.ID,
        description = PsychWall.DESCRIPTION,
        createWallType = { PsychWall(Random.nextBoolean()) },
    ),
    EARLY_DECAYED(
        id = EarlyDecayedWall.ID,
        description = EarlyDecayedWall.DESCRIPTION,
        assignmentChance = 30,
        mutuallyExclusiveGroup = WallTypeMutuallyExclusiveGroup.TRAVEL_LIFESPAN_MODIFIER,
        createWallType = ::EarlyDecayedWall,
    ),
    RAMMING(
        id = RammingWall.ID,
        description = RammingWall.DESCRIPTION,
        assignmentChance = 35,
        mutuallyExclusiveGroup = WallTypeMutuallyExclusiveGroup.TRAVEL_LIFESPAN_MODIFIER,
        createWallType = ::RammingWall,
    ),
    JUMPSCARE(
        id = JumpscareWall.ID,
        description = JumpscareWall.DESCRIPTION,
        createWallType = ::JumpscareWall,
    ),
    REPEATER(
        id = RepeaterWall.ID,
        description = RepeaterWall.DESCRIPTION,
        assignmentChance = 33,
        createWallType = ::RepeaterWall,
    ),
    DOOMINATOR(
        id = DoominatorWall.ID,
        description = DoominatorWall.DESCRIPTION,
        assignmentChance = 25,
        createWallType = ::DoominatorWall,
    ),
    // exclusively being assigned to psych walls. always. (when it is in the wall type pool)
    MORPH(
        id = MorphWall.ID,
        description = MorphWall.DESCRIPTION,
        assignmentChance = 100,
        createWallType = ::MorphWall,
    ),
    ;

    /**
     * Whether this wall type can be selected for a new game.
     * Commented entries will make those wall types not be able to be selected
    */
    val isTurned: Boolean
        get() = name in setOf(
            "PSYCH",
            "EARLY_DECAYED",
             "REPEATER",
            "JUMPSCARE",
            "RAMMING",
            "DOOMINATOR",
            "MORPH"
        )

    fun create(): WallType = createWallType()
}

/** Types in the same group may not be assigned to the same wall together. */
internal enum class WallTypeMutuallyExclusiveGroup {
    TRAVEL_LIFESPAN_MODIFIER,
}
