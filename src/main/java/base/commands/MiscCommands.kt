package base.commands

import base.MinigamePlugin
import base.utils.additions.Utils.nukeGameArea
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import java.util.*

class MiscCommands(private val plugin: MinigamePlugin?) : CommandExecutor, TabExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can execute this command.")
            return false
        } else if (args.isEmpty()) {
            return false
        }

        //--------Going Through all the valid misc commands----------------
        when (args[0].lowercase(Locale.getDefault())) {
            "nuke" -> {
                if (args.size < 2) {
                    sender.sendMessage("Please provide a radius for the nuke.")
                    return false
                } else if (!args[1].matches("\\d+".toRegex()) || args[1].toInt() <= 0) {
                    sender.sendMessage("Invalid radius.")
                    return false
                }
                sender.sendMessage("Nuking the game area...")
                val radius = args[1].toInt()
                nukeGameArea(sender.location, radius)
            }

            else -> {
                sender.sendMessage("Invalid command.")
                return false
            }
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String?> {
        if (args.size == 1) {
            return mutableListOf<String?>("nuke")
        }
        return mutableListOf()
    }
}
