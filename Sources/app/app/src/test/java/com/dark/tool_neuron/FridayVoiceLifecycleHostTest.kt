package com.dark.tool_neuron

import android.media.AudioDeviceCallback
import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.dark.tool_neuron.voice.AttachedSession
import com.dark.tool_neuron.voice.SystemHooks
import com.dark.tool_neuron.voice.VoiceSessionLifecycleHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Host is forward-only: OS signals reach the attached session's passthroughs and never anything else.
class FridayVoiceLifecycleHostTest {

    private class RecordingHooks : SystemHooks {
        var focusListener: AudioManager.OnAudioFocusChangeListener? = null
        var routeCallback: AudioDeviceCallback? = null
        var lifecycleObserver: LifecycleEventObserver? = null
        var abandonCalls = 0
        var unregisterRouteCalls = 0
        var removeObserverCalls = 0

        override fun registerFocus(listener: AudioManager.OnAudioFocusChangeListener): Boolean {
            focusListener = listener
            return true
        }
        override fun abandonFocus() { abandonCalls++ }
        override fun registerRouteCallback(callback: AudioDeviceCallback) { routeCallback = callback }
        override fun unregisterRouteCallback(callback: AudioDeviceCallback) {
            unregisterRouteCalls++
            routeCallback = null
        }
        override fun addLifecycleObserver(observer: LifecycleEventObserver) { lifecycleObserver = observer }
        override fun removeLifecycleObserver(observer: LifecycleEventObserver) {
            removeObserverCalls++
            lifecycleObserver = null
        }
    }

    private class RecordingSession : AttachedSession {
        var focusLost = 0
        var routeChanged = 0
        var background = 0
        override fun onAudioFocusLost() { focusLost++ }
        override fun onAudioRouteChanged() { routeChanged++ }
        override fun onBackground() { background++ }
    }

    private class StubOwner : LifecycleOwner {
        override val lifecycle: Lifecycle get() = throw UnsupportedOperationException()
    }

    private fun fireOnStop(hooks: RecordingHooks) {
        hooks.lifecycleObserver?.onStateChanged(StubOwner(), Lifecycle.Event.ON_STOP)
    }

    @Test
    fun focusLoss_forwardsOnAudioFocusLostOnce() {
        val hooks = RecordingHooks()
        val session = RecordingSession()
        VoiceSessionLifecycleHost(hooks).attach(session)
        hooks.focusListener!!.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(1, session.focusLost)
        assertEquals(0, session.routeChanged)
        assertEquals(0, session.background)
    }

    @Test
    fun transientFocusLoss_forwards_gainDoesNot() {
        val hooks = RecordingHooks()
        val session = RecordingSession()
        VoiceSessionLifecycleHost(hooks).attach(session)
        hooks.focusListener!!.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        hooks.focusListener!!.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(1, session.focusLost)
    }

    @Test
    fun routeChange_forwardsOnAudioRouteChanged() {
        val hooks = RecordingHooks()
        val session = RecordingSession()
        VoiceSessionLifecycleHost(hooks).attach(session)
        hooks.routeCallback!!.onAudioDevicesAdded(emptyArray())
        hooks.routeCallback!!.onAudioDevicesRemoved(emptyArray())
        assertEquals(2, session.routeChanged)
        assertEquals(0, session.background)
    }

    @Test
    fun onStop_continuationOff_forwardsBackground() {
        val hooks = RecordingHooks()
        val session = RecordingSession()
        VoiceSessionLifecycleHost(hooks).attach(session)
        fireOnStop(hooks)
        assertEquals(1, session.background)
    }

    @Test
    fun onStop_continuationOn_withholdsBackground() {
        val hooks = RecordingHooks()
        val session = RecordingSession()
        val host = VoiceSessionLifecycleHost(hooks)
        host.attach(session)
        host.setContinuationActive(true)
        fireOnStop(hooks)
        assertEquals(0, session.background)
    }

    @Test
    fun detach_unregistersEverything_andSilencesSignals() {
        val hooks = RecordingHooks()
        val session = RecordingSession()
        val host = VoiceSessionLifecycleHost(hooks)
        host.attach(session)
        val focus = hooks.focusListener!!
        host.detach()
        assertEquals(1, hooks.abandonCalls)
        assertEquals(1, hooks.unregisterRouteCalls)
        assertEquals(1, hooks.removeObserverCalls)
        assertNull(hooks.lifecycleObserver)
        focus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(0, session.focusLost)
    }

    @Test
    fun doubleDetach_isIdempotent() {
        val hooks = RecordingHooks()
        val host = VoiceSessionLifecycleHost(hooks)
        host.attach(RecordingSession())
        host.detach()
        host.detach()
        assertEquals(1, hooks.abandonCalls)
        assertEquals(1, hooks.unregisterRouteCalls)
    }

    @Test
    fun doubleAttach_detachesPriorSession() {
        val hooks = RecordingHooks()
        val first = RecordingSession()
        val second = RecordingSession()
        val host = VoiceSessionLifecycleHost(hooks)
        host.attach(first)
        host.attach(second)
        assertEquals(1, hooks.abandonCalls)
        assertNotNull(hooks.focusListener)
        hooks.focusListener!!.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        assertEquals(0, first.focusLost)
        assertEquals(1, second.focusLost)
    }

    @Test
    fun detach_resetsContinuationFlag() {
        val hooks = RecordingHooks()
        val session = RecordingSession()
        val host = VoiceSessionLifecycleHost(hooks)
        host.attach(session)
        host.setContinuationActive(true)
        host.detach()
        host.attach(session)
        fireOnStop(hooks)
        assertEquals(1, session.background)
    }
}
