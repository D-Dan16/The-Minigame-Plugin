package base.listeners

import base.minigames.parkour_dash.ParkourDash
import org.bukkit.entity.FallingBlock
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityChangeBlockEvent

class PhysicsListener(private val parkourDash: ParkourDash) : Listener {
    @EventHandler
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!parkourDash.isGameRunning) return

        if (event.entity is FallingBlock && event.block.type.hasGravity()) {
            event.isCancelled = true
        }
    }
}
