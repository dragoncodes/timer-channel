import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TimerChannelTests {

    @Test
    fun `timer with callback cancel working correctly`() = runBlocking {

        val atomicCounter = AtomicInteger()
        val timer = Timer(300)

        fun callback() {
            atomicCounter.addAndGet(1)

            if (atomicCounter.get() == 3) {
                timer.stop()
            }
        }

        timer.start(::callback)

        delay(1200)

        assertEquals(3, atomicCounter.get())
    }

    @Test
    fun `calling start multiple times - callback`() = runBlocking {
        val timer = Timer(300)

        GlobalScope.launch {
            delay(400)

            timer.start { }
        }

        var hasPassed = false
        timer.start {

            if (hasPassed) {
                assert(false)
            }

            hasPassed = true
        }

        delay(3000)

        assertTrue(hasPassed)
    }

    @Test
    fun `timer with channel cancel working correctly`() = runBlocking {

        var counter = 0

        val timer = Timer(300)
        val channel = timer.start(this)

        for (tick in channel) {

            counter++

            if (counter == 3) {
                timer.stop()
            }

            if (!timer.isActive) {
                break
            }
        }

        assertEquals(3, counter)
    }

    @Test
    fun `stop timer - channel has no pending items to be received`() = runBlocking {
        val timer = Timer(300)

        val channel = timer.start(this)

        delay(600)

        timer.stop()

        var counter = 0
        for (tick in channel) {
            counter++
        }

        assertEquals(0, counter)
    }

    @Test
    fun `timer restart with channels`() = runBlocking {

        val timer = Timer(300)

        var channel = timer.start(this)

        launch {
            delay(601)

            timer.stop()
        }

        // Block here until the above coroutine stops the timer
        for (tick in channel) {
        }

        channel = timer.start(this)

        launch {
            delay(601)

            timer.stop()
        }

        for (tick in channel) {
            println("tick tock")
        }

        assertTrue(!timer.isActive)
    }

    @Test
    fun `calling start multiple times - coroutines`() = runBlocking {
        val timer = Timer(300)

        val channel = timer.start(this)

        launch {

            delay(800)

            timer.start(this@runBlocking)
        }

        for (tick in channel) {
        }

        assertTrue(channel.isClosedForReceive)
    }

    @Test
    fun `repeat normal`() = runBlocking {
        val timer = Timer(300, 3)

        val channel = timer.start(this)
        repeat(3) {
            channel.receive()
        }

        assertTrue(channel.isClosedForReceive)

        val channel1 = timer.start(this)
        repeat(3) {
            channel1.receive()
        }

        assertTrue(channel1.isClosedForReceive)
    }
}
