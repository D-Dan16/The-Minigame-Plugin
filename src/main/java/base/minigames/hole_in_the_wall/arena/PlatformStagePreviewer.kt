package base.minigames.hole_in_the_wall.arena

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.utils.other.BuildLoader
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.util.Direction
import com.sk89q.worldedit.world.block.BlockTypes
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Places the three editable HITW platform stages beside one another. */
internal object PlatformStagePreviewer {
    private const val PLATFORM_STAGE_COUNT = 3
    /** The largest stage is 12 blocks wide; this leaves 8 blocks between stages. */
    private const val PLATFORM_ORIGIN_SPACING = 20

    private data class PreviewPlatformStage(val sourceFile: File, val region: Region, val origin: Location)

    private var previewPlatformStages: List<PreviewPlatformStage> = emptyList()

    fun show(player: Player, mapName: String): String? {
        val platformsFolder = File(
            MinigamePlugin.plugin.getSchematicsBaseFolder(MinigamePlugin.Companion.MinigameType.HOLE_IN_THE_WALL),
            "$mapName/${HITWConst.ArenaFiles.PLATFORMS_FOLDER}",
        )
        return show(player, platformsFolder, "Platform stages for '$mapName'")
    }

    private fun show(player: Player, platformsFolder: File, description: String): String? {
        if (previewPlatformStages.isNotEmpty()) {
            return "A platform-stage preview already exists. Run /mg_hole_in_the_wall clear_platform_stage_preview first."
        }
        if (!platformsFolder.isDirectory) return "$description were not found."

        val files = platformsFolder.listFiles { file -> file.isFile && file.extension.equals("schem", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
        if (files.size != PLATFORM_STAGE_COUNT) {
            return "$description must contain exactly $PLATFORM_STAGE_COUNT .schem files."
        }

        val anchor = player.location.block.location
        val stages = mutableListOf<PreviewPlatformStage>()
        try {
            files.forEachIndexed { stageIndex, file ->
                val origin = Location(
                    anchor.world,
                    (anchor.blockX + stageIndex * PLATFORM_ORIGIN_SPACING).toDouble(),
                    anchor.blockY.toDouble(),
                    anchor.blockZ.toDouble(),
                )
                val region = BuildLoader.loadSchematicByFile(file, origin)
                stages += PreviewPlatformStage(file, region, origin)
            }
        } catch (exception: Exception) {
            stages.forEach { BuildLoader.deleteSchematic(it.region) }
            return "Could not create the platform-stage preview: ${exception.message}"
        }

        previewPlatformStages = stages
        ArenaComponentBuildingMaterials.givePlatformMaterialsTo(player)
        return null
    }

    /** Creates three editable 12x12 green glazed-terracotta platform stages, then previews them. */
    fun create(player: Player): String? {
        if (previewPlatformStages.isNotEmpty()) {
            return "A platform-stage preview already exists. Run /mg_hole_in_the_wall clear_platform_stage_preview first."
        }

        val platformsFolder = File(Bukkit.getWorldContainer(), "map_component_creations/platforms")
        if (platformsFolder.exists()) {
            return "Staged platform stages already exist at 'map_component_creations/platforms'."
        }

        try {
            if (!platformsFolder.mkdirs()) {
                throw IllegalStateException("Could not create the platforms folder.")
            }
            for (i in 0..2) {
                BuildLoader.saveClipboardAsSchematic(
                    createDummyPlatformClipboard(),
                    File(platformsFolder, "Platform-${i + 1}.schem"),
                )
            }
        } catch (exception: Exception) {
            platformsFolder.deleteRecursively()
            return "Could not create the platform stages: ${exception.message}"
        }

        return show(player, platformsFolder, "Staged platform stages")
    }

    /** Saves each edited stage back to the schematic from which it was loaded. */
    fun save(): String? {
        if (previewPlatformStages.isEmpty()) {
            return "No platform-stage preview exists. Run /mg_hole_in_the_wall preview_platform_stages <map> first."
        }

        val temporaryFiles = mutableListOf<Pair<File, File>>()
        try {
            previewPlatformStages.forEach { stage ->
                val temporaryFile = File(
                    stage.sourceFile.parentFile,
                    ".${stage.sourceFile.nameWithoutExtension}.preview-save.${stage.sourceFile.extension}",
                )
                BuildLoader.saveWorldRegionAsSchematic(
                    stage.region,
                    BlockVector3.at(stage.origin.blockX, stage.origin.blockY, stage.origin.blockZ),
                    temporaryFile,
                    stage.sourceFile,
                )
                temporaryFiles += temporaryFile to stage.sourceFile
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
            return "Could not save the edited platform stages: ${exception.message}"
        }

        return null
    }

    fun clear(): Int {
        val stages = previewPlatformStages
        stages.forEach { BuildLoader.deleteSchematic(it.region) }
        previewPlatformStages = emptyList()
        return stages.size
    }

    private fun createDummyPlatformClipboard(): BlockArrayClipboard {
        val region = CuboidRegion(BlockVector3.ZERO, BlockVector3.at(11, 0, 11))
        val platformBlock = BlockTypes.GREEN_GLAZED_TERRACOTTA!!.defaultState
            .with(BlockTypes.GREEN_GLAZED_TERRACOTTA!!.getProperty("facing"), Direction.NORTH)

        return BlockArrayClipboard(region).apply {
            origin = BlockVector3.ZERO
            region.forEach { position ->
                if (position.x() == 0 || position.x() == 11 || position.z() == 0 || position.z() == 11) {
                    setBlock(position, platformBlock)
                }
            }
        }
    }
}
