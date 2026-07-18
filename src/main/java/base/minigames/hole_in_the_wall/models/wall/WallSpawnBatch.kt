package base.minigames.hole_in_the_wall.models.wall

import java.util.concurrent.atomic.AtomicInteger

/**
 * Identity shared by every wall designed in one spawn wave.
 *
 * The object intentionally outlives the spawner's transient state and `upcomingWalls`, allowing
 * active walls to coordinate with their original wave after they have entered the arena.
 */
class WallSpawnBatch {
    companion object {
        private val nextId = AtomicInteger(1)
    }

    val id: Int = nextId.getAndIncrement()

    override fun toString(): String = "batch#$id"
}
