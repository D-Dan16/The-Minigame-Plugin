package base.minigames.hole_in_the_wall.wall_types

import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState
import org.bukkit.Particle

/**
 * This wall type spawns at one of the stop signs.
 * Instead of spawning immediately, it highlights that it is about to spawn by making the area it'll spawn at with SOUL_FIRE_FLAME particles, warning the players of its existence.
 * after it spawns, it takes a small time to actually start moving, specifically [base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed]*2 Ticks.
 */
class JumpscareWall : WallType() {
    override val id: String = "jumpscare"
    override fun toString(): String = id

    /**
     * Both phases are measured from the current speed when the phase begins, so a speed change
     * later in the game cannot shorten a warning or make an already-spawned wall start early.
     */
    internal fun warningDurationTicks(): Long = GameLoopRuntimeState.wallSpeed * 7L

    internal fun movementStartDelayTicks(): Int = GameLoopRuntimeState.wallSpeed * 2

    /** True only after this queued wall has been announced and is counting down to its spawn. */
    internal var isAwaitingSpawnAfterWarning: Boolean = false
        private set

    /** Starts the warning only once the spawner has confirmed that this wall can enter safely. */
    internal fun beginSpawnWarning() {
        if (isAwaitingSpawnAfterWarning) return

        isAwaitingSpawnAfterWarning = true
        repeatedlyEmitParticlesBeforeSpawn(Particle.EGG_CRACK, taskInterval = 10L)
    }
}
