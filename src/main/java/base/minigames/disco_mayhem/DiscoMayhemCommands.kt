// src/main/java/me/stavgordeev/plugin/commands/MinigameCommand.java
package base.minigames.disco_mayhem

import base.commands.MinigameCommandsSkeleton
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class DiscoMayhemCommands(private val discoMayhem: DiscoMayhem) : MinigameCommandsSkeleton() {
    /**
     * All sub-commands for this minigame
     */
    private enum class SubCommands {
        START,
        START_HARD_MODE,
        PAUSE,
        RESUME,
        END,
        NUKE_ARENA;

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
                if (discoMayhem.guardAlreadyRunning()) return false
                discoMayhem.start(sender)
            }
            SubCommands.START_HARD_MODE -> {
                if (discoMayhem.guardAlreadyRunning()) return false
                discoMayhem.startFastMode(sender)
            }
            SubCommands.PAUSE -> {
                if (discoMayhem.guardAlreadyPaused()) return false
                discoMayhem.pauseGame()
            }
            SubCommands.RESUME -> {
                if (discoMayhem.guardNotPaused()) return false
                discoMayhem.resumeGame()
            }
            SubCommands.END -> {
                if (discoMayhem.isGameNotRunning()) return false
                discoMayhem.endGame()
            }
            SubCommands.NUKE_ARENA -> discoMayhem.nukeArea(DiscoMayhemConst.GAME_START_LOCATION,DiscoMayhemConst.NUKE_AREA_RADIUS)
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