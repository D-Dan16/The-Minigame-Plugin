package base.minigames.hole_in_the_wall.arena

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.WallPack
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.initializePlatformProgression
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.WallDifficultyPack
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.wallPackDifficulties
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

internal fun HoleInTheWall.arenaPreparer() {
    fun pickWallPackDifficultyFiles(component: File): WallPack {
        val easyFiles = File(component, HITWConst.ArenaFiles.EASY_WALLPACK_FOLDER)
        val mediumFiles = File(component, HITWConst.ArenaFiles.MEDIUM_WALLPACK_FOLDER)
        val hardFiles = File(component, HITWConst.ArenaFiles.HARD_WALLPACK_FOLDER)
        val veryHardFiles = File(component, HITWConst.ArenaFiles.VERY_HARD_WALLPACK_FOLDER)

        if (!easyFiles.exists() || !mediumFiles.exists() || !hardFiles.exists() || !veryHardFiles.exists()) {
            throw IOException("Wall pack ${component.absolutePath} must contain E, M, H, and VH schematics")
        }

        return WallPack(
            WallDifficultyPack(easyFiles.listFiles().toList(), HITWConst.WallDifficulty.EASY),
            WallDifficultyPack(mediumFiles.listFiles().toList(), HITWConst.WallDifficulty.MEDIUM),
            WallDifficultyPack(hardFiles.listFiles().toList(), HITWConst.WallDifficulty.HARD),
            WallDifficultyPack(veryHardFiles.listFiles().toList(), HITWConst.WallDifficulty.VERY_HARD)
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
                HITWConst.ArenaFiles.PLATFORMS_FOLDER -> {
                    platformSchematics = component.listFiles()
                        ?.filterNotNull()
                        ?.filter { it.isFile && it.extension.equals("schem", ignoreCase = true) }
                        ?.sortedBy { it.name.lowercase() }
                        ?.toTypedArray()
                        ?: throw IOException("No platform schematics found in ${component.name}")
                    if (platformSchematics.size != 3) {
                        throw IOException("Platform folder ${component.absolutePath} must contain exactly 3 .schem files")
                    }
                }
                HITWConst.ArenaFiles.WALLPACK_FOLDER -> {
                    wallPackDifficulties = pickWallPackDifficultyFiles(component)
                }
                HITWConst.ArenaFiles.MAP_FOLDER -> {
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
    val initialPlatformIndex = initializePlatformProgression(platformSchematics.size)
    currentPlatformRegion = BuildLoader.loadSchematicByFile(platformSchematics[initialPlatformIndex], HITWConst.Locations.PLATFORM)
}

internal fun deleteArena() {
    if (::mapSchematicRegion.isInitialized)
        BuildLoader.deleteSchematic(mapSchematicRegion)
}
