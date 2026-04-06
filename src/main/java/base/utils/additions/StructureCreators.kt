package base.utils.additions

import org.bukkit.Location
import org.bukkit.Material

/**
 * Initializes the floor under the player to a specific material.
 * @param xLengthRad The x radius of the floor
 * @param zLengthRad The z radius of the floor
 * @param material The material to set the floor to
 * @param center The center of the floor
 */
fun initFloor(
    xLengthRad: Int,
    zLengthRad: Int,
    material: Material,
    center: Location,
) {
    // Initialize the floor under the player to stone 1 block at a time. The floor is a rectangle with side lengths 2*xLengthRad+1 and 2*zLengthRad+1.
    for (x in -xLengthRad..xLengthRad) {
        for (z in -zLengthRad..zLengthRad) {
            val selectedLocation = Location(center.world, center.x + x, center.y, center.z + z)
            selectedLocation.block.type = material
        }
    }
}


fun createBoxOutline(
    xLengthRad: Int,
    zLengthRad: Int,
    height: Int,
    material: Material,
    center: Location,
) {
    for (layer in 0..<height) {
        for (x in -xLengthRad..xLengthRad) {
            val firstStripe = Location(center.world, center.x + x, center.y + layer, center.z - zLengthRad)
            firstStripe.block.type = material
            val secondStripe = Location(center.world, center.x + x, center.y + layer, center.z + zLengthRad)
            secondStripe.block.type = material
        }

        for (z in -zLengthRad..zLengthRad) {
            val firstStripe = Location(center.world, center.x - xLengthRad, center.y + layer, center.z + z)
            firstStripe.block.type = material
            val secondStripe = Location(center.world, center.x + xLengthRad, center.y + layer, center.z + z)
            secondStripe.block.type = material
        }
    }
}