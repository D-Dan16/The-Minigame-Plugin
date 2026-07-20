package base.minigames.hole_in_the_wall.wall_types

import org.bukkit.Color
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
    private val createWallType: () -> WallType,
    val description: String,
) {
    EARLY_DECAYED(
        displayName = "Early Decayed",
        mutuallyExclusiveGroup = WallTypeMutuallyExclusiveGroup.TRAVEL_LIFESPAN_MODIFIER,
        createWallType = ::EarlyDecayedWall,
        description = "A wall with a shorter than normal lifespan, which makes it decay in the middle platform"
    ),
    RAMMING(
        displayName = "Ramming",
        mutuallyExclusiveGroup = WallTypeMutuallyExclusiveGroup.TRAVEL_LIFESPAN_MODIFIER,
        createWallType = ::RammingWall,
        description = "A long-lived wall that rams opposing walls out of the arena.",
    ),
    JUMPSCARE(
        displayName = "Jumpscare",
        createWallType = ::JumpscareWall,
        description = "A wall that spawns very close to the platform, emitting green particles to indicate its very soon presence"
    ),
    PSYCH(
        displayName = "Psych",
        createWallType = { PsychWall(Random.nextBoolean()) },
        description = "A wall that can stop before it reaches the platform. May decay, or stay. Always comes in groups of walls"
    ),
    ;

    fun create(): WallType = createWallType()
}

/** Types in the same group may not be assigned to the same wall together. */
internal enum class WallTypeMutuallyExclusiveGroup {
    TRAVEL_LIFESPAN_MODIFIER,
}