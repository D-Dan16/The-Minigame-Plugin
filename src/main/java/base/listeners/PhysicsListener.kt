package base.listeners

import base.minigames.parkour_dash.ParkourDash
import base.minigames.parkour_dash.courseRegions
import com.sk89q.worldedit.extent.transform.BlockTransformExtent
import com.sk89q.worldedit.math.BlockVector3
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.entity.EntityChangeBlockEvent

class PhysicsListener(private val parkourDash: ParkourDash) : Listener {
    @EventHandler
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!parkourDash.isGameRunning && courseRegions.isEmpty()) return
        if (isInCourse(event.block.x, event.block.y, event.block.z)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        if (!parkourDash.isGameRunning && courseRegions.isEmpty()) return

        if (isInCourse(event.block.x, event.block.y, event.block.z)) {
            // Allow bubble columns and water/bubble-related updates, but cancel general physics
            val type = event.block.type
            if (type == Material.BUBBLE_COLUMN || type == Material.WATER ||
                type == Material.SOUL_SAND || type == Material.MAGMA_BLOCK) {
                return
            }
            event.isCancelled = true
            return
        }
    }

    @EventHandler
    // Cancel water/lava spreading
    fun onBlockFromTo(event: BlockFromToEvent) {
        if (!parkourDash.isGameRunning && courseRegions.isEmpty()) return
        if (isInCourse(event.block.x, event.block.y, event.block.z)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onLeavesDecay(event: LeavesDecayEvent) {
        if (!parkourDash.isGameRunning && courseRegions.isEmpty()) return
        if (isInCourse(event.block.x, event.block.y, event.block.z)) {
            event.isCancelled = true
        }
    }

    private fun isInCourse(x: Int, y: Int, z: Int): Boolean {
        val vector = BlockVector3.at(x, y, z)
        return courseRegions.any { it.contains(vector) }
    }
}
