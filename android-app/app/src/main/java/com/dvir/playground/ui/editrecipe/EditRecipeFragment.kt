package com.dvir.playground.ui.editrecipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.dvir.playground.R
import androidx.appcompat.app.AlertDialog
import com.dvir.playground.api.AiParsedResult
import com.dvir.playground.api.CheckDuplicatesRequest
import com.dvir.playground.api.ParseTextRequest
import com.dvir.playground.api.RetrofitClient
import com.dvir.playground.api.TranslateRequest
import java.util.Locale
import com.dvir.playground.databinding.FragmentEditRecipeBinding
import com.dvir.playground.model.Ingredient
import com.dvir.playground.model.Recipe
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import kotlinx.coroutines.launch

class EditRecipeFragment : Fragment() {
    private var _binding: FragmentEditRecipeBinding? = null
    private val binding get() = _binding!!
    private lateinit var recipe: Recipe
    private var isCreateMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val json = arguments?.getString("recipeJson") ?: return
        recipe = Gson().fromJson(json, Recipe::class.java)
        isCreateMode = arguments?.getBoolean("createMode", false) ?: false

        if (isCreateMode) {
            binding.saveButton.text = getString(R.string.create_recipe)
            binding.pasteCard.visibility = View.VISIBLE
            binding.parseButton.setOnClickListener { parseFromPaste() }
            binding.translateContainer.visibility = View.VISIBLE
            binding.translateButton.setOnClickListener { translateCurrentRecipe() }
        }

        binding.titleInput.setText(recipe.title)
        binding.descriptionInput.setText(recipe.description ?: "")

        // Tags
        recipe.tags?.forEach { tag -> addTagChip(tag) }
        binding.addTagButton.setOnClickListener {
            val tag = binding.tagInput.text?.toString()?.trim()
            if (!tag.isNullOrBlank()) {
                addTagChip(tag)
                binding.tagInput.text?.clear()
            }
        }

        // Ingredients
        if (recipe.ingredients.isEmpty() && isCreateMode) {
            addIngredientRow(Ingredient("", "", ""))
        } else {
            recipe.ingredients.forEach { addIngredientRow(it) }
        }
        binding.addIngredientButton.setOnClickListener {
            addIngredientRow(Ingredient("", "", ""))
        }

        // Steps
        if (recipe.steps.isEmpty() && isCreateMode) {
            addStepRow("")
        } else {
            recipe.steps.forEach { addStepRow(it) }
        }
        binding.addStepButton.setOnClickListener {
            addStepRow("")
        }

        binding.cancelButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.saveButton.setOnClickListener {
            saveRecipe()
        }
    }

    private fun getTargetLanguage(): String {
        val lang = Locale.getDefault().language
        return when (lang) {
            "iw", "he" -> "Hebrew"
            "en" -> "English"
            "ar" -> "Arabic"
            "ru" -> "Russian"
            "fr" -> "French"
            "es" -> "Spanish"
            else -> lang
        }
    }

    private fun translateCurrentRecipe() {
        val parsedResult = AiParsedResult(
            title = binding.titleInput.text.toString().trim(),
            description = binding.descriptionInput.text.toString().trim().ifBlank { null },
            ingredients = collectIngredients(),
            steps = collectSteps(),
            tags = collectTags()
        )

        binding.translateButton.isEnabled = false
        binding.translateProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val translated = RetrofitClient.api.translateRecipe(
                    TranslateRequest(parsedResult, getTargetLanguage())
                )
                binding.titleInput.setText(translated.title)
                binding.descriptionInput.setText(translated.description ?: "")

                binding.tagsChipGroup.removeAllViews()
                translated.tags?.forEach { addTagChip(it) }

                binding.ingredientsList.removeAllViews()
                translated.ingredients.forEach { addIngredientRow(it) }

                binding.stepsList.removeAllViews()
                translated.steps.forEach { addStepRow(it) }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.translate_failed), Toast.LENGTH_SHORT).show()
            } finally {
                binding.translateButton.isEnabled = true
                binding.translateProgress.visibility = View.GONE
            }
        }
    }

    private fun addTagChip(tag: String) {
        val chip = Chip(requireContext()).apply {
            text = tag
            isCloseIconVisible = true
            setOnCloseIconClickListener { binding.tagsChipGroup.removeView(this) }
            setChipBackgroundColorResource(R.color.ingredient_bg)
            setTextColor(resources.getColor(R.color.text_primary, null))
            setCloseIconTintResource(R.color.text_secondary)
        }
        binding.tagsChipGroup.addView(chip)
    }

    private fun parseFromPaste() {
        val text = binding.pasteInput.text?.toString()?.trim()
        if (text.isNullOrBlank()) return

        binding.parseButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val parsed = RetrofitClient.api.parseText(ParseTextRequest(text))

                // Fill title if empty
                if (binding.titleInput.text.isNullOrBlank()) {
                    binding.titleInput.setText(parsed.title)
                }

                // Fill description
                if (!parsed.description.isNullOrBlank() && binding.descriptionInput.text.isNullOrBlank()) {
                    binding.descriptionInput.setText(parsed.description)
                }

                // Add parsed ingredients
                binding.ingredientsList.removeAllViews()
                parsed.ingredients.forEach { addIngredientRow(it) }

                // Add parsed steps
                binding.stepsList.removeAllViews()
                parsed.steps.forEach { addStepRow(it) }

                // Collapse the paste card
                binding.pasteCard.visibility = View.GONE
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Parse failed", Toast.LENGTH_SHORT).show()
            } finally {
                binding.parseButton.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun addIngredientRow(ingredient: Ingredient) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_edit_ingredient, binding.ingredientsList, false)
        row.findViewById<EditText>(R.id.amountInput).setText(ingredient.amount)
        row.findViewById<EditText>(R.id.unitInput).setText(ingredient.unit)
        row.findViewById<EditText>(R.id.nameInput).setText(ingredient.name)
        row.findViewById<ImageButton>(R.id.removeButton).setOnClickListener {
            binding.ingredientsList.removeView(row)
        }
        binding.ingredientsList.addView(row)
    }

    private fun addStepRow(step: String) {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_edit_step, binding.stepsList, false)
        row.findViewById<EditText>(R.id.stepInput).setText(step)
        row.findViewById<ImageButton>(R.id.removeButton).setOnClickListener {
            binding.stepsList.removeView(row)
            renumberSteps()
        }
        binding.stepsList.addView(row)
        renumberSteps()
    }

    private fun renumberSteps() {
        for (i in 0 until binding.stepsList.childCount) {
            val row = binding.stepsList.getChildAt(i)
            row.findViewById<TextView>(R.id.stepNumber).text = "${i + 1}"
        }
    }

    private fun collectTags(): List<String> {
        val tags = mutableListOf<String>()
        for (i in 0 until binding.tagsChipGroup.childCount) {
            val chip = binding.tagsChipGroup.getChildAt(i) as? Chip
            chip?.text?.toString()?.let { tags.add(it) }
        }
        return tags
    }

    private fun collectIngredients(): List<Ingredient> {
        val ingredients = mutableListOf<Ingredient>()
        for (i in 0 until binding.ingredientsList.childCount) {
            val row = binding.ingredientsList.getChildAt(i)
            val amount = row.findViewById<EditText>(R.id.amountInput).text.toString().trim()
            val unit = row.findViewById<EditText>(R.id.unitInput).text.toString().trim()
            val name = row.findViewById<EditText>(R.id.nameInput).text.toString().trim()
            if (name.isNotBlank()) {
                ingredients.add(Ingredient(amount, unit, name))
            }
        }
        return ingredients
    }

    private fun collectSteps(): List<String> {
        val steps = mutableListOf<String>()
        for (i in 0 until binding.stepsList.childCount) {
            val row = binding.stepsList.getChildAt(i)
            val text = row.findViewById<EditText>(R.id.stepInput).text.toString().trim()
            if (text.isNotBlank()) {
                steps.add(text)
            }
        }
        return steps
    }

    private fun saveRecipe() {
        val title = binding.titleInput.text.toString().trim()
        if (title.isBlank()) {
            Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = recipe.copy(
            title = title,
            description = binding.descriptionInput.text.toString().trim().ifBlank { null },
            ingredients = collectIngredients(),
            steps = collectSteps(),
            tags = collectTags()
        )

        if (!isCreateMode) {
            doSave(updated, replaceId = recipe.id)
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.saveButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.checkDuplicates(CheckDuplicatesRequest(listOf(title)))
                val dup = response.duplicates.firstOrNull()
                if (dup != null) {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.isEnabled = true
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.duplicate_found_title)
                        .setMessage(getString(R.string.duplicate_found_message, title))
                        .setPositiveButton(R.string.replace) { _, _ -> doSave(updated, replaceId = dup.id) }
                        .setNegativeButton(R.string.keep_both) { _, _ -> doSave(updated, replaceId = null) }
                        .setNeutralButton(R.string.cancel, null)
                        .show()
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.saveButton.isEnabled = true
                    doSave(updated, replaceId = null)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.saveButton.isEnabled = true
                doSave(updated, replaceId = null)
            }
        }
    }

    private fun doSave(updated: Recipe, replaceId: String?) {
        binding.progressBar.visibility = View.VISIBLE
        binding.saveButton.isEnabled = false

        lifecycleScope.launch {
            try {
                if (replaceId != null) {
                    RetrofitClient.api.updateRecipe(replaceId, updated)
                } else if (isCreateMode) {
                    RetrofitClient.api.createManual(updated)
                } else {
                    RetrofitClient.api.updateRecipe(recipe.id!!, updated)
                }
                findNavController().popBackStack(R.id.recipeListFragment, false)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Error saving", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.saveButton.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
