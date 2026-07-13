package base.minigames.hole_in_the_wall

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.game_loop.walls.WallPack
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.startOnFinalPlatformStage
import base.minigames.hole_in_the_wall.game_loop.walls.wallPackDifficulties
import base.utils.other.BuildLoader
import com.sk89q.worldedit.regions.Region
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.Objects

internal lateinit var selectedMapBaseFile: File
/** The platform stages for a given map. */
internal lateinit var platformSchematics: Array<File>
/** The map schematic that is being played. */
internal lateinit var mapSchematic: File
/** The region of the map schematic that is being played. Used to nuke the area gracefully. */
internal lateinit var mapSchematicRegion : Region
/** The region of the currently loaded platform stage. */
internal var currentPlatformRegion: Region? = null
/** The currently loaded platform stage. */
internal var currentPlatformStageIndex: Int = 0

internal fun HoleInTheWall.arenaPreparer() {
    fun pickWallPackDifficultyFiles(component: File): WallPack {
        val easyFiles = File(component, HITWConst.EASY_WALLPACK_FOLDER)
        val mediumFiles = File(component, HITWConst.MEDIUM_WALLPACK_FOLDER)
        val hardFiles = File(component, HITWConst.HARD_WALLPACK_FOLDER)
        val veryHardFiles = File(component, HITWConst.VERY_HARD_WALLPACK_FOLDER)

        if (!easyFiles.exists() || !mediumFiles.exists() || !hardFiles.exists() || !veryHardFiles.exists()) {
            throw IOException("Wall pack ${component.absolutePath} must contain E, M, H, and VH schematics")
        }

        return WallPack(
            easyFiles.listFiles().toList(),
            mediumFiles.listFiles().toList(),
            hardFiles.listFiles().toList(),
            veryHardFiles.listFiles().toList()
        )
    }

    fun getGameBaseFolder(): File {
        check(plugin is MinigamePlugin) { "Invalid plugin type" }
        val baseFolder: File = plugin.getSchematicsBaseFolder(MinigamePlugin.Companion.MinigameType.HOLE_IN_THE_WALL)
        Objects.requireNonNull(baseFolder, "Game base folder not found")
        if (!baseFolder.exists() || !baseFolder.isDirectory) {
            throw IOException("Base folder is missing or not a directory: ${baseFolder.absolutePath}")
        }
        return baseFolder
    }

    fun loadMapSchematics(baseFolder: File) {
        // Check if the base folder exists and is a directory. If not, throw an exception. Otherwise, proceed to find the map schematics.
        if (baseFolder.listFiles().isNullOrEmpty())
            throw IOException("Could not list files in: ${baseFolder.absolutePath}")

        val files: Array<File> = baseFolder.listFiles()

        selectedMapBaseFile = Arrays.stream(files)
            .filter { file: File -> file.isDirectory() && file.getName() == mapName }
            .findFirst()
            .orElse(null)
            ?: throw IOException("No map schematics found in base folder named ${baseFolder.name} with map name $mapName")
    }

    fun processMapComponents() {
        val mapComponents: Array<out File?> = selectedMapBaseFile.listFiles() ?: throw IOException("No files found in map base folder named ${selectedMapBaseFile.name}")
        for (component in mapComponents) {
            when (component?.name?.lowercase()) {
                HITWConst.PLATFORMS_FOLDER -> {
                    platformSchematics = component.listFiles()
                        ?.filterNotNull()
                        ?.sortedBy { it.name.lowercase() }
                        ?.toTypedArray()
                        ?: throw IOException("No platform schematics found in ${component.name}")
                }
                HITWConst.WALLPACK_FOLDER -> {
                    wallPackDifficulties = pickWallPackDifficultyFiles(component)
                }
                HITWConst.MAP_FOLDER -> {
                    mapSchematic = component.listFiles()?.firstOrNull()
                        ?: throw IOException("No map schematic found in ${component.name}")
                }
            }
        }
    }

    try {
        val baseFolder = getGameBaseFolder()
        loadMapSchematics(baseFolder)
        processMapComponents()
    } catch (e: Exception) {
        plugin.logger.severe("Failed to load minigame: ${e.message}")
        endGame()
        return
    }

    // Load the map schematic (the deco arena) and store the region of the map
    mapSchematicRegion = BuildLoader.loadSchematicByFile(mapSchematic, HITWConst.Locations.CENTER_OF_MAP)
    // Load the requested initial platform stage.
    val initialPlatformIndex = if (startOnFinalPlatformStage) platformSchematics.lastIndex else 0
    currentPlatformRegion = BuildLoader.loadSchematicByFile(platformSchematics[initialPlatformIndex], HITWConst.Locations.PLATFORM)
    currentPlatformStageIndex = initialPlatformIndex
    startOnFinalPlatformStage = false
}

internal fun deleteArena() {
    if (::mapSchematicRegion.isInitialized)
        BuildLoader.deleteSchematic(mapSchematicRegion)
}
