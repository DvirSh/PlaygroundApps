package com.dvir.playground.ui.recipelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvir.playground.api.RetrofitClient
import com.dvir.playground.model.Recipe
import kotlinx.coroutines.launch

class RecipeListViewModel : ViewModel() {
    private val _recipes = MutableLiveData<List<Recipe>>(emptyList())
    val recipes: LiveData<List<Recipe>> = _recipes

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _tags = MutableLiveData<List<String>>(emptyList())
    val tags: LiveData<List<String>> = _tags

    var selectedTag: String? = null
        private set

    fun loadRecipes(query: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _recipes.value = RetrofitClient.api.getRecipes(query, selectedTag)
            } catch (e: Exception) {
                _recipes.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadTags() {
        viewModelScope.launch {
            try {
                _tags.value = RetrofitClient.api.getTags()
            } catch (_: Exception) {
            }
        }
    }

    fun selectTag(tag: String?) {
        selectedTag = tag
        loadRecipes()
    }
}
