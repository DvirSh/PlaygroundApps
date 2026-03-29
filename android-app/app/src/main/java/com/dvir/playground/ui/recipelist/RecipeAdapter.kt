package com.dvir.playground.ui.recipelist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dvir.playground.databinding.ItemRecipeBinding
import com.dvir.playground.model.Recipe

class RecipeAdapter(
    private val onClick: (Recipe) -> Unit,
    private val onAddPhoto: (Recipe) -> Unit = {}
) : ListAdapter<Recipe, RecipeAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecipeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemRecipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipe: Recipe) {
            binding.recipeTitle.text = recipe.title
            binding.recipeDescription.text = recipe.description ?: ""
            if (!recipe.image_url.isNullOrBlank()) {
                Glide.with(binding.root.context)
                    .load(recipe.image_url)
                    .centerCrop()
                    .into(binding.recipeImage)
            } else {
                binding.recipeImage.setImageDrawable(null)
                binding.recipeImage.setBackgroundResource(com.dvir.playground.R.color.search_bg)
            }
            binding.root.setOnClickListener { onClick(recipe) }
            binding.addPhotoButton.setOnClickListener { onAddPhoto(recipe) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(a: Recipe, b: Recipe) = a.id == b.id
        override fun areContentsTheSame(a: Recipe, b: Recipe) = a == b
    }
}
