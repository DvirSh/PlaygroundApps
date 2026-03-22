package com.dvir.playground.ui.recipebook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.dvir.playground.R
import com.dvir.playground.api.AiParsedResult
import com.dvir.playground.api.BatchCreateRequest
import com.dvir.playground.api.CheckDuplicatesRequest
import com.dvir.playground.api.RetrofitClient
import com.dvir.playground.api.TranslateRequest
import com.dvir.playground.databinding.FragmentRecipeBookBinding
import com.dvir.playground.model.Recipe
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.Locale

class RecipeBookFragment : Fragment() {
    private var _binding: FragmentRecipeBookBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RecipePageAdapter
    private val recipes = mutableListOf<Recipe>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val json = arguments?.getString("recipeListJson") ?: return
        val type = object : TypeToken<List<Recipe>>() {}.type
        val parsed: List<Recipe> = Gson().fromJson(json, type)
        recipes.addAll(parsed)

        adapter = RecipePageAdapter(recipes) { position, holder ->
            translateRecipe(position, holder)
        }
        binding.viewPager.adapter = adapter

        updatePageIndicator(0)
        updateSaveButton()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position)
            }
        })

        binding.prevPageButton.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) binding.viewPager.currentItem = current - 1
        }

        binding.nextPageButton.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < recipes.size - 1) binding.viewPager.currentItem = current + 1
        }

        binding.deletePageButton.setOnClickListener {
            if (recipes.size <= 1) {
                Toast.makeText(requireContext(), "Cannot remove the last recipe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pos = binding.viewPager.currentItem
            adapter.removePage(pos)
            val newPos = if (pos >= recipes.size) recipes.size - 1 else pos
            binding.viewPager.setCurrentItem(newPos, false)
            updatePageIndicator(newPos)
            updateSaveButton()
        }

        binding.saveAllButton.setOnClickListener {
            saveAll()
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

    private fun translateRecipe(position: Int, holder: RecipePageAdapter.PageViewHolder) {
        val current = adapter.collectRecipe(holder, position)
        val parsedResult = AiParsedResult(
            title = current.title,
            description = current.description,
            ingredients = current.ingredients,
            steps = current.steps,
            tags = current.tags
        )

        holder.translateButton.isEnabled = false
        holder.translateProgress.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val translated = RetrofitClient.api.translateRecipe(
                    TranslateRequest(parsedResult, getTargetLanguage())
                )
                val translatedRecipe = current.copy(
                    title = translated.title,
                    description = translated.description,
                    ingredients = translated.ingredients,
                    steps = translated.steps,
                    tags = translated.tags
                )
                recipes[position] = translatedRecipe
                adapter.bindRecipeToHolder(holder, translatedRecipe)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.translate_failed), Toast.LENGTH_SHORT).show()
            } finally {
                holder.translateButton.isEnabled = true
                holder.translateProgress.visibility = View.GONE
            }
        }
    }

    private fun updatePageIndicator(position: Int) {
        binding.pageIndicator.text = getString(R.string.recipe_n_of_total, position + 1, recipes.size)
        binding.prevPageButton.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        binding.nextPageButton.visibility = if (position < recipes.size - 1) View.VISIBLE else View.INVISIBLE
    }

    private fun updateSaveButton() {
        binding.saveAllButton.text = getString(R.string.save_all_n, recipes.size)
    }

    private fun collectAllRecipes(): List<Recipe> {
        val collectedRecipes = mutableListOf<Recipe>()
        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView

        for (i in recipes.indices) {
            val holder = recyclerView?.findViewHolderForAdapterPosition(i)
            if (holder != null) {
                collectedRecipes.add(adapter.collectRecipe(holder, i))
            } else {
                collectedRecipes.add(recipes[i])
            }
        }
        return collectedRecipes
    }

    private fun saveAll() {
        val collectedRecipes = collectAllRecipes()
        val titles = collectedRecipes.map { it.title }

        binding.progressBar.visibility = View.VISIBLE
        binding.saveAllButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.checkDuplicates(CheckDuplicatesRequest(titles))
                val dups = response.duplicates
                binding.progressBar.visibility = View.GONE
                binding.saveAllButton.isEnabled = true

                if (dups.isNotEmpty()) {
                    val dupTitles = dups.map { it.title }
                    val dupNames = dupTitles.joinToString(", ") { "\"$it\"" }
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.duplicates_found_title)
                        .setMessage(getString(R.string.duplicates_found_message, dups.size, dupNames))
                        .setPositiveButton(R.string.replace_all) { _, _ ->
                            doSaveBatch(collectedRecipes, dups)
                        }
                        .setNegativeButton(R.string.keep_all) { _, _ ->
                            doSaveBatch(collectedRecipes, emptyList())
                        }
                        .setNeutralButton(R.string.cancel, null)
                        .show()
                } else {
                    doSaveBatch(collectedRecipes, emptyList())
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.saveAllButton.isEnabled = true
                doSaveBatch(collectedRecipes, emptyList())
            }
        }
    }

    private fun doSaveBatch(collectedRecipes: List<Recipe>, duplicatesToReplace: List<Recipe>) {
        binding.progressBar.visibility = View.VISIBLE
        binding.saveAllButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val dupMap = duplicatesToReplace.associateBy { it.title }

                // Update existing duplicates
                for (recipe in collectedRecipes) {
                    val existing = dupMap[recipe.title]
                    if (existing != null) {
                        RetrofitClient.api.updateRecipe(existing.id!!, recipe)
                    }
                }

                // Create new ones (non-duplicates)
                val newRecipes = collectedRecipes.filter { dupMap[it.title] == null }
                if (newRecipes.isNotEmpty()) {
                    RetrofitClient.api.createManualBatch(BatchCreateRequest(newRecipes))
                }

                Toast.makeText(
                    requireContext(),
                    getString(R.string.imported_n_recipes, collectedRecipes.size),
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().popBackStack(R.id.recipeListFragment, false)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Error saving", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.saveAllButton.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
