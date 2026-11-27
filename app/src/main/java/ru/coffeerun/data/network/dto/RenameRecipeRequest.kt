package ru.coffeerun.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RenameRecipeRequest(val name: String)
