package com.diary.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diary.app.data.Note
import com.diary.app.databinding.ItemVersionBinding
import com.diary.app.utils.RichTextHelper
import java.text.SimpleDateFormat
import java.util.*

class VersionAdapter(
    private val onVersionClick: (Note) -> Unit,
    private val onVersionLongClick: (Note) -> Boolean = { false },
    private val onSelectionChanged: (Int) -> Unit = {},
    private val onMultiSelectExited: () -> Unit = {}
) : ListAdapter<Note, VersionAdapter.VersionViewHolder>(VersionDiffCallback()) {

    private val selectedIds = mutableSetOf<Long>()
    var isMultiSelectMode = false
        private set

    fun enterMultiSelectMode(note: Note) {
        if (note.isLatest) return // cannot select current version
        isMultiSelectMode = true
        selectedIds.add(note.id)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun toggleSelection(note: Note) {
        if (note.isLatest) return // cannot select current version
        if (selectedIds.contains(note.id)) {
            selectedIds.remove(note.id)
        } else {
            selectedIds.add(note.id)
        }
        notifyItemChanged(currentList.indexOf(note))
        onSelectionChanged(selectedIds.size)

        if (selectedIds.isEmpty()) {
            isMultiSelectMode = false
            notifyDataSetChanged()
            onMultiSelectExited()
        }
    }

    fun selectAllExceptCurrent() {
        selectedIds.clear()
        selectedIds.addAll(currentList.filter { !it.isLatest }.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun getSelectedIds(): List<Long> = selectedIds.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionViewHolder {
        val binding = ItemVersionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VersionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VersionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VersionViewHolder(private val binding: ItemVersionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

        fun bind(note: Note) {
            binding.tvVersionNumber.text = "Version ${note.version}"

            // Always show edited time
            binding.tvVersionLabel.text = dateFormat.format(Date(note.editedAt))

            // Current badge for latest version
            binding.chipCurrent.visibility = if (note.isLatest) View.VISIBLE else View.GONE

            // Preview
            binding.tvPreview.text = RichTextHelper.loadForPreview(note.content, 100)

            // Multi-select checkbox
            if (isMultiSelectMode) {
                binding.checkboxSelect.visibility = View.VISIBLE
                if (note.isLatest) {
                    // Cannot select current version — hide checkbox
                    binding.checkboxSelect.visibility = View.INVISIBLE
                    binding.checkboxSelect.isChecked = false
                } else {
                    binding.checkboxSelect.isChecked = selectedIds.contains(note.id)
                }
            } else {
                binding.checkboxSelect.visibility = View.GONE
            }

            // Click handlers
            binding.root.setOnClickListener {
                if (isMultiSelectMode) {
                    toggleSelection(note)
                } else {
                    onVersionClick(note)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isMultiSelectMode && !note.isLatest) {
                    onVersionLongClick(note)
                } else {
                    false
                }
            }
        }
    }

    class VersionDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
    }
}
