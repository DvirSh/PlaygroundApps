package com.dvir.playground.ui.addrecipe

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.dvir.playground.R
import com.dvir.playground.api.RetrofitClient
import com.dvir.playground.databinding.FragmentAddRecipeBinding
import com.dvir.playground.model.Recipe
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class AddRecipeFragment : Fragment() {
    private var _binding: FragmentAddRecipeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AddRecipeViewModel by viewModels()
    private var currentImageUri: Uri? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            currentImageUri = uri
            processImage(uri)
        }
    }

    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            processPdf(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddRecipeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.submitButton.setOnClickListener {
            val url = binding.urlInput.text?.toString()?.trim()
            if (url.isNullOrBlank()) {
                binding.errorText.text = getString(R.string.error_empty_url)
                binding.errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            viewModel.addRecipe(url)
        }

        binding.composeButton.setOnClickListener {
            val emptyRecipe = Recipe(title = "", source_url = "manual")
            val json = Gson().toJson(emptyRecipe)
            val bundle = Bundle().apply {
                putString("recipeJson", json)
                putBoolean("createMode", true)
            }
            findNavController().navigate(R.id.action_add_to_edit, bundle)
        }

        binding.scanButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePicker.launch(intent)
        }

        binding.pdfButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            pdfPicker.launch(intent)
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.submitButton.isEnabled = !loading
            binding.composeButton.isEnabled = !loading
            binding.scanButton.isEnabled = !loading
            binding.pdfButton.isEnabled = !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.errorText.text = error
                binding.errorText.visibility = View.VISIBLE
            } else {
                binding.errorText.visibility = View.GONE
            }
        }

        viewModel.success.observe(viewLifecycleOwner) { success ->
            if (success) {
                binding.urlInput.text?.clear()
                findNavController().navigate(R.id.recipeListFragment)
            }
        }
    }

    private fun navigateToEdit(parsed: com.dvir.playground.api.AiParsedResult, sourceUrl: String) {
        val recipe = Recipe(
            title = parsed.title,
            description = parsed.description,
            ingredients = parsed.ingredients,
            steps = parsed.steps,
            tags = parsed.tags,
            source_url = sourceUrl
        )
        val json = Gson().toJson(recipe)
        val bundle = Bundle().apply {
            putString("recipeJson", json)
            putBoolean("createMode", true)
        }
        findNavController().navigate(R.id.action_add_to_edit, bundle)
    }

    private fun navigateToBook(parsedList: List<com.dvir.playground.api.AiParsedResult>, sourceUrl: String) {
        val recipes = parsedList.map { parsed ->
            Recipe(
                title = parsed.title,
                description = parsed.description,
                ingredients = parsed.ingredients,
                steps = parsed.steps,
                tags = parsed.tags,
                source_url = sourceUrl
            )
        }
        val json = Gson().toJson(recipes)
        val bundle = Bundle().apply {
            putString("recipeListJson", json)
        }
        findNavController().navigate(R.id.action_add_to_book, bundle)
    }

    private fun processImage(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()

                val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"

                // Send image to AI for parsing
                val requestBody = bytes.toRequestBody(mimeType.toMediaType())
                val parsePart = MultipartBody.Part.createFormData("image", "scan.jpg", requestBody)
                val result = RetrofitClient.api.parseImage(parsePart)

                // Upload image to storage for "view source"
                var sourceUrl = "scanned"
                try {
                    val uploadBody = bytes.toRequestBody(mimeType.toMediaType())
                    val uploadPart = MultipartBody.Part.createFormData("image", "scan.jpg", uploadBody)
                    val uploadResult = RetrofitClient.api.uploadImage(uploadPart)
                    sourceUrl = uploadResult.url
                } catch (_: Exception) {}

                if (result.recipes.size > 1) {
                    navigateToBook(result.recipes, sourceUrl)
                } else {
                    navigateToEdit(result.recipes.first(), sourceUrl)
                }
            } catch (e: Exception) {
                binding.errorText.text = e.message ?: getString(R.string.scan_failed)
                binding.errorText.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun processPdf(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()

                val requestBody = bytes.toRequestBody("application/pdf".toMediaType())
                val part = MultipartBody.Part.createFormData("file", "recipe.pdf", requestBody)
                val result = RetrofitClient.api.parsePdf(part)

                if (result.recipes.size > 1) {
                    navigateToBook(result.recipes, "pdf")
                } else {
                    navigateToEdit(result.recipes.first(), "pdf")
                }
            } catch (e: Exception) {
                binding.errorText.text = e.message ?: getString(R.string.pdf_parse_failed)
                binding.errorText.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
