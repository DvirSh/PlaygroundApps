package com.dvir.playground.ui.recipebook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dvir.playground.R
import com.dvir.playground.model.Ingredient
import com.dvir.playground.model.Recipe
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

class RecipePageAdapter(
    private val recipes: MutableList<Recipe>,
    private val onTranslateClick: ((position: Int, holder: PageViewHolder) -> Unit)? = null
) : RecyclerView.Adapter<RecipePageAdapter.PageViewHolder>() {

    class PageViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val titleInput: TextInputEditText = view.findViewById(R.id.titleInput)
        val descriptionInput: TextInputEditText = view.findViewById(R.id.descriptionInput)
        val tagsChipGroup: ChipGroup = view.findViewById(R.id.tagsChipGroup)
        val tagInput: TextInputEditText = view.findViewById(R.id.tagInput)
        val addTagButton: View = view.findViewById(R.id.addTagButton)
        val ingredientsList: LinearLayout = view.findViewById(R.id.ingredientsList)
        val addIngredientButton: View = view.findViewById(R.id.addIngredientButton)
        val stepsList: LinearLayout = view.findViewById(R.id.stepsList)
        val addStepButton: View = view.findViewById(R.id.addStepButton)
        val translateButton: MaterialButton = view.findViewById(R.id.translateButton)
        val translateProgress: ProgressBar = view.findViewById(R.id.translateProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_recipe_edit, parent, false)
        return PageViewHolder(view)
    }

    override fun getItemCount(): Int = recipes.size

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        bindRecipeToHolder(holder, recipes[position])

        holder.translateButton.setOnClickListener {
            onTranslateClick?.invoke(holder.adapterPosition, holder)
        }
    }

    fun bindRecipeToHolder(holder: PageViewHolder, recipe: Recipe) {
        holder.titleInput.setText(recipe.title)
        holder.descriptionInput.setText(recipe.description ?: "")

        // Tags
        holder.tagsChipGroup.removeAllViews()
        recipe.tags?.forEach { tag -> addTagChip(holder, tag) }
        holder.addTagButton.setOnClickListener {
            val tag = holder.tagInput.text?.toString()?.trim()
            if (!tag.isNullOrBlank()) {
                addTagChip(holder, tag)
                holder.tagInput.text?.clear()
            }
        }

        // Ingredients
        holder.ingredientsList.removeAllViews()
        if (recipe.ingredients.isEmpty()) {
            addIngredientRow(holder, Ingredient("", "", ""))
        } else {
            recipe.ingredients.forEach { addIngredientRow(holder, it) }
        }
        holder.addIngredientButton.setOnClickListener {
            addIngredientRow(holder, Ingredient("", "", ""))
        }

        // Steps
        holder.stepsList.removeAllViews()
        if (recipe.steps.isEmpty()) {
            addStepRow(holder, "")
        } else {
            recipe.steps.forEach { addStepRow(holder, it) }
        }
        holder.addStepButton.setOnClickListener {
            addStepRow(holder, "")
        }
    }

    private fun addTagChip(holder: PageViewHolder, tag: String) {
        val chip = Chip(holder.view.context).apply {
            text = tag
            isCloseIconVisible = true
            setOnCloseIconClickListener { holder.tagsChipGroup.removeView(this) }
            setChipBackgroundColorResource(R.color.ingredient_bg)
            setTextColor(holder.view.resources.getColor(R.color.text_primary, null))
            setCloseIconTintResource(R.color.text_secondary)
        }
        holder.tagsChipGroup.addView(chip)
    }

    private fun addIngredientRow(holder: PageViewHolder, ingredient: Ingredient) {
        val row = LayoutInflater.from(holder.view.context)
            .inflate(R.layout.item_edit_ingredient, holder.ingredientsList, false)
        row.findViewById<EditText>(R.id.amountInput).setText(ingredient.amount)
        row.findViewById<EditText>(R.id.unitInput).setText(ingredient.unit)
        row.findViewById<EditText>(R.id.nameInput).setText(ingredient.name)
        row.findViewById<ImageButton>(R.id.removeButton).setOnClickListener {
            holder.ingredientsList.removeView(row)
        }
        holder.ingredientsList.addView(row)
    }

    private fun addStepRow(holder: PageViewHolder, step: String) {
        val row = LayoutInflater.from(holder.view.context)
            .inflate(R.layout.item_edit_step, holder.stepsList, false)
        row.findViewById<EditText>(R.id.stepInput).setText(step)
        row.findViewById<ImageButton>(R.id.removeButton).setOnClickListener {
            holder.stepsList.removeView(row)
            renumberSteps(holder)
        }
        holder.stepsList.addView(row)
        renumberSteps(holder)
    }

    private fun renumberSteps(holder: PageViewHolder) {
        for (i in 0 until holder.stepsList.childCount) {
            val row = holder.stepsList.getChildAt(i)
            row.findViewById<TextView>(R.id.stepNumber).text = "${i + 1}"
        }
    }

    fun collectRecipe(holder: RecyclerView.ViewHolder, position: Int): Recipe {
        val h = holder as PageViewHolder
        val tags = mutableListOf<String>()
        for (i in 0 until h.tagsChipGroup.childCount) {
            val chip = h.tagsChipGroup.getChildAt(i) as? Chip
            chip?.text?.toString()?.let { tags.add(it) }
        }

        val ingredients = mutableListOf<Ingredient>()
        for (i in 0 until h.ingredientsList.childCount) {
            val row = h.ingredientsList.getChildAt(i)
            val amount = row.findViewById<EditText>(R.id.amountInput).text.toString().trim()
            val unit = row.findViewById<EditText>(R.id.unitInput).text.toString().trim()
            val name = row.findViewById<EditText>(R.id.nameInput).text.toString().trim()
            if (name.isNotBlank()) {
                ingredients.add(Ingredient(amount, unit, name))
            }
        }

        val steps = mutableListOf<String>()
        for (i in 0 until h.stepsList.childCount) {
            val row = h.stepsList.getChildAt(i)
            val text = row.findViewById<EditText>(R.id.stepInput).text.toString().trim()
            if (text.isNotBlank()) {
                steps.add(text)
            }
        }

        return recipes[position].copy(
            title = h.titleInput.text.toString().trim().ifBlank { "Untitled" },
            description = h.descriptionInput.text.toString().trim().ifBlank { null },
            ingredients = ingredients,
            steps = steps,
            tags = tags
        )
    }

    fun removePage(position: Int) {
        recipes.removeAt(position)
        notifyDataSetChanged()
    }
}
