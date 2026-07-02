package base.minigames.hole_in_the_wall

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.game_loop_handlers.wallPackSchematics
import base.utils.other.BuildLoader
import com.sk89q.worldedit.regions.Region
import net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger
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

internal fun HoleInTheWall.arenaPreparer() {
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
            when (component?.getName()) {
                HITWConst.PLATFORMS_FOLDER -> {
                    platformSchematics = component.listFiles() ?: throw IOException("No platform schematics found in ${component.name}")
                }
                HITWConst.WALLPACK_FOLDER -> {
                    wallPackSchematics = component.listFiles() ?: throw IOException("No wall pack schematics found in ${component.name}")
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
    }


    // Load the map schematic (the deco arena) and store the region of the map
    mapSchematicRegion = BuildLoader.loadSchematicByFile(mapSchematic, HITWConst.Locations.CENTER_OF_MAP)
    // Load the platform schematic (the platform that players will stand on)
    BuildLoader.loadSchematicByFile(platformSchematics[2], HITWConst.Locations.PLATFORM)
}

internal fun deleteArena() {
    if (::mapSchematicRegion.isInitialized)
        BuildLoader.deleteSchematic(mapSchematicRegion)
}
