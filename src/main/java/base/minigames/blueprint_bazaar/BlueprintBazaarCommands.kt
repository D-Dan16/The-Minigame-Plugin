package base.minigames.blueprint_bazaar

import base.commands.MinigameCommandsSkeleton
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class BlueprintBazaarCommands(private val blueprintbazaar: BlueprintBazaar) : MinigameCommandsSkeleton() {
        /**
     * All sub-commands for this minigame
     */
    private enum class SubCommands {
        START,
        START_HARD_MODE,
        PAUSE,
        RESUME,
        END,
        NUKE_ARENA,
        SPAWN_BUILD,
        SHOWCASE_ALL_BUILDS,
        INIT_SCHEMATICS,
        CYCLE_THROUGH_SCHEMATICS
        ;

        companion object {
            /**
             * Converts a string to a SubCommand enum value. Case-insensitive.
             * @param str The string to convert
             * @return The SubCommand enum value, or null if the string does not match any enum value
             */
            fun fromString(str: String): SubCommands? {
                return entries.find { it.name.equals(str, ignoreCase = true) }
            }
        }
    }



    override fun handleCommand(sender: Player, command: Command, label: String, args: Array<String>): Boolean {
        when (SubCommands.fromString(args[0])) {
            SubCommands.START -> {
                if (blueprintbazaar.isAlreadyRunning()) return false
                blueprintbazaar.start(sender)
            }
            SubCommands.START_HARD_MODE -> {
                if (blueprintbazaar.isAlreadyRunning()) return false
                blueprintbazaar.startFastMode(sender)
            }
            SubCommands.PAUSE -> {
                if (blueprintbazaar.isAlreadyPaused()) return false
                blueprintbazaar.pauseGame()
            }
            SubCommands.RESUME -> {
                if (blueprintbazaar.isNotPaused()) return false
                blueprintbazaar.resumeGame()
            }
            SubCommands.END -> {
                if (blueprintbazaar.isGameNotRunning()) return false
                blueprintbazaar.endGame()
            }
            SubCommands.INIT_SCHEMATICS -> blueprintbazaar.initSchematics()
            SubCommands.SPAWN_BUILD -> blueprintbazaar.skipToNextBuild()
            SubCommands.SHOWCASE_ALL_BUILDS -> blueprintbazaar.loadAllSchematics()
            SubCommands.CYCLE_THROUGH_SCHEMATICS -> blueprintbazaar.cycleThroughSchematics()
            SubCommands.NUKE_ARENA -> blueprintbazaar.nukeArea(BPBConst.Locations.GAME_START_LOCATION, BPBConst.Locations.GAME_AREA_RADIUS)

            else -> return error(sender, "Unknown command.")
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): List<String> {
        return when (args.size) {
            1 -> SubCommands.entries.map { it.name.lowercase()}
            else -> {listOf()}
        }
    }
}
