package base.minigames.hole_in_the_wall.arena

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.utils.other.BuildLoader
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.world.block.BlockTypes
import org.bukkit.Location
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Places a static, spaced-out visual preview of a HITW wall pack. */
internal object WallPackPreviewer {
    /** Wall schematics are at most 14 blocks wide; this leaves 14 clear blocks between them. */
    private const val WALL_ORIGIN_SPACING = 28
    private const val ROW_ORIGIN_SPACING = 12
    private const val DUMMY_WALL_WIDTH = 14
    private const val DUMMY_WALL_HEIGHT = 5
    private const val DUMMY_WALL_DEPTH = 2
    /** WorldEdit origin: bottom row, seventh column from the left, on the visible wall layer. */
    private const val WALL_ORIGIN_X_OFFSET = 6
    private const val WALL_ORIGIN_Z_OFFSET = 1
    private const val WALLS_PER_DIFFICULTY = 5
    private val difficultyFolders = listOf("easy", "medium", "hard", "very_hard")
    private data class PreviewWall(
        val sourceFile: File,
        val region: Region,
        /** A visual coordinate guide placed outside [region], so it is never saved into the schematic. */
        val guideLine: GuideLine,
    )

    private data class GuideLine(val blocks: List<Location>, val signs: List<Location>)

    private var previewWalls: List<PreviewWall> = emptyList()

    fun show(player: Player, mapName: String): String? {
        val wallPackFolder = File(
            MinigamePlugin.plugin.getSchematicsBaseFolder(MinigamePlugin.Companion.MinigameType.HOLE_IN_THE_WALL),
            "$mapName/${HITWConst.ArenaFiles.WALLPACK_FOLDER}",
        )
        return show(player, wallPackFolder, "Wall pack '$mapName'")
    }

    private fun show(player: Player, wallPackFolder: File, wallPackDescription: String): String? {
        if (previewWalls.isNotEmpty()) {
            return "A wall-pack preview already exists. Run /mg_hole_in_the_wall clear_wallpack_preview first."
        }

        if (!wallPackFolder.isDirectory) {
            return "$wallPackDescription was not found."
        }

        val rows = difficultyFolders.map { difficulty ->
            val folder = File(wallPackFolder, difficulty)
            val files = folder.listFiles { file -> file.isFile && file.extension.equals("schem", ignoreCase = true) }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
            difficulty to files
        }
        val missingDifficulty = rows.firstOrNull { it.second.isEmpty() }?.first
        if (missingDifficulty != null) {
            return "$wallPackDescription has no .schem files in '$missingDifficulty'."
        }

        val anchor = player.location.block.location
        val walls = mutableListOf<PreviewWall>()
        try {
            rows.forEachIndexed { rowIndex, (_, files) ->
                var nextX = anchor.blockX
                val rowZ = anchor.blockZ + rowIndex * ROW_ORIGIN_SPACING

                files.forEach { file ->
                    val region = BuildLoader.loadSchematicByFile(
                        file,
                        Location(anchor.world, nextX.toDouble(), anchor.blockY.toDouble(), rowZ.toDouble()),
                    )
                    walls += PreviewWall(
                        sourceFile = file,
                        region = region,
                        guideLine = placeGuideLine(region),
                    )
                    nextX += WALL_ORIGIN_SPACING
                }
            }
        } catch (exception: Exception) {
            walls.forEach { wall ->
                BuildLoader.deleteSchematic(wall.region)
                clearGuideLine(wall.guideLine)
            }
            return "Could not create the preview: ${exception.message}"
        }

        previewWalls = walls
        ArenaComponentBuildingMaterials.giveWallPackMaterialsTo(player)
        return null
    }

    /** Creates a new editable pack of twenty 14x5x2 slime-block walls, then previews it. */
    fun create(player: Player): String? {
        if (previewWalls.isNotEmpty()) {
            return "A wall-pack preview already exists. Run /mg_hole_in_the_wall clear_wallpack_preview first."
        }

        val wallPackFolder = File(Bukkit.getWorldContainer(), "map_component_creations/wallpack")
        if (wallPackFolder.exists()) {
            return "A staged wall pack already exists at 'map_component_creations/wallpack'."
        }

        try {
            difficultyFolders.forEach { difficulty ->
                val folder = File(wallPackFolder, difficulty)
                if (!folder.mkdirs()) {
                    throw IllegalStateException("Could not create folder '${folder.name}'.")
                }

                repeat(WALLS_PER_DIFFICULTY) { wallIndex ->
                    val file = File(folder, "Wall-${difficultyPrefix(difficulty)}${wallIndex + 1}.schem")
                    BuildLoader.saveClipboardAsSchematic(createDummyWallClipboard(), file)
                }
            }
        } catch (exception: Exception) {
            wallPackFolder.deleteRecursively()
            return "Could not create the wall pack: ${exception.message}"
        }

        return show(player, wallPackFolder, "Staged wall pack")
    }

    /** Saves all manually edited preview walls back to the exact files that were previewed. */
    fun save(): String? {
        if (previewWalls.isEmpty()) {
            return "No wall-pack preview exists. Run /mg_hole_in_the_wall preview_wallpack <map> first."
        }

        val temporaryFiles = mutableListOf<Pair<File, File>>()
        try {
            previewWalls.forEach { wall ->
                val temporaryFile = File(
                    wall.sourceFile.parentFile,
                    ".${wall.sourceFile.nameWithoutExtension}.preview-save.${wall.sourceFile.extension}",
                )
                BuildLoader.saveWorldRegionAsSchematic(
                    wall.region,
                    wallSchematicOrigin(wall.region),
                    temporaryFile,
                    wall.sourceFile,
                )
                temporaryFiles += temporaryFile to wall.sourceFile
            }

            temporaryFiles.forEach { (temporaryFile, sourceFile) ->
                Files.move(
                    temporaryFile.toPath(),
                    sourceFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }
        } catch (exception: Exception) {
            temporaryFiles.forEach { (temporaryFile, _) -> Files.deleteIfExists(temporaryFile.toPath()) }
            return "Could not save the edited wall pack: ${exception.message}"
        }

        return null
    }

    fun clear(): Int {
        val walls = previewWalls
        walls.forEach { wall ->
            BuildLoader.deleteSchematic(wall.region)
            clearGuideLine(wall.guideLine)
        }
        previewWalls = emptyList()
        return walls.size
    }

    /** Places an alternating, numbered guide row outside the saved schematic bounds. */
    private fun placeGuideLine(region: Region): GuideLine {
        val min = region.minimumPoint
        val max = region.maximumPoint
        val y = min.y() - 1
        val z = min.z() + 1
        val signs = mutableListOf<Location>()

        val blocks = (min.x()..max.x()).mapIndexed { index, x ->
            Location(MinigamePlugin.world, x.toDouble(), y.toDouble(), z.toDouble()).also { location ->
                location.block.type = if ((x - min.x()) % 2 == 0) Material.WHITE_CONCRETE else Material.BLACK_CONCRETE

                val signLocation = location.clone().add(0.0, 0.0, 1.0)
                val signBlock = signLocation.block
                signBlock.setType(Material.DARK_OAK_WALL_SIGN, false)
                (signBlock.blockData as Directional).apply {
                    facing = BlockFace.SOUTH
                    signBlock.setBlockData(this, false)
                }
                (signBlock.state as Sign).apply {
                    setLine(0, (index + 1).toString())
                    isGlowingText = true
                    update(true, false)
                }
                signs += signLocation
            }
        }
        return GuideLine(blocks, signs)
    }

    private fun clearGuideLine(guideLine: GuideLine) {
        guideLine.signs.forEach { it.block.type = Material.AIR }
        guideLine.blocks.forEach { it.block.type = Material.AIR }
    }

    private fun createDummyWallClipboard(): BlockArrayClipboard {
        val region = CuboidRegion(
            BlockVector3.ZERO,
            BlockVector3.at(DUMMY_WALL_WIDTH - 1, DUMMY_WALL_HEIGHT - 1, DUMMY_WALL_DEPTH - 1),
        )
        return BlockArrayClipboard(region).apply {
            origin = BlockVector3.at(WALL_ORIGIN_X_OFFSET, 0, WALL_ORIGIN_Z_OFFSET)
            // The rear layer is reserved for pistons; the visible wall itself is one block thick.
            region.forEach { position ->
                if (position.z() == DUMMY_WALL_DEPTH - 1) {
                    setBlock(position, BlockTypes.SLIME_BLOCK!!.defaultState)
                }
            }
        }
    }

    /** Uses the same origin convention as a WorldEdit save made from bottom-row column 7. */
    private fun wallSchematicOrigin(region: Region): BlockVector3 = BlockVector3.at(
        region.minimumPoint.x() + WALL_ORIGIN_X_OFFSET,
        region.minimumPoint.y(),
        region.minimumPoint.z() + WALL_ORIGIN_Z_OFFSET,
    )

    private fun difficultyPrefix(difficulty: String): String = when (difficulty) {
        "easy" -> "E"
        "medium" -> "M"
        "hard" -> "H"
        "very_hard" -> "VH"
        else -> error("Unknown difficulty folder: $difficulty")
    }
}
