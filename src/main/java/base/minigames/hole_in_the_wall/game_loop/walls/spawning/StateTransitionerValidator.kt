package base.minigames.hole_in_the_wall.game_loop.walls.spawning

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HoleInTheWall
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit

internal fun HoleInTheWall.attemptChangingStateTo(wantedState: HITWConst.WallSpawnerState) {
    val canTransition = when (SpawnerRuntimeState.stateOfWallSpawner) {
        HITWConst.WallSpawnerState.IDLE -> wantedState in setOf(
            HITWConst.WallSpawnerState.INTENDING_TO_CREATE_1_WALL,
            HITWConst.WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE,
        )

        HITWConst.WallSpawnerState.INTENDING_TO_CREATE_1_WALL -> wantedState in setOf(
            HITWConst.WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN,
            HITWConst.WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS
        )

        HITWConst.WallSpawnerState.INTENDING_TO_CREATE_MULTIPLE_WALLS_AT_ONCE -> wantedState in setOf(
            HITWConst.WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN,
            HITWConst.WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS
        )

        HITWConst.WallSpawnerState.WAITING_A_LIL_TILL_WALL_HAS_SPACE_TO_SPAWN -> wantedState in setOf(
            HITWConst.WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS, //used only when we change the mode of the wall spawner, when we cancel runnables that are sending you to a desired state..
            HITWConst.WallSpawnerState.SPAWNING_1_WALL,
            HITWConst.WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE
        )

        HITWConst.WallSpawnerState.SPAWNING_MULTIPLE_WALLS_AT_ONCE -> wantedState in setOf(
            HITWConst.WallSpawnerState.IDLE
        )

        HITWConst.WallSpawnerState.SPAWNING_1_WALL -> wantedState in setOf(
            HITWConst.WallSpawnerState.IDLE
        )

        HITWConst.WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS -> wantedState in setOf(
            HITWConst.WallSpawnerState.SWAPPING_TO_IDLE_WHEN_THERE_ARE_NO_EXISTING_WALLS,  //THIS IS CRITICAL TO HAVE, SINCE, WHEN CHANGING MODES, we might try to change states to this state multiple times, from the condition that is called when the runable is canceled which are canceled when the mode is changed
            HITWConst.WallSpawnerState.IDLE
        )

        HITWConst.WallSpawnerState.DO_NO_ACTION -> wantedState in setOf(
            HITWConst.WallSpawnerState.IDLE
        )
    }

    if (!canTransition) {
        Bukkit.getServer().broadcast(
            Component.text("HITW: Cannot transition from ${SpawnerRuntimeState.stateOfWallSpawner} to $wantedState").color(
                NamedTextColor.RED))
        pauseGame()
    }

    SpawnerRuntimeState.stateOfWallSpawner = wantedState

    if (HITWConst.Development.IS_IN_DEVELOPMENT)
        Bukkit.getServer().broadcast(Component.text("state = ${SpawnerRuntimeState.stateOfWallSpawner}").color(NamedTextColor.GRAY))
}
