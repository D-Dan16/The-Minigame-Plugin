package base.annotations

/**
 * A marker for a property of a class that inherits [base.minigames.MinigameSkeleton].
 * This marker dictates that when calling the class' [base.minigames.MinigameSkeleton.endGame] method, the property needs to be reset to a neutral value/ state.
 */
annotation class ShouldBeReset
