package base.minigames.hole_in_the_wall.arena

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Supplies used while editing staged HITW wall packs and platform stages. */
internal object ArenaComponentBuildingMaterials {
    private val platformMaterials = listOf(
        Material.GREEN_GLAZED_TERRACOTTA,
        Material.YELLOW_GLAZED_TERRACOTTA,
        Material.RED_GLAZED_TERRACOTTA,
    )
    private val wallPackMaterials = listOf(
        Material.PISTON,
        Material.OAK_PLANKS,
        Material.SLIME_BLOCK,
        Material.WAXED_OXIDIZED_CUT_COPPER,
        Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS,
        Material.WAXED_OXIDIZED_CUT_COPPER_SLAB,
        Material.NETHER_BRICK_FENCE,
        Material.WARPED_FENCE,
        Material.IRON_TRAPDOOR,
    )

    fun givePlatformMaterialsTo(player: Player) {
        val overflow = player.inventory.addItem(*platformMaterials.map { material ->
            ItemStack(material, 1)
        }.toTypedArray())

        overflow.values.forEach { item -> player.world.dropItemNaturally(player.location, item) }
    }


    fun giveWallPackMaterialsTo(player: Player) {
        val overflow = player.inventory.addItem(*wallPackMaterials.map { material ->
            ItemStack(material, 1)
        }.toTypedArray())

        overflow.values.forEach { item -> player.world.dropItemNaturally(player.location, item) }
    }
}