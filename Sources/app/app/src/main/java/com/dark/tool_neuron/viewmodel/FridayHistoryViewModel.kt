package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FridayHistoryViewModel @Inject constructor() : ViewModel() {

    private val _reminders = MutableStateFlow(DEFAULT_REMINDERS)
    val reminders: StateFlow<List<String>> = _reminders.asStateFlow()

    companion object {
        private val DEFAULT_REMINDERS = listOf(
            "How do I improve my productivity at work?",
            "What are the advantages of online education?",
            "Suggest a 7-day workout plan for beginners",
            "Write a polite email to reschedule a meeting",
            "Explain quantum computing in simple words",
            "Best places to visit in Da Nang this summer",
        )
    }
}
