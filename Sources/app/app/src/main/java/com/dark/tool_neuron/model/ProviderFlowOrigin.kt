package com.dark.tool_neuron.model

/*
 * FRI-574 round-5 BLOCKER 1: identifies which Friday surface the user opened the
 * provider selector / OnboardingProviders flow from, so Add/Manage + Continue return
 * there instead of always landing on FridayVoice. Carried as an optional route arg on
 * OnboardingProviders (null = first-run onboarding path -> legacy Voice landing). NOT
 * persisted; describes the current navigation flow only.
 */
enum class ProviderFlowOrigin { Voice, Chat }
