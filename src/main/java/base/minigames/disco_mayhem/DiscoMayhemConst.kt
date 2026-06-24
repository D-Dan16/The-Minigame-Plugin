// src/main/java/me/stavgordeev/plugin/MinigameConstants.java
package base.minigames.disco_mayhem

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World

object DiscoMayhemConst {
    val WORLD: World = Bukkit.getWorld("world")!!

    const val NUKE_AREA_RADIUS = 50

    val GAME_START_LOCATION: Location = Location(WORLD, 0.0, 150.0, 0.0)
    val INIT_FLOOR_LOCATION: Location = GAME_START_LOCATION.clone().add(0.0, 8.0, 0.0)
    val PLAYER_TP_LOCATION: Location = GAME_START_LOCATION.clone().add(0.0, 11.0, 0.0)
    const val MIN_INTERVAL: Int = 1

    // Constants that define boundaries for random values for changing floor logic.
    object FloorLogic {
        const val DELAY_TO_SELECT_A_FLOOR_MATERIAL: Int = 25
        const val DURATION_OF_STAYING_IN_A_FLOOR_WITH_ONLY_CHOSEN_MATERIAL: Int = 60

        val LIST_OF_FLOOR_MATERIALS: Array<Material> = arrayOf(
            Material.RED_WOOL,
            Material.BLUE_WOOL,
            Material.GREEN_WOOL,
            Material.PURPLE_WOOL,
            Material.ORANGE_WOOL,
            Material.YELLOW_WOOL,
            Material.LIME_WOOL,
            Material.CYAN_WOOL,
            Material.LIGHT_BLUE_WOOL
        )
        val DEFAULT_FLOOR_BLOCK_TYPES: Array<Material> =
            arrayOf(Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.PURPLE_WOOL)

        // Constants that define boundaries for where a new floor can spawn.
        object NewFloorSpawnBoundaries {
            const val UPPER_BOUND_X_CENTER: Int = 10
            const val LOWER_BOUND_X_CENTER: Int = 5
            const val UPPER_BOUND_Z_CENTER: Int = 10
            const val LOWER_BOUND_Z_CENTER: Int = 5
            const val UPPER_BOUND_Y_CENTER: Int = 1
            const val LOWER_BOUND_Y_CENTER: Int = -3
        }

        object FloorSize {
            const val UPPER_BOUND_X_RADIUS: Int = 7
            const val LOWER_BOUND_X_RADIUS: Int = 3
            const val UPPER_BOUND_Z_RADIUS: Int = 7
            const val LOWER_BOUND_Z_RADIUS: Int = 3
        }

        // Constants that define boundaries for how often a certain floor changes its materials.
        object ChangingFloor {
            const val UPPER_BOUND_START_INTERVAL: Int = 25
            const val LOWER_BOUND_START_INTERVAL: Int = 20
            const val UPPER_BOUND_STOP_INTERVAL: Int = 15
            const val LOWER_BOUND_STOP_INTERVAL: Int = 10

            const val DELAY_TO_DECREASE_INTERVAL: Int = 20 * 15
        }
    }
}