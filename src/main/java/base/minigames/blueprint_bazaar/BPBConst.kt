package base.minigames.blueprint_bazaar

import base.utils.additions.Direction
import com.sk89q.worldedit.math.BlockVector3
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.util.BoundingBox

object BPBConst {
    val WORLD: World = Bukkit.getWorld("world") ?: throw IllegalStateException("World 'world' not found. Please ensure it exists in the server configuration.")

    /**
     * Represents the raw materials used in the game.
     * Only those materials will be given to the players for building, and they shall be used to craft more complex materials.
     * An example of a basic material is Stone, which can be used to craft Stone Bricks.
      */
    enum class BasicMaterials {
        // Colours (Glass and Concrete)
        WHITE_CONCRETE, BLACK_CONCRETE,
        RED_CONCRETE, ORANGE_CONCRETE, YELLOW_CONCRETE, LIME_CONCRETE,
        LIGHT_BLUE_CONCRETE, BLUE_CONCRETE, PURPLE_CONCRETE, PINK_CONCRETE,
        WHITE_STAINED_GLASS, BLACK_STAINED_GLASS,
        RED_STAINED_GLASS, ORANGE_STAINED_GLASS, YELLOW_STAINED_GLASS, LIME_STAINED_GLASS,
        LIGHT_BLUE_STAINED_GLASS, BLUE_STAINED_GLASS, PURPLE_STAINED_GLASS, PINK_STAINED_GLASS,

        // Garden
        FLOWER_POTS ,DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET, RED_TULIP, LILY_OF_THE_VALLEY,
        ORANGE_TULIP, WHITE_TULIP, PINK_TULIP, OXEYE_DAISY, CORNFLOWER,
        MOSS_BLOCK, AZALEA, SAND,RED_SAND,

        // Ores
        REDSTONE_BLOCK, DIAMOND_BLOCK, EMERALD_BLOCK, GOLD_BLOCK, IRON_BLOCK, COAL_BLOCK, COPPER_BLOCK, OXIDIZED_COPPER,

        // Stone
        COBBLESTONE, STONE, DIORITE, ANDESITE, GRANITE,

        // Lumber
        OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG,

        // Bricks
        BRICKS, PRISMARINE_BRICKS,

        // Nether
        QUARTZ_BLOCK, WARPED_WART_BLOCK, NETHER_WART_BLOCK, NETHER_BRICKS, BLACKSTONE;

        companion object {
            fun contains(material: Material) : Boolean {
                entries.forEach { entry ->
                    if (entry.toString() == material.name) return true
                }
                return false
            }
            fun contains(materials: Collection<Material>) : Boolean {
                return materials.all { contains(it) }
            }
        }
    }

    /**
     * A map that represents the conversion of stripped logs to their corresponding regular logs. used when calculating the material list the players get for a given build
     */
    val StripedLogsToLogs: Map<Material, Material> = mapOf(
        Material.STRIPPED_OAK_LOG to Material.OAK_LOG,
        Material.STRIPPED_ACACIA_LOG to Material.ACACIA_LOG,
        Material.STRIPPED_BIRCH_LOG to Material.BIRCH_LOG,
        Material.STRIPPED_JUNGLE_LOG to Material.JUNGLE_LOG,
        Material.STRIPPED_SPRUCE_LOG to Material.SPRUCE_LOG
    )

    object Locations {
        val GAME_START_LOCATION: Location = Location(WORLD, 0.0, 150.0, 0.0)
        val CENTER_BUILD_SHOWCASE_PLOT = Location(WORLD, GAME_START_LOCATION.x + 20, GAME_START_LOCATION.y + 1, GAME_START_LOCATION.z)
        val CENTER_BUILD_PLOT_OFFSET: BlockVector3 = BlockVector3.at(-10.0, 0.0, 0.0)

        const val GAME_AREA_RADIUS: Int = 30

        val MIN_CORNER_OF_GAME_AREA: Location = Location(WORLD, -GAME_AREA_RADIUS.toDouble(), 150.0, -GAME_AREA_RADIUS.toDouble())

        val MAX_CORNER_OF_GAME_AREA: Location = Location(WORLD, GAME_AREA_RADIUS.toDouble(), 160.0, GAME_AREA_RADIUS.toDouble())
        val ARENA_REGION = BoundingBox.of(
            MIN_CORNER_OF_GAME_AREA,
            MAX_CORNER_OF_GAME_AREA
        )
    }

    object Timers {
        const val DELAY_BETWEEN_SHOWCASING_BUILDS = 80L
    }

    object Build {
        val buildFacingDirection: Direction = Direction.NORTH
    }
}
