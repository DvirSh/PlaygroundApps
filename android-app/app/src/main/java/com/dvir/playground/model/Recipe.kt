package com.dvir.playground.model

data class Ingredient(
    val amount: String,
    val unit: String,
    val name: String
)

data class Recipe(
    val id: String? = null,
    val title: String,
    val description: String? = null,
    val image_url: String? = null,
    val source_url: String = "manual",
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<String>? = null,
    val created_at: String? = null
)

data class ParsedTextResult(
    val title: String,
    val description: String?,
    val ingredients: List<Ingredient>,
    val steps: List<String>
)
