@file:Suppress("DEPRECATION")

package base.minigames.hole_in_the_wall.ui

import base.minigames.hole_in_the_wall.HoleInTheWall
import base.resources.Colors
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.ChatColor

object UIState {
    private val wallTypeKeys = listOf("firstWallType", "secondWallType", "thirdWallType", "forthWallType")
    internal var wallTypesScoreboardText = wallTypeKeys.iterator()

    internal fun initScoreboard(hitw: HoleInTheWall) {
        hitw.registerScoreboardLine(key = "", entryText = " ")
        hitw.registerScoreboardLine(key = "wallTypesTitle", entryText = "${ChatColor.GOLD}${ChatColor.BOLD}Wall Types:")

        wallTypeKeys.forEachIndexed { index, key ->
            hitw.registerScoreboardLine(key, entryText = "${ChatColor.COLOR_CHAR}${index + 1}")
        }
    }

    fun formatWallType(wallTypeName: String): String =
        "${ChatColor.YELLOW}• ${ChatColor.WHITE}$wallTypeName"

    fun formatWallTypeChatMessage(id: String, description: String): Component =
        Component.text()
            .append(Component.text("★ ", TextColor.fromHexString(Colors.TitleColors.ORANGE), TextDecoration.BOLD))
            .append(Component.text(id, NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text("\n  ➜ ", NamedTextColor.GOLD))
            .append(Component.text(description, NamedTextColor.WHITE))
            .build()

    fun reset(hitw: HoleInTheWall) {
        wallTypeKeys.forEach { key ->
            hitw.updateScoreboardLineSuffix(key, "")
        }
        wallTypesScoreboardText = wallTypeKeys.iterator()
    }
}
