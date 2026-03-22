package com.dvir.playground.ui.recipedetail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.dvir.playground.R
import com.dvir.playground.api.RetrofitClient
import com.dvir.playground.databinding.FragmentRecipeDetailBinding
import com.dvir.playground.model.Recipe
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import kotlinx.coroutines.launch

class RecipeDetailFragment : Fragment() {
    private var _binding: FragmentRecipeDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val json = arguments?.getString("recipeJson") ?: return
        val recipe = Gson().fromJson(json, Recipe::class.java)

        binding.recipeTitle.text = recipe.title

        // Tags
        recipe.tags?.filter { it.isNotBlank() }?.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isClickable = false
                setChipBackgroundColorResource(R.color.ingredient_bg)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
            binding.tagsChipGroup.addView(chip)
        }
        if (recipe.tags.isNullOrEmpty()) {
            binding.tagsChipGroup.visibility = View.GONE
        }

        // Source link
        val isScannedImage = recipe.source_url.startsWith("http") &&
            recipe.source_url.contains("recipe-scans")
        val isWebUrl = recipe.source_url.startsWith("http") && !isScannedImage

        when {
            isWebUrl -> {
                binding.sourceLink.text = getString(R.string.view_original)
                binding.sourceLink.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recipe.source_url)))
                }
            }
            isScannedImage -> {
                binding.sourceLink.text = getString(R.string.view_scan)
                binding.sourceLink.setOnClickListener {
                    showScannedImage(recipe.source_url)
                }
            }
            else -> {
                binding.sourceLink.visibility = View.GONE
            }
        }

        // Edit button
        binding.editButton.setOnClickListener {
            val bundle = Bundle().apply { putString("recipeJson", json) }
            findNavController().navigate(R.id.action_detail_to_edit, bundle)
        }

        // Delete button
        binding.deleteButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setPositiveButton(R.string.delete_recipe) { _, _ -> deleteRecipe(recipe) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.recipeDescription.text = recipe.description ?: ""
        if (recipe.description.isNullOrBlank()) {
            binding.recipeDescription.visibility = View.GONE
        }

        if (!recipe.image_url.isNullOrBlank()) {
            binding.imageCard.visibility = View.VISIBLE
            Glide.with(this).load(recipe.image_url).centerCrop().into(binding.recipeImage)
        }

        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp4 = (4 * resources.displayMetrics.density).toInt()

        recipe.ingredients.forEach { ingredient ->
            val text = buildString {
                if (ingredient.amount.isNotBlank()) append("${ingredient.amount} ")
                if (ingredient.unit.isNotBlank()) append("${ingredient.unit} ")
                append(ingredient.name)
            }
            val tv = TextView(requireContext()).apply {
                this.text = text
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                setBackgroundResource(R.drawable.ingredient_bg)
                setPadding(dp12, dp8, dp12, dp8)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = dp4
                layoutParams = params
            }
            binding.ingredientsList.addView(tv)
        }

        val dp28 = (28 * resources.displayMetrics.density).toInt()

        recipe.steps.forEachIndexed { index, step ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.bottomMargin = dp12
                layoutParams = params
            }

            val number = TextView(requireContext()).apply {
                this.text = "${index + 1}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
                setBackgroundResource(R.drawable.step_number_bg)
                gravity = Gravity.CENTER
                val size = dp28
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dp12
                    topMargin = dp4
                }
            }

            val stepText = TextView(requireContext()).apply {
                this.text = step
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setLineSpacing(4f, 1f)
            }

            row.addView(number)
            row.addView(stepText)
            binding.stepsList.addView(row)
        }
    }

    private fun showScannedImage(imageUrl: String) {
        val imageView = android.widget.ImageView(requireContext()).apply {
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 0)
        }
        Glide.with(this).load(imageUrl).into(imageView)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.view_scan)
            .setView(imageView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun deleteRecipe(recipe: Recipe) {
        lifecycleScope.launch {
            try {
                RetrofitClient.api.deleteRecipe(recipe.id!!)
                findNavController().popBackStack()
            } catch (e: Exception) {
                // silent fail
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
