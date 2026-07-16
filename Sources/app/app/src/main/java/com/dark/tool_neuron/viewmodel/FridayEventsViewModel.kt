package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.repo.InboundEventCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// Thin bridge from the singleton InboundEventCenter to the Friday screens' in-app event card.
@HiltViewModel
class FridayEventsViewModel @Inject constructor(
    private val center: InboundEventCenter,
) : ViewModel() {

    val activeEvent: StateFlow<InboundEvent?> = center.activeEvent

    fun dismiss() = center.dismiss()
}
