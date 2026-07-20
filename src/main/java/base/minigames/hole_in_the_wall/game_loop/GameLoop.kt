package base.minigames.hole_in_the_wall.game_loop

import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.HITWConst.Timers
import base.minigames.hole_in_the_wall.HoleInTheWall
import base.minigames.hole_in_the_wall.debug.HITWDevLogger
import base.minigames.hole_in_the_wall.currentPlatformRegion
import base.minigames.hole_in_the_wall.platformSchematics
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.gameLoopRunnable
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.tickCount
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.timeElapsed
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.timeLeft
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeed
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallDifficultyProgression
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallSpeedProgression
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.multipleWallWaveProgression
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.platformProgression
import base.minigames.hole_in_the_wall.game_loop.GameLoopRuntimeState.wallTypePoolProgression
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.updateWallLifecycleIfNeeded
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.WallsRuntimeState.existingWalls
import base.minigames.hole_in_the_wall.game_loop.walls.runtime.buildWallAxisOccupancyGrid
import base.minigames.hole_in_the_wall.game_loop.walls.spawning.manageWallSpawning
import base.minigames.hole_in_the_wall.wall_types.WallTypeDefinition
import base.resources.Colors
import base.utils.other.BuildLoader
import base.utils.additions.delayTheFollowing
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger.logger
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable

private const val PLATFORM_DECAY_WARNING_LEAD_TIME_SECONDS = 5.0

/**
 * Starts the repeating task that drives time progression, wall spawning, wall movement, and
 * other time-based game events.
 */
internal fun HoleInTheWall.startRepeatingGameLoop() {
    if (!this.isGameRunning || isGamePaused) {
        logger().warn("HITW: Game is not running, cannot start periodic task")
        return
    }

    gameLoopRunnable = object : BukkitRunnable() {
        override fun run() {
            try {
                tickCount++
                timeLeft -= 1.0 / 20.0
                timeElapsed += 1.0 / 20.0

                if (timeLeft <= 0) {
                    endGame()
                    return
                }

                updateGameState()
                // Resolve wall-to-wall conflicts after movement but before spawning the next wall.
                //forceTwoCloseWallsToStop()
                //stopNecessaryWallsAtStopSign()

                //--Add new walls to the game
                // We cap the number of possible walls that are in the arena incase that the generator goes for some reason nuts
                if (existingWalls.size < HITWConst.WallSpawning.HARD_CAP_MAX_POSSIBLE_AMOUNT_OF_WALLS)
                    manageWallSpawning()

                WallsRuntimeState.locationsOfWalls = buildWallAxisOccupancyGrid()
            } catch (throwable: Throwable) {
                HITWDevLogger.error("Unhandled exception in HITW game loop", throwable)
                throw throwable
            }
        }

        private fun updateGameState() {
            announceInitialWallTypeIfNeeded()
            updateWallSpeedIfNeeded()
            updateWallDifficultyIfNeeded()
            updateAvailableWallTypesIfNeeded()
            updatePlatformStatusIfNeeded()

            updateWallLifecycleIfNeeded()

            updateAmountOfWallsInAMultiWallWaveThatCanAppear()
        }

        /** Announces the non-Psych wall type selected for the initial pool. */
        private fun announceInitialWallTypeIfNeeded() {
            if (GameLoopRuntimeState.hasAnnouncedInitialWallType) return
            if (GameLoopRuntimeState.availableWallTypes.size != HITWConst.WallSpawning.INITIAL_WALL_TYPE_POOL_SIZE) return

            val initialNonPsychWallType = GameLoopRuntimeState.availableWallTypes
                .firstOrNull { it !== WallTypeDefinition.PSYCH }
                ?: return

            announceMessage(
                content = "Initial Wall Type: ${initialNonPsychWallType.displayName}",
                color = Colors.TitleColors.ORANGE,
                duration = 1500L,
            )
            players.forEach { player ->
                player.sendMessage(orangeMessage(initialNonPsychWallType.description))
            }
            GameLoopRuntimeState.hasAnnouncedInitialWallType = true
        }

        /** Shrinks the platform when the next shrinkage landmark is reached. */
        private fun updatePlatformStatusIfNeeded() {
            if (!platformProgression.advanceIfDue(timeElapsed + PLATFORM_DECAY_WARNING_LEAD_TIME_SECONDS)) return

            announceMessage("PLATFORM DECAYING!", "STAY AWAY FROM RED", Colors.TitleColors.RED, 1500L)
            players.forEach { it.playSound(it.location, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f) }

            val nextPlatformStage = platformProgression.current
            5 * 20L delayTheFollowing {
                shrinkPlatform(nextPlatformStage)
            }
        }

        /** Increases the current wall difficulty once the matching landmark is reached. */
        private fun updateWallDifficultyIfNeeded() {
            if (wallDifficultyProgression.advanceIfDue(timeElapsed)) {
                announceMessage(
                    content = "Wall difficulty increased",
                    color = Colors.TitleColors.AQUA,
                    duration = 1500L
                )
            }
        }

        /** Advances to the next configured wall speed when its landmark is reached. */
        private fun updateWallSpeedIfNeeded() {
            if (wallSpeedProgression.advanceIfDue(timeElapsed)) {
                wallSpeed = wallSpeedProgression.current
            }
        }

        /** Adds the next configured wall type to the designer's pool at its progression mark. */
        private fun updateAvailableWallTypesIfNeeded() {
            if (!wallTypePoolProgression.advanceIfDue(timeElapsed)) return

            GameLoopRuntimeState.refreshAvailableWallTypes()
            val newWallType = GameLoopRuntimeState.availableWallTypes.last()
            announceMessage(
                "New Wall Type: ${newWallType.displayName}",
                color = Colors.TitleColors.ORANGE,
                duration = 1500L
            )
            players.forEach { player ->
                player.sendMessage(orangeMessage(newWallType.description))
            }
        }

        private fun updateAmountOfWallsInAMultiWallWaveThatCanAppear() {
            multipleWallWaveProgression.advanceIfDue(timeElapsed)
        }
    }

    gameLoopRunnable?.runTaskTimer(plugin, Timers.DELAY_BEFORE_STARTING_GAME, 1L)
}

private fun orangeMessage(description: String): Component = Component.text(
    "--> $description",
    TextColor.fromHexString(Colors.TitleColors.ORANGE),
)

/** Removes the current platform schematic and loads the requested stage. */
internal fun shrinkPlatform(platformStage: Int) {
    currentPlatformRegion?.let { BuildLoader.deleteSchematic(it) }

    currentPlatformRegion = BuildLoader.loadSchematicByFile(
        platformSchematics[platformStage],
        HITWConst.Locations.PLATFORM
    )
}