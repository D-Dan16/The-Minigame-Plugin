package base.listeners

import base.utils.extensions_for_classes.getOnClickListener
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class ItemClickListener : Listener {
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        
        // We trigger the listener on right-click actions (with or without block)
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            val listener = item.getOnClickListener()
            if (listener != null) {
                listener.invoke(event.player)
                event.isCancelled = true
            }
        }
    }
}
