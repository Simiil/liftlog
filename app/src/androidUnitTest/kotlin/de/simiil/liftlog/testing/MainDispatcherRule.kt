package de.simiil.liftlog.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Swaps Dispatchers.Main for a test dispatcher (viewModelScope needs it).
 *
 *  [dispatcher] is exposed so tests can pass it to `runTest(mainDispatcherRule.dispatcher)`: that
 *  makes the test coroutine and `viewModelScope` share ONE scheduler. Required since PR5, when name
 *  resolution inside `combine{}.stateIn()` transforms became `suspend` — with two schedulers the
 *  transform's continuation lands on the other scheduler and turbine never sees the emission. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
