package base.minigames.parkour_dash

import base.commands.MinigameCommandsSkeleton
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private const val string = "apple"

class ParkourDashCommands(val parkourDash: ParkourDash) : MinigameCommandsSkeleton() {
    private enum class SubCommands {
        START,
        PAUSE,
        RESUME,
        END,
        RESET,
        GENERATE,
        NUKE_ARENA,
        NEXT_LEVEL,
        SET_CHECKPOINT,
        RESPAWN;

        companion object {
            fun fromString(str: String): SubCommands? = entries.find { it.name.equals(str,true) }
        }
    }

    enum class Modes {
        NORMAL,
        HARD,
        EXTREME;

        companion object {
            fun fromString(str: String): Modes? = entries.find { it.name.equals(str,true) }
        }
    }

    private enum class Targets {
        SENDER,
        ALL;

        companion object {
            fun fromString(str: String): Targets? = entries.find { it.name.equals(str,true) }
        }
    }

    override fun handleCommand(
        sender: Player,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        val argZeroCommand: SubCommands = SubCommands.fromString(args[0]) ?:
            return error(sender, "Unknown command.")

        when (argZeroCommand) {
            SubCommands.START -> {
                if (parkourDash.isAlreadyRunning()) return false
                if (args.size < 2) return error(sender, "Please provide a mode: ${Modes.entries.joinToString { it.name.lowercase() }}")
                val mode = Modes.fromString(args[1])
                    ?: return error(sender, "Unknown mode. Use one of: ${Modes.entries.joinToString { it.name.lowercase() }}")

                parkourDash.setDifficultyMode(mode)
                parkourDash.start(sender)
            }
            SubCommands.PAUSE -> {
                if (parkourDash.isAlreadyPaused()) return false
                parkourDash.pauseGame()
            }
            SubCommands.RESUME -> {
                if (parkourDash.isNotPaused()) return false
                parkourDash.resumeGame()
            }
            SubCommands.END -> {
                if (parkourDash.isGameNotRunning()) return false
                parkourDash.endGame()
            }
            SubCommands.NUKE_ARENA -> parkourDash.nukeArea()
            SubCommands.NEXT_LEVEL -> {
                if (parkourDash.isGameNotRunning()) return false
                if (args.size < 2) return error(sender, "Please specify target: ${Targets.entries.joinToString { it.name.lowercase() }}")

                when (Targets.fromString(args[1])) {
                    Targets.SENDER -> parkourDash.tpPlayerToNextSection(sender)
                    Targets.ALL -> parkourDash.tpPlayersToNextSection()
                    null -> return error(sender, "Unknown target. Use one of: ${Targets.entries.joinToString { it.name.lowercase() }}")
                }
            }

            SubCommands.SET_CHECKPOINT -> {
                if (parkourDash.isGameNotRunning()) return false
                parkourDash.setCheckpointFor(sender)
            }

            SubCommands.RESPAWN -> {
                if (parkourDash.isGameNotRunning()) return false
                parkourDash.sendToLastCheckpoint(sender)
            }

            SubCommands.RESET -> {
                if (parkourDash.isGameNotRunning()) return false
                parkourDash.reset()
            }

            SubCommands.GENERATE -> {
                if (parkourDash.isAlreadyRunning()) return false
                parkourDash.prepareArea()
            }
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
            1 -> {
                val prefix = args[0].lowercase()
                SubCommands.entries.map { it.name.lowercase() }
                    .filter { it.startsWith(prefix) }
            }
            2 -> {
                val sub = SubCommands.fromString(args[0]) ?: return emptyList()
                val prefix = args[1].lowercase()
                when (sub) {
                    SubCommands.START -> Modes.entries.map { it.name.lowercase() }.filter { it.startsWith(prefix) }
                    SubCommands.NEXT_LEVEL -> Targets.entries.map { it.name.lowercase() }.filter { it.startsWith(prefix) }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
