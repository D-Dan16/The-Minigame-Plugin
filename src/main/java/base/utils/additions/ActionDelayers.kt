package base.utils.additions

import base.MinigamePlugin
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import kotlin.math.max


infix fun Long.delayTheFollowing(runnable: ()-> Unit) {
    Bukkit.getScheduler().runTaskLater(MinigamePlugin.plugin,runnable,this)
}

/**
 * A [BukkitRunnable] that can be paused and resumed. When paused, it keeps track of the remaining time until the next execution. When resumed, it continues from where it left off.
 *
 * If added to the [base.minigames.MinigameSkeleton.pausableRunnables] list, the runnable will be automatically stopped on [base.minigames.MinigameSkeleton.pauseGame], and started upon [base.minigames.MinigameSkeleton.resumeGame] and [base.minigames.MinigameSkeleton.start].
 *
 * @param plugin The JavaPlugin instance.
 * @param periodTicks The period in ticks between each execution of the action. If null, the action will be executed only once after the initial delay.
 * @param remainingTicks The remaining ticks until the next execution when paused. Default is 0.
 * @param action The action to execute.
 */
class PausableBukkitRunnable(
    private val plugin: Plugin,
    private var remainingTicks: Long = 0L,
    private val periodTicks: Long? = null,
    private val action: () -> Unit
) {
    private var task: BukkitTask? = null
    private var lastStartTime: Long = 0L // in system ms

    private var isPaused: Boolean = true

    /**
     *  Used to dictate if this runnable shouldn't be used and needs to be removed from lists.
     *  This is flagged as true for run once runnables that have been executed.
     *
     *  Can also be manually flagged; however, it'll be removed only when calling [base.minigames.MinigameSkeleton.pauseGame].
     * */
    var shouldNotBeUsed: Boolean = false

    /**
     * Starts/resumes the task. If the task is already running, this method does nothing.
     */
    fun start() {
        if (!isPaused) return
        isPaused = false

        lastStartTime = System.currentTimeMillis()

        task = if (periodTicks != null) {

            object : BukkitRunnable() {
                override fun run() {
                    action()
                }
            }.runTaskTimer(plugin, remainingTicks, periodTicks)
        } else {
            object : BukkitRunnable() {
                override fun run() {
                    action()

                    // disable reusing this instance of Pausable BukkitRunnable
                    shouldNotBeUsed = true
                    cancel()
                    return
                }
            }.runTaskLater(plugin, remainingTicks)
        }


        if (periodTicks != null) {
            remainingTicks = periodTicks
        }
    }

    /**
     * Pauses the task. If the task is already paused, this method does nothing.
     */
    fun pause() {
        if (isPaused) return
        isPaused = true

        task?.cancel()
        task = null


        val elapsedTicks = ((System.currentTimeMillis() - lastStartTime) * 20 / 1000)

        // If the task is a repeating timer, we'll need to have a module operation since the task repeats itself every 'periodTicks' ticks.
        // Otherwise, if the task is only a delayed run once task, we'll just calculate the difference in the 2 times.
        val elapsedTicksInCycle =
            if (periodTicks != null)
                elapsedTicks % periodTicks
            else
                elapsedTicks

        remainingTicks = max(0, remainingTicks - elapsedTicksInCycle)
    }

    /**
     * Resets the runnable to its initial state.
     * This cancels any running task and resets all internal state variables to their initial values.
     */
    fun reset() {
        task?.cancel()
        task = null
        isPaused = true
        shouldNotBeUsed = false
        lastStartTime = 0L
    }
}

fun Collection<PausableBukkitRunnable>.activateChain(
    listOfRunnablesToStoreAt: MutableCollection<PausableBukkitRunnable>? = null,
    stopCondition: (() -> Boolean)? = null
) {
    listOfRunnablesToStoreAt?.addAll(this)

    val iterator = this.iterator()
    fun runNext() {
        if (stopCondition?.invoke() == true || !iterator.hasNext()) return

        val runnable: PausableBukkitRunnable = iterator.next()

        runnable.start()

        // Schedule next runnable after this one finishes
        activateTaskAfterConditionIsMet(
            condition = {
                runnable.shouldNotBeUsed == true
            } ,
            conditionToCancel = stopCondition,
            actionToDoIfCanceled = { runnable.pause() },
            action = {
                runNext()
            }
        )
    }

    runNext()
}

/**
 * Activates a task after a condition is met, with an optional delay after the condition is met.
 * The task will be checked at regular intervals defined by `checkInterval`.
 * If `conditionToCancel` is provided and returns true, the task will be canceled.
 *
 * @param checkInterval The interval in ticks to check the condition.
 * @param delayAfterConditionMet The delay in ticks after the condition is met before executing the action.
 * @param condition The condition to check.
 * @param conditionToCancel An optional condition to cancel the task.
 * @param action The action to execute when the condition is met.
 * @param actionToDoIfCanceled An optional action to execute if the task is cancelled.
 * @param actionToDoWhileWaitingForAConditionToOccur An optional action to execute while waiting for the condition to occur.
 * @param listOfRunnablesToAddTo An optional list to add the runnable to, for later cancellation.
 * @return A BukkitRunnable that can be canceled if needed.
 */
fun activateTaskAfterConditionIsMet(
    checkInterval: Long = 1L,
    delayAfterConditionMet: Long = 0L,
    condition: () -> Boolean,
    conditionToCancel: (() -> Boolean)? = null,
    action: Runnable,
    actionToDoIfCanceled: (() -> Unit)? = null,
    actionToDoWhileWaitingForAConditionToOccur: Runnable? = null,
    listOfRunnablesToAddTo: MutableList<BukkitRunnable>? = null
): BukkitRunnable {
    val runnable: BukkitRunnable = object : BukkitRunnable() {
        override fun run() {
            when {
                conditionToCancel?.invoke() == true -> cancel()
                condition.invoke() -> {
                    if (delayAfterConditionMet > 0L) {
                        Bukkit.getScheduler().runTaskLater(MinigamePlugin.Companion.plugin, action, delayAfterConditionMet)
                    } else {
                        action.run()
                    }
                    cancel(false)
                }
                else -> actionToDoWhileWaitingForAConditionToOccur?.run()
            }
            return
        }

        override fun cancel() {
            cancel(true)
        }

        fun cancel(doActionWhenCanceled: Boolean){
            if (doActionWhenCanceled) actionToDoIfCanceled?.invoke()
            super.cancel()

            listOfRunnablesToAddTo?.remove(this)
        }
    }

    runnable.runTaskTimer(MinigamePlugin.Companion.plugin, 0L, checkInterval)

    listOfRunnablesToAddTo?.add(runnable)

    return runnable
}