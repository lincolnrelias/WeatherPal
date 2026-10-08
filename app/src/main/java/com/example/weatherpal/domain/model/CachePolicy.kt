package com.example.weatherpal.domain.model

data class CachePolicy(val maxCities: Int = 3) {
    init {
        require(maxCities >= 1)
    }
}
