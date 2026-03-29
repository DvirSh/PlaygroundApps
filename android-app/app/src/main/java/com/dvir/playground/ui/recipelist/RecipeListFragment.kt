package com.dvir.playground.ui.recipelist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dvir.playground.R
import com.dvir.playground.api.RetrofitClient
import com.dvir.playground.databinding.FragmentRecipeListBinding
import com.dvir.playground.model.Recipe
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RecipeListFragment : Fragment() {
    private var _binding: FragmentRecipeListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipeListViewModel by viewModels()
    private var pendingPhotoRecipe: Recipe? = null

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val recipe = pendingPhotoRecipe ?: return@registerForActivityResult
            uploadPhotoForRecipe(recipe, uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = RecipeAdapter(
            onClick = { recipe ->
                val json = Gson().toJson(recipe)
                val bundle = Bundle().apply { putString("recipeJson", json) }
                findNavController().navigate(R.id.action_list_to_detail, bundle)
            },
            onAddPhoto = { recipe ->
                pendingPhotoRecipe = recipe
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                photoPicker.launch(intent)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewModel.recipes.observe(viewLifecycleOwner) { recipes ->
            adapter.submitList(recipes)
            binding.emptyState.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (recipes.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.tags.observe(viewLifecycleOwner) { tags ->
            binding.tagChipGroup.removeAllViews()
            if (tags.isNotEmpty()) {
                binding.tagScrollView.visibility = View.VISIBLE

                // "All" chip
                val allChip = Chip(requireContext()).apply {
                    text = getString(R.string.all_tags)
                    isCheckable = true
                    isChecked = viewModel.selectedTag == null
                    setChipBackgroundColorResource(R.color.search_bg)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                    setOnClickListener {
                        viewModel.selectTag(null)
                        refreshTagSelection()
                    }
                }
                binding.tagChipGroup.addView(allChip)

                tags.forEach { tag ->
                    val chip = Chip(requireContext()).apply {
                        text = tag
                        isCheckable = true
                        isChecked = viewModel.selectedTag == tag
                        setChipBackgroundColorResource(R.color.search_bg)
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                        setOnClickListener {
                            viewModel.selectTag(tag)
                            refreshTagSelection()
                        }
                    }
                    binding.tagChipGroup.addView(chip)
                }
            } else {
                binding.tagScrollView.visibility = View.GONE
            }
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.loadRecipes(query?.takeIf { it.isNotBlank() })
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) viewModel.loadRecipes()
                return true
            }
        })

        viewModel.loadRecipes()
        viewModel.loadTags()
    }

    private fun uploadPhotoForRecipe(recipe: Recipe, uri: android.net.Uri) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()

                val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
                val requestBody = bytes.toRequestBody(mimeType.toMediaType())
                val part = MultipartBody.Part.createFormData("image", "recipe.jpg", requestBody)
                val uploadResult = RetrofitClient.api.uploadImage(part)

                RetrofitClient.api.updateRecipe(recipe.id!!, recipe.copy(image_url = uploadResult.url))
                viewModel.loadRecipes()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Upload failed", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun refreshTagSelection() {
        for (i in 0 until binding.tagChipGroup.childCount) {
            val chip = binding.tagChipGroup.getChildAt(i) as? Chip ?: continue
            chip.isChecked = when {
                i == 0 -> viewModel.selectedTag == null
                else -> chip.text.toString() == viewModel.selectedTag
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRecipes()
        viewModel.loadTags()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
