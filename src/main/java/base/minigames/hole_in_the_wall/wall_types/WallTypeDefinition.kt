package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.wall_types.actual_types.DoominatorWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.EarlyDecayedWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.FlipperWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.JumpscareWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.MorphWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.RammingWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.RepeaterWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.ReturningWall
import base.minigames.hole_in_the_wall.wall_types.actual_types.RotatorWall

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
    val assignmentChance: Int,
    private val createWallType: () -> WallType,
    val description: String,
) {
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
        assignmentChance = 30,
        mutuallyExclusiveGroup = WallTypeMutuallyExclusiveGroup.TRAVEL_LIFESPAN_MODIFIER,
        createWallType = ::RammingWall,
    ),
    JUMPSCARE(
        id = JumpscareWall.ID,
        description = JumpscareWall.DESCRIPTION,
        assignmentChance = 50,
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
    FLIPPER(
        id = FlipperWall.ID,
        description = FlipperWall.DESCRIPTION,
        assignmentChance = 50,
        createWallType = ::FlipperWall,
    ),
    RETURNING(
        id = ReturningWall.ID,
        description = ReturningWall.DESCRIPTION,
        assignmentChance = 40,
        createWallType = ::ReturningWall,
    ),
    ROTATOR(
        id = RotatorWall.ID,
        description = RotatorWall.DESCRIPTION,
        assignmentChance = 40,
        createWallType = ::RotatorWall,
    );

    /**
     * Whether this wall type can be selected for a new game.
     * Commented entries will make those wall types not be able to be selected
    */
    val isTurned: Boolean
        get() = name in setOf(
//            "DUMMY", //Not a real name
//            "EARLY_DECAYED",
//            "REPEATER",
//            "JUMPSCARE",
//            "RAMMING",
//            "DOOMINATOR",
//            "MORPH",
//            "FLIPPER",
//            "RETURNING",
            "ROTATOR"
        )

    /**
     * Whether this enabled wall type is guaranteed to be included in every game's pool.
     *
     * This is primarily for testing and debugging. Priority types are placed after Psych, and before
     * the remaining enabled wall types are randomly selected.
     */
    val isPriority: Boolean
        get() = name in setOf(
            "DUMMY", //Not a real name
//            "EARLY_DECAYED",
//            "REPEATER",
//            "JUMPSCARE",
//            "RAMMING",
//            "DOOMINATOR",
//            "MORPH",
//            "FLIPPER",
//            "RETURNING",
//            "ROTATOR"
        )

    fun create(): WallType = createWallType()
}

/** Types in the same group may not be assigned to the same wall together. */
internal enum class WallTypeMutuallyExclusiveGroup {
    TRAVEL_LIFESPAN_MODIFIER,
}
