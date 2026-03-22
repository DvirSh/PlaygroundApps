package com.dvir.playground.ui.addrecipe

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvir.playground.api.AddRecipeRequest
import com.dvir.playground.api.RetrofitClient
import kotlinx.coroutines.launch

class AddRecipeViewModel : ViewModel() {
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _success = MutableLiveData(false)
    val success: LiveData<Boolean> = _success

    fun addRecipe(url: String) {
        _error.value = null
        _success.value = false
        viewModelScope.launch {
            _loading.value = true
            try {
                RetrofitClient.api.addRecipe(AddRecipeRequest(url))
                _success.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add recipe"
            } finally {
                _loading.value = false
            }
        }
    }
}
