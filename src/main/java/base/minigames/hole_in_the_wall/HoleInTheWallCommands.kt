package base.minigames.hole_in_the_wall

import base.commands.MinigameCommandsSkeleton
import base.minigames.hole_in_the_wall.HITWConst.availableMaps
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class HoleInTheWallCommands(private val holeInTheWall: HoleInTheWall) : MinigameCommandsSkeleton() {
    private enum class SubCommands {
        START,
        START_HARD_MODE,
        PAUSE,
        RESUME,
        END,
        NUKE_ARENA,
        SET,
        SPAWN_WALL,
        CLEAR_WALLS
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
                if (holeInTheWall.guardAlreadyRunning()) return false

                when (args.size) {
                    1 -> return  error(sender, "Please specify a map name to start the game.")
                    2 -> {
                        if (SubCommands.fromString(args[0]) == SubCommands.START_HARD_MODE) {
                            holeInTheWall.startFastMode(sender, args[1])
                        } else {
                            holeInTheWall.start(sender, args[1])
                        }
                    }
                    3 -> {
                        if (SubCommands.fromString(args[0]) == SubCommands.START_HARD_MODE) {
                            holeInTheWall.startFastMode(sender, args[1], args[2])
                        } else {
                            holeInTheWall.start(sender, args[1], args[2])
                        }
                    }
                    else -> return error(sender, "Too many arguments")
                }
            }
            SubCommands.START_HARD_MODE -> {
                if (holeInTheWall.guardAlreadyRunning()) return false
                holeInTheWall.startFastMode(sender)
            }
            SubCommands.PAUSE -> {
                if (holeInTheWall.guardAlreadyPaused()) return false
                holeInTheWall.pauseGame()
            }
            SubCommands.RESUME -> {
                if (holeInTheWall.guardNotPaused()) return false
                holeInTheWall.resumeGame()
            }
            SubCommands.END -> {
                if (holeInTheWall.isGameNotRunning()) return false
                holeInTheWall.endGame()
            }
            SubCommands.NUKE_ARENA -> holeInTheWall.nukeArea()
            SubCommands.SET -> {
                if (args.size == 1) return error(sender, "Please specify a setting to change.")


                when (args[1].lowercase(Locale.getDefault())) {
                    "wall_spawning_mode" -> {
                        if (args.size < 3) return error(sender, "Please specify the wall spawning mode.")

                        holeInTheWall.changeWallSpawningMode(args[2])
                    }
                    "wall_speed" -> {
                        if (args.size < 3) return error(sender, "Please specify the wall speed.")

                        try {
                            val speed = args[2].toInt()
                            holeInTheWall.wallSpeed = speed
                        } catch (_: NumberFormatException) {
                            return error(sender, "Invalid wall speed value")
                        }
                    }
                    else -> return error(sender, "Unknown setting: ${args[1]}.")
                }
            }
            SubCommands.SPAWN_WALL -> holeInTheWall.createNewWall()
            SubCommands.CLEAR_WALLS -> holeInTheWall.clearWalls()

            else -> return error(sender, "Unknown command.")
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> SubCommands.entries.map { it.name.lowercase()}
            2 -> {
                when (args[0]) {
                    "start" -> availableMaps
                    "set" -> listOf(
                        "wall_spawning_mode", "wall_speed"
                    )
                    else -> listOf()
                }
            }
            3 -> {
                when (args[0]) {
                    "start" -> HITWConst.WallSpawnerMode.getModesAsAStringList()
                    "set" -> when (args[1]) {
                        "wall_spawning_mode" -> HITWConst.WallSpawnerMode.getModesAsAStringList()
                        "wall_speed" -> HITWConst.Timers.WALL_SPEED.map { it.toString() }
                        else -> listOf()
                    }
                    else -> listOf()
                }
            }
            else -> listOf()
        }
    }
}
