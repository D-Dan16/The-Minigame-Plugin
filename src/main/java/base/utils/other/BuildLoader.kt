package base.utils.other

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.WorldEditException
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.math.transform.AffineTransform
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.world.block.BlockState
import base.MinigamePlugin
import base.MinigamePlugin.Companion.plugin
import base.MinigamePlugin.Companion.world
import base.utils.additions.Direction
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.function.operation.Operation
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Skull
import org.bukkit.entity.FallingBlock
import org.bukkit.util.Vector
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

object BuildLoader {
    //--region ---------------Helper methods for loading schematics -//
    fun getClipboardHolderFromFile(file: File,location: Location?): ClipboardHolder {
        val format: ClipboardFormat = ClipboardFormats.findByFile(file) ?: throw IllegalArgumentException("Unsupported schematic format: ${file.name}")

        try {
            FileInputStream(file).use { fis ->
                format.getReader(fis).use { reader ->

                    // Read the clipboard from the file.
                    val clipboard: Clipboard = reader.read()


                    // If no location is provided, return the ClipboardHolder without applying any translation.
                    if (location == null) return ClipboardHolder(clipboard)

                    // Otherwise, read the clipboard and apply the transform based on the provided location. We are going to make a new clipboard with the same blocks, but shifted to the location provided.

                    val newOrigin = BlockVector3.at(location.blockX, location.blockY, location.blockZ)
                    val offset: BlockVector3 = newOrigin.subtract(clipboard.origin)

                    val newRegion: Region = CuboidRegion(clipboard.region.world,
                        clipboard.region.minimumPoint.add(offset),
                        clipboard.region.maximumPoint.add(offset)
                    )

                    val newClipboard = BlockArrayClipboard(newRegion)

                    // Copy block data into the new clipboard at the shifted positions
                    for (pos in clipboard.region) {
                        val shiftedPos: BlockVector3 = pos.add(offset)
                        val block: BlockState = clipboard.getBlock(pos)
                        newClipboard.setBlock(shiftedPos, block)
                    }

                    // Set the new origin to the location provided
                    newClipboard.origin = newOrigin

                    return ClipboardHolder(newClipboard)
                }
            }
        } catch (e: Exception) {
        val reason = when (e) {
            is IOException -> "I/O error"
            is WorldEditException -> "WorldEdit error"
            else -> "Unexpected error"
        }
        Bukkit.getLogger().severe("Failed to load schematic (${file.name}): $reason: ${e.message}")
        throw IllegalArgumentException("Failed to load clipboard from file: ${file.name}", e)
        }
    }

    fun applyDirectionToClipboardHolder(clipboardHolder: ClipboardHolder, direction: Direction) {
        // get the current direction the schematic is already facing, and based on that and the desired direction, calculate by how much to rotate the schematic.
        val rotation = getRotationForDirection(direction)
        // Rotate clipboard - Convert degrees to WorldEdit's 2D Y-axis rotation
        clipboardHolder.transform = AffineTransform().rotateY(rotation.toDouble())
    }

    fun mirrorClipboardHolder(clipboardHolder: ClipboardHolder, facingDirection: Direction) {
        val region: Region = clipboardHolder.clipboard.region

        val wallLongestLength: Int = when (facingDirection) {
            Direction.NORTH, Direction.SOUTH -> region.width
            Direction.EAST, Direction.WEST -> region.length
        }

        val mirrorTransform = when (facingDirection) {
            Direction.NORTH, Direction.SOUTH -> AffineTransform().scale(-1.0, 1.0, 1.0)
            Direction.EAST, Direction.WEST -> AffineTransform().scale(1.0, 1.0, -1.0)
        }

        // If the wall's length is even, it doesn't have a proper center. we need to move it so when we paste it, it won't have an offset.
        if (wallLongestLength % 2 == 0) {
            val offsetCorrection = when (facingDirection) {
                Direction.NORTH -> AffineTransform().translate(1.0, 0.0, 0.0)
                Direction.SOUTH -> AffineTransform().translate(-1.0, 0.0, 0.0)
                Direction.EAST -> AffineTransform().translate(0.0, 0.0, 1.0)
                Direction.WEST -> AffineTransform().translate(0.0, 0.0, -1.0)
            }


            clipboardHolder.transform = mirrorTransform.combine(offsetCorrection).combine(clipboardHolder.transform)
        } else {
            clipboardHolder.transform = mirrorTransform.combine(clipboardHolder.transform)
        }
    }

    fun loadSchematic(clipboardHolder: ClipboardHolder) {
        val adaptedWorld = BukkitAdapter.adapt(world)

        WorldEdit.getInstance()
            .newEditSessionBuilder()
            .world(adaptedWorld)
            .build()
            .use { editSession: EditSession ->

                editSession.setReorderMode(EditSession.ReorderMode.NONE)

                try {
                    val operation: Operation = clipboardHolder
                        .createPaste(editSession)
                        .to(clipboardHolder.clipboard.origin)
                        .ignoreAirBlocks(false)
                        .copyEntities(true)
                        .copyBiomes(false)
                        .build()

                    Operations.completeLegacy(operation)
                    editSession.close()

                } catch (exception: Exception) {
                    Bukkit.getLogger().warning("Error while pasting schematic: ${exception.message}")
                }
            }

        // Delay to ensure chunks + tile entities are fully applied
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            refreshPlayerHeads()
        }, 2L)
    }

    //TODO:  Make this work - currently player heads are still not rendering
    private fun refreshPlayerHeads() {
        for (chunk in world.loadedChunks) {
            for (state in chunk.tileEntities) {

                if (state is Skull) {
                    val block = state.block

                    // Re-apply the SAME block data
                    val data = block.blockData
                    block.blockData = data

                    // Then update state (no physics)
                    state.update(true, false)
                }
            }
        }
    }

    fun getRotatedRegion(clipboardHolder: ClipboardHolder): CuboidRegion {
        val region = clipboardHolder.clipboard.region
        val transform = clipboardHolder.transform
        val origin = clipboardHolder.clipboard.origin

        // Transform all 8 corners of the cuboid region
        val min = region.minimumPoint
        val max = region.maximumPoint
        val corners = listOf(
            BlockVector3.at(min.x(), min.y(), min.z()),
            BlockVector3.at(min.x(), min.y(), max.z()),
            BlockVector3.at(min.x(), max.y(), min.z()),
            BlockVector3.at(min.x(), max.y(), max.z()),
            BlockVector3.at(max.x(), min.y(), min.z()),
            BlockVector3.at(max.x(), min.y(), max.z()),
            BlockVector3.at(max.x(), max.y(), min.z()),
            BlockVector3.at(max.x(), max.y(), max.z())
        )

        val transformedPoints = corners.map { point ->
            val rel = point.subtract(origin)
            val transformed = transform.apply(rel.toVector3())
            origin.toVector3().add(transformed)
        }

        val minX = transformedPoints.minOf { it.x() }.toInt()
        val minY = transformedPoints.minOf { it.y() }.toInt()
        val minZ = transformedPoints.minOf { it.z() }.toInt()
        val maxX = transformedPoints.maxOf { it.x() }.toInt()
        val maxY = transformedPoints.maxOf { it.y() }.toInt()
        val maxZ = transformedPoints.maxOf { it.z() }.toInt()

        return CuboidRegion(
            BlockVector3.at(minX, minY, minZ),
            BlockVector3.at(maxX, maxY, maxZ)
        )
    }

    //endregion -------------------------------------------------------------

    /**
     * Modify and load a schematic via this method.
     * @param file the schematic file you want to paste it
     * @param location an optional parameter to specify where this shcem should be pasted. if nto specified, it will be pasted in the world position in was saved at.
     *
     */
    fun loadSchematicByFileAndDirection(
        file: File,
        location: Location? = null,
        direction: Direction,
        shouldBeMirrored: Boolean = false,
    ) : Region {
        val clipboardHolder = getClipboardHolderFromFile(file,location)

        applyDirectionToClipboardHolder(clipboardHolder, direction)

        if (shouldBeMirrored)
            mirrorClipboardHolder(clipboardHolder, direction)

        // Load the schematic into the world.
        loadSchematic(clipboardHolder)

        return getRotatedRegion(clipboardHolder)
    }

    fun loadSchematicByFile(
        file: File,
        location: Location? = null,
    ) : Region {
        val clipboardHolder = getClipboardHolderFromFile(file,location)

        // Load the schematic into the world.
        loadSchematic(clipboardHolder)

        return clipboardHolder.clipboard.region
    }

    fun loadSchematicByFileAndCoordinates(file: File, x: Int, y: Int, z: Int) {
        val location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
        loadSchematicByFile(file, location)
    }


    /**
     * Returns the rotation in degrees for the given direction.
     */
    private fun getRotationForDirection(direction: Direction): Int {
        return when (direction) {
            Direction.SOUTH -> 0
            Direction.EAST -> 90
            Direction.NORTH -> 180
            Direction.WEST -> 270
        }
    }

    fun deleteSchematic(firstCorner: BlockVector3, secondCorner: BlockVector3) {
        // Calculate the boundaries of the area to delete.
        val minX = min(firstCorner.x(), secondCorner.x())
        val maxX = max(firstCorner.x(), secondCorner.x())
        val minY = min(firstCorner.y(), secondCorner.y())
        val maxY = max(firstCorner.y(), secondCorner.y())
        val minZ = min(firstCorner.z(), secondCorner.z())
        val maxZ = max(firstCorner.z(), secondCorner.z())

        // Loop through the area and set all blocks to air.
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    world.getBlockAt(x, y, z).type = Material.AIR
                }
            }
        }
    }

    fun deleteSchematic(region: Region) {
        // Loop through the region and set all blocks to air.
        for (x in region.minimumPoint.x()..region.maximumPoint.x()) {
            for (y in region.minimumPoint.y()..region.maximumPoint.y()) {
                for (z in region.minimumPoint.z()..region.maximumPoint.z()) {
                    world.getBlockAt(x, y, z).type = Material.AIR
                }
            }
        }
    }


    /**
     * Disables gravity for all falling blocks in a certain radius around the center.
     *
     * @param center The center of the area to disable gravity in.
     * @param radius The radius of the area to disable gravity in.
     */
    private fun disableGravity(center: Location, radius: Int) {
        for (entity in center.getWorld()
            .getNearbyEntities(center, radius.toDouble(), radius.toDouble(), radius.toDouble())) {
            if (entity is FallingBlock) {
                entity.setGravity(false)
                entity.velocity = Vector(0, 0, 0)
            }
        }
    }

    /**
     * Enables gravity for all falling blocks in a certain radius around the center.
     *
     * @param center The center of the area to enable gravity in.
     * @param radius The radius of the area to enable gravity in.
     */
    private fun enableGravity(center: Location, radius: Int) {
        for (entity in center.getWorld()
            .getNearbyEntities(center, radius.toDouble(), radius.toDouble(), radius.toDouble())) {
            if (entity is FallingBlock) {
                entity.setGravity(true)
            }
        }
    }
}
