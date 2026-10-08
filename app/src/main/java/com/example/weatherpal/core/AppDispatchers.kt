package com.example.weatherpal.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class AppDispatchers(val main: CoroutineDispatcher = Dispatchers.Main.immediate)
