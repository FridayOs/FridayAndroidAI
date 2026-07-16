package com.dark.tool_neuron.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Forward-only signal seam: an attached session (cloud handle wrapper or local-route adapter)
// implements this; the host never decides to tear anything down, it only forwards OS signals.
// All teardown discipline stays where FRI-548 put it (LiveSessionEngine / VM callers).
interface AttachedSession {
    fun onAudioFocusLost()
    fun onAudioRouteChanged()
    fun onBackground()
}

// Abstracts the Android system APIs the host touches so JVM tests can drive focus/route/lifecycle
// signals without a device or Robolectric. AndroidSystemHooks below is the real implementation.
internal interface SystemHooks {
    fun registerFocus(listener: AudioManager.OnAudioFocusChangeListener): Boolean
    fun abandonFocus()
    fun registerRouteCallback(callback: AudioDeviceCallback)
    fun unregisterRouteCallback(callback: AudioDeviceCallback)
    fun addLifecycleObserver(observer: LifecycleEventObserver)
    fun removeLifecycleObserver(observer: LifecycleEventObserver)
}

// Registered when a live-voice session starts, unregistered on session end — attach/detach are
// idempotent so double-attach or double-detach never leaks or double-unregisters a listener.
@Singleton
class VoiceSessionLifecycleHost internal constructor(
    private val hooks: SystemHooks,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(AndroidSystemHooks(context))

    private var session: AttachedSession? = null
    private var continuationActive = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            session?.onAudioFocusLost()
        }
    }

    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            session?.onAudioRouteChanged()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            session?.onAudioRouteChanged()
        }
    }

    // Continuation-off ON_STOP forwards to onBackground() (full teardown per engine contract);
    // continuation-on simply withholds the forward — the FGS keeps the session alive instead.
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP && !continuationActive) {
            session?.onBackground()
        }
    }

    fun attach(attached: AttachedSession) {
        if (session != null) detach()
        session = attached
        hooks.registerFocus(focusListener)
        hooks.registerRouteCallback(routeCallback)
        hooks.addLifecycleObserver(lifecycleObserver)
    }

    fun setContinuationActive(active: Boolean) {
        continuationActive = active
    }

    fun detach() {
        if (session == null) return
        hooks.abandonFocus()
        hooks.unregisterRouteCallback(routeCallback)
        hooks.removeLifecycleObserver(lifecycleObserver)
        session = null
        continuationActive = false
    }
}

private class AndroidSystemHooks(private val context: Context) : SystemHooks {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    override fun registerFocus(listener: AudioManager.OnAudioFocusChangeListener): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(listener)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    override fun registerRouteCallback(callback: AudioDeviceCallback) {
        audioManager.registerAudioDeviceCallback(callback, null)
    }

    override fun unregisterRouteCallback(callback: AudioDeviceCallback) {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    override fun addLifecycleObserver(observer: LifecycleEventObserver) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    override fun removeLifecycleObserver(observer: LifecycleEventObserver) {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
    }
}
