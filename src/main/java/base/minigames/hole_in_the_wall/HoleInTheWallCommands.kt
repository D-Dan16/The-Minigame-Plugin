package base.minigames.hole_in_the_wall

import base.commands.MinigameCommandsSkeleton
import base.minigames.hole_in_the_wall.HITWConst.ArenaFiles.availableMaps
import base.minigames.hole_in_the_wall.arena.PlatformStagePreviewer
import base.minigames.hole_in_the_wall.arena.WallPackPreviewer
import base.minigames.hole_in_the_wall.game_loop.walls.wall_creating.clearWalls
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import java.util.*

class HoleInTheWallCommands(private val holeInTheWall: HoleInTheWall) : MinigameCommandsSkeleton() {
    companion object {
        const val COMMAND_BOOK_TITLE = "HITW Commands"
        const val HITW_COMMAND_PREFIX = "/mg_hole_in_the_wall"
        private val COMMAND_BOOK_KEY = NamespacedKey("minigameplugin", "hitw_command_book")

        internal fun isCommandBook(item: ItemStack?): Boolean =
            item?.type == Material.WRITTEN_BOOK &&
                item.itemMeta.persistentDataContainer.has(COMMAND_BOOK_KEY, PersistentDataType.BYTE)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender is Player && command.name.equals("hitw", ignoreCase = true) && args.isEmpty()) {
            giveCommandBook(sender)
            return true
        }

        return super.onCommand(sender, command, label, args)
    }

    private enum class SubCommands {
        START,
        START_HARD_MODE,
        PAUSE,
        RESUME,
        END,
        NUKE_ARENA,
        SET,
        CLEAR_WALLS,
        PREVIEW_WALLPACK,
        CREATE_WALLPACK,
        SAVE_WALLPACK_PREVIEW,
        CLEAR_WALLPACK_PREVIEW,
        PREVIEW_PLATFORM_STAGES,
        CREATE_PLATFORM_STAGES,
        SAVE_PLATFORM_STAGE_PREVIEW,
        CLEAR_PLATFORM_STAGE_PREVIEW,
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
                if (holeInTheWall.isAlreadyRunning()) return false

                when (args.size) {
                    1 -> return  error(sender, "Please specify a map name to start the game.")
                    2 -> {
                        if (SubCommands.fromString(args[0]) == SubCommands.START_HARD_MODE) {
                            holeInTheWall.startFastMode(sender, args[1])
                        } else {
                            holeInTheWall.start(sender, args[1])
                        }
                    }
                    else -> return error(sender, "Too many arguments")
                }
            }
            SubCommands.START_HARD_MODE -> {
                if (holeInTheWall.isAlreadyRunning()) return false
                if (args.size < 2) return error(sender, "Please specify a map name to start the game.")
                holeInTheWall.startFastMode(sender, args[1])
            }
            SubCommands.PAUSE -> {
                if (holeInTheWall.isAlreadyPaused()) return false
                holeInTheWall.pauseGame()
            }
            SubCommands.RESUME -> {
                if (holeInTheWall.isNotPaused()) return false
                holeInTheWall.resumeGame()
            }
            SubCommands.END -> {
                if (holeInTheWall.isGameNotRunning()) return false
                holeInTheWall.endGame()
            }
            SubCommands.NUKE_ARENA -> holeInTheWall.nukeArena()
            SubCommands.SET -> {
                if (args.size == 1) return error(sender, "Please specify a setting to change.")


                when (args[1].lowercase(Locale.getDefault())) {
                    "wall_speed" -> {
                        if (args.size < 3) return error(sender, "Please specify the wall speed.")

                        try {
                            val speed = args[2].toInt()
                            holeInTheWall.setWallSpeed(speed)
                        } catch (_: NumberFormatException) {
                            return error(sender, "Invalid wall speed value")
                        }
                    }
                    else -> return error(sender, "Unknown setting: ${args[1]}.")
                }
            }
            SubCommands.CLEAR_WALLS -> clearWalls()
            SubCommands.PREVIEW_WALLPACK -> {
                if (args.size != 2) return error(sender, "Usage: /mg_hole_in_the_wall preview_wallpack <map>")
                WallPackPreviewer.show(sender, args[1])?.let { return error(sender, it) }
                sender.sendMessage(
                    "Static preview created: easy, medium, hard, and very_hard are separate rows. " +
                        "Run /mg_hole_in_the_wall clear_wallpack_preview to remove it."
                )
            }
            SubCommands.CREATE_WALLPACK -> {
                if (args.size != 1) return error(sender, "Usage: /mg_hole_in_the_wall create_wallpack")
                WallPackPreviewer.create(sender)?.let { return error(sender, it) }
                sender.sendMessage(
                    "Created and displayed 20 slime-block walls: 5 each for easy, medium, hard, and very_hard. " +
                        "Edit and save them, then move map_component_creations/wallpack into your map folder."
                )
            }
            SubCommands.SAVE_WALLPACK_PREVIEW -> {
                if (args.size != 1) return error(sender, "Usage: /mg_hole_in_the_wall save_wallpack_preview")
                WallPackPreviewer.save()?.let { return error(sender, it) }
                sender.sendMessage("Saved the edited preview walls to their wall-pack schematic files.")
            }
            SubCommands.CLEAR_WALLPACK_PREVIEW -> {
                val removed = WallPackPreviewer.clear()
                sender.sendMessage("Removed $removed wall-preview schematics.")
            }
            SubCommands.PREVIEW_PLATFORM_STAGES -> {
                if (args.size != 2) return error(sender, "Usage: /mg_hole_in_the_wall preview_platform_stages <map>")
                PlatformStagePreviewer.show(sender, args[1])?.let { return error(sender, it) }
                sender.sendMessage(
                    "Platform-stage preview created: stages 1, 2, and 3 are placed side by side. " +
                        "Run /mg_hole_in_the_wall clear_platform_stage_preview to remove it."
                )
            }
            SubCommands.CREATE_PLATFORM_STAGES -> {
                if (args.size != 1) return error(sender, "Usage: /mg_hole_in_the_wall create_platform_stages")
                PlatformStagePreviewer.create(sender)?.let { return error(sender, it) }
                sender.sendMessage(
                    "Created and displayed three platform stages. Edit and save them, then move " +
                        "map_component_creations/platforms into your map folder."
                )
            }
            SubCommands.SAVE_PLATFORM_STAGE_PREVIEW -> {
                if (args.size != 1) return error(sender, "Usage: /mg_hole_in_the_wall save_platform_stage_preview")
                PlatformStagePreviewer.save()?.let { return error(sender, it) }
                sender.sendMessage("Saved the three edited platform stages to their schematic files.")
            }
            SubCommands.CLEAR_PLATFORM_STAGE_PREVIEW -> {
                val removed = PlatformStagePreviewer.clear()
                sender.sendMessage("Removed $removed platform-stage preview schematics.")
            }

            else -> return error(sender, "Unknown command.")
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> SubCommands.entries.map { it.name.lowercase()}
            2 -> {
                when (args[0]) {
                    "start", "start_hard_mode", "preview_wallpack", "preview_platform_stages" -> availableMaps
                    "set" -> listOf(
                        "wall_speed"
                    )
                    else -> listOf()
                }
            }
            3 -> when (args[0]) {
                "set" -> when (args[1]) {
                    "wall_speed" -> HITWConst.Timers.WALL_SPEED.map { it.toString() }
                    else -> listOf()
                }
                else -> listOf()
            }
            else -> listOf()
        }
    }

    private fun giveCommandBook(player: Player) {
        val book = ItemStack(Material.WRITTEN_BOOK).apply {
            itemMeta = (itemMeta as BookMeta).apply {
                title = COMMAND_BOOK_TITLE
                author = "MinigamePlugin"
                displayName(Component.text("HITW Command Book", NamedTextColor.GREEN))
                pages(commandBookPages())
                persistentDataContainer.set(COMMAND_BOOK_KEY, PersistentDataType.BYTE, 1.toByte())
            }
        }

        player.inventory.addItem(book).values.forEach { overflow ->
            player.world.dropItemNaturally(player.location, overflow)
        }
        player.openBook(book)
    }

    private fun commandBookPages(): List<Component> {
        val maps = availableMaps.sorted()

        return listOf(
            commandPage(
                "Game controls",
                commandLink("Pause game", "pause"),
                commandLink("Resume game", "resume"),
                commandLink("End game", "end"),
                commandLink("Clear active walls", "clear_walls"),
                commandLink("Nuke arena", "nuke_arena")
            ),
            commandPage(
                "Start a game",
                *mapLinks(maps, "Start", "start")
            ),
            commandPage(
                "Start hard mode",
                *mapLinks(maps, "Start hard", "start_hard_mode")
            ),
            commandPage(
                "Wall-pack preview",
                *mapLinks(maps, "Preview", "preview_wallpack"),
                commandLink("Create blank wall pack", "create_wallpack"),
                commandLink("Save preview edits", "save_wallpack_preview"),
                commandLink("Clear preview", "clear_wallpack_preview")
            ),
            commandPage(
                "Platform stages",
                *mapLinks(maps, "Preview", "preview_platform_stages"),
                commandLink("Create dummy stages", "create_platform_stages"),
                commandLink("Save preview edits", "save_platform_stage_preview"),
                commandLink("Clear preview", "clear_platform_stage_preview")
            ),
            commandPage(
                "Wall speed",
                *HITWConst.Timers.WALL_SPEED.map { speed ->
                    commandLink("Set speed to $speed", "set wall_speed $speed")
                }.toTypedArray()
            )
        )
    }

    private fun commandPage(title: String, vararg links: Component): Component {
        val page = Component.text("$COMMAND_BOOK_TITLE\n", NamedTextColor.GREEN)
            .append(Component.text("$title\n\n", NamedTextColor.DARK_GREEN))

        return links.fold(page) { content, link -> content.append(link) }
    }

    private fun mapLinks(maps: List<String>, action: String, command: String): Array<Component> {
        if (maps.isEmpty()) {
            return arrayOf(Component.text("No maps are available.", NamedTextColor.RED))
        }

        return maps.map { map -> commandLink("$action: $map", "$command $map") }.toTypedArray()
    }

    private fun commandLink(label: String, command: String): Component =
        Component.text("• $label\n", NamedTextColor.BLACK)
            .clickEvent(ClickEvent.runCommand("$HITW_COMMAND_PREFIX $command"))
            .hoverEvent(Component.text("Run: $HITW_COMMAND_PREFIX $command", NamedTextColor.GRAY))
}
