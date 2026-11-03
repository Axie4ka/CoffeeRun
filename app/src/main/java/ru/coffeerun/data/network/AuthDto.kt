package ru.coffeerun.data.network

import kotlinx.serialization.Serializable

// Вход и регистрация — поля один в один со схемами docs/openapi.yaml.

@Serializable
data class LoginRequest(val phone: String, val password: String)

@Serializable
data class RegisterRequest(val name: String, val phone: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class UserDto(val name: String, val phone: String)
