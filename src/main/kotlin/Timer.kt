import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 *
 */
@ExperimentalCoroutinesApi
class Timer(val delay: Long, private val repeat: Int = REPEAT_INFINITE) {

    val isActive: Boolean
        get() = job.isActive

    private lateinit var channel: TimerChannel

    private lateinit var job: Job

    private var repeatCount = 0

    fun start(tickCallback: TickCallback) {

        stop()

        job = Job()

        GlobalScope.launch(job) {
            while (job.isActive) {

                delay(delay)

                tickCallback.invoke()

                checkRepeatConstraints()
            }
        }
    }

    fun start(coroutineScope: CoroutineScope = GlobalScope): Channel<Unit> {

        stop()

        job = Job()
        channel = TimerChannel(Channel(Channel.RENDEZVOUS))

        CoroutineScheduler(delay, channel, job, coroutineScope)
            .tickCallback(::checkRepeatConstraints)
            .start()

        return channel
    }

    fun stop() {

        if (::job.isInitialized) {
            job.cancel()
        }

        if (::channel.isInitialized) {
            channel.safeCancel()
        }

        repeatCount = 0
    }

    private fun checkRepeatConstraints() {
        if (repeat == REPEAT_INFINITE)
            return

        repeatCount++

        if (repeatCount == repeat) {
            stop()
        }
    }

    internal class CoroutineScheduler(
        private val interval: Long,
        private val channel: Channel<Unit>,
        private val job: Job,
        private val coroutineScope: CoroutineScope
    ) {
        private var tickCallback: TickCallback? = null

        fun tickCallback(tickCallback: TickCallback): CoroutineScheduler {

            this.tickCallback = tickCallback

            return this
        }

        fun start() {
            coroutineScope.launch(job) {
                try {
                    while (job.isActive) {

                        delay(interval)

                        channel.send(Unit)

                        tickCallback?.invoke()
                    }
                } catch (ex: CancellationException) {
                    // We probably caused this one
                }
            }
        }
    }

    internal class TimerChannel(private val channel: Channel<Unit>) : Channel<Unit> by channel {

        override fun cancel() {
            if (!channel.isClosedForReceive) {
                throw RuntimeException("Cancellation of Timer channel is not allowed. Use timer#stop()")
            }
        }

        internal fun safeCancel() {
            channel.cancel()

            cancel()
        }
    }

    companion object {
        const val REPEAT_INFINITE = -1337
    }
}

internal typealias TickCallback = () -> Unit
