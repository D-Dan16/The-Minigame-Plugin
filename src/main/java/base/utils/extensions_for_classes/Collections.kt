package base.utils.extensions_for_classes

import kotlin.random.Random

fun <T> Collection<Pair<T, Int>>.getWeightedRandom(): T {
    val totalWeight = this.sumOf { it.second }
    var randomValue = Random.nextInt(totalWeight)
    for ((item, weight) in this) {
        randomValue -= weight
        if (randomValue < 0) {
            return item
        }
    }
    throw IllegalStateException("Should never reach here if weights are positive")
}

fun <T> Collection<T>.returnQuantityOfEach(): Map<T, Int> =
    this.groupingBy { it }.eachCount()