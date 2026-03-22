package com.dvir.playground.api

import com.dvir.playground.model.Recipe
import com.dvir.playground.model.ParsedTextResult
import okhttp3.MultipartBody
import retrofit2.http.*

data class AddRecipeRequest(val url: String)
data class ParseTextRequest(val text: String)

interface RecipeApi {
    @GET("recipes")
    suspend fun getRecipes(
        @Query("q") query: String? = null,
        @Query("tag") tag: String? = null
    ): List<Recipe>

    @POST("recipes")
    suspend fun addRecipe(@Body request: AddRecipeRequest): Recipe

    @POST("recipes/manual")
    suspend fun createManual(@Body recipe: Recipe): Recipe

    @POST("recipes/parse-text")
    suspend fun parseText(@Body request: ParseTextRequest): ParsedTextResult

    @PUT("recipes/{id}")
    suspend fun updateRecipe(@Path("id") id: String, @Body recipe: Recipe): Recipe

    @DELETE("recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: String)

    @Multipart
    @POST("recipes/upload-image")
    suspend fun uploadImage(@Part image: MultipartBody.Part): UploadResult

    @Multipart
    @POST("recipes/parse-image")
    suspend fun parseImage(@Part image: MultipartBody.Part): AiParsedMultiResult

    @Multipart
    @POST("recipes/parse-pdf")
    suspend fun parsePdf(@Part file: MultipartBody.Part): AiParsedMultiResult

    @POST("recipes/manual/batch")
    suspend fun createManualBatch(@Body request: BatchCreateRequest): List<Recipe>

    @POST("recipes/check-duplicates")
    suspend fun checkDuplicates(@Body request: CheckDuplicatesRequest): CheckDuplicatesResponse

    @POST("recipes/translate")
    suspend fun translateRecipe(@Body request: TranslateRequest): AiParsedResult

    @GET("recipes/tags")
    suspend fun getTags(): List<String>
}

data class UploadResult(val url: String)

data class CheckDuplicatesRequest(val titles: List<String>)

data class CheckDuplicatesResponse(val duplicates: List<Recipe>)

data class TranslateRequest(
    val recipe: AiParsedResult,
    val targetLang: String
)

data class AiParsedResult(
    val title: String,
    val description: String?,
    val ingredients: List<com.dvir.playground.model.Ingredient>,
    val steps: List<String>,
    val tags: List<String>?
)

data class AiParsedMultiResult(
    val recipes: List<AiParsedResult>
)

data class BatchCreateRequest(
    val recipes: List<com.dvir.playground.model.Recipe>
)
