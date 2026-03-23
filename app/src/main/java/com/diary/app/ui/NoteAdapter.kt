package com.diary.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.diary.app.R
import com.diary.app.data.Note
import com.diary.app.databinding.ItemNoteBinding
import com.diary.app.utils.RichTextHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ListItem {
    data class SectionHeader(val title: String) : ListItem()
    data class NoteEntry(val note: Note) : ListItem()
}

class NoteAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onNoteLongClick: (Note) -> Boolean,
    private val onSelectionChanged: (Int) -> Unit = {},
    private val onMultiSelectExited: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_NOTE = 1
    }

    private var items: List<ListItem> = emptyList()
    private val selectedNotes = mutableSetOf<Long>()
    var isMultiSelectMode = false
        private set

    fun submitNotes(notes: List<Note>) {
        val newItems = mutableListOf<ListItem>()
        val currentMonthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        var lastMonthYear = ""

        for (note in notes) {
            val monthYear = extractMonthYear(note.date)
            if (monthYear != lastMonthYear) {
                // Don't show header for current month
                if (monthYear != currentMonthYear) {
                    newItems.add(ListItem.SectionHeader(monthYear))
                }
                lastMonthYear = monthYear
            }
            newItems.add(ListItem.NoteEntry(note))
        }
        items = newItems
        notifyDataSetChanged()
    }

    private fun extractMonthYear(dateStr: String): String {
        // Date format: "Monday, March 5, 2026"
        return try {
            val parsed = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).parse(dateStr)
            if (parsed != null) SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(parsed) else ""
        } catch (_: Exception) {
            // Fallback: try splitting
            val parts = dateStr.split(", ")
            if (parts.size >= 3) {
                val month = parts[1].split(" ").firstOrNull() ?: ""
                "$month ${parts[2]}"
            } else ""
        }
    }

    // ── Multi-select ────────────────────────────────────────────

    fun enterMultiSelectMode(note: Note) {
        isMultiSelectMode = true
        selectedNotes.add(note.id)
        notifyDataSetChanged()
        onSelectionChanged(selectedNotes.size)
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedNotes.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun toggleSelection(note: Note) {
        if (selectedNotes.contains(note.id)) selectedNotes.remove(note.id)
        else selectedNotes.add(note.id)
        val pos = items.indexOfFirst { it is ListItem.NoteEntry && it.note.id == note.id }
        if (pos >= 0) notifyItemChanged(pos)
        onSelectionChanged(selectedNotes.size)
        if (selectedNotes.isEmpty()) {
            isMultiSelectMode = false
            notifyDataSetChanged()
            onMultiSelectExited()
        }
    }

    fun selectAll() {
        selectedNotes.clear()
        selectedNotes.addAll(items.filterIsInstance<ListItem.NoteEntry>().map { it.note.id })
        notifyDataSetChanged()
        onSelectionChanged(selectedNotes.size)
    }

    fun getSelectedNotes(): List<Note> =
        items.filterIsInstance<ListItem.NoteEntry>().filter { selectedNotes.contains(it.note.id) }.map { it.note }

    fun getSelectedCount(): Int = selectedNotes.size

    fun isEmpty(): Boolean = items.filterIsInstance<ListItem.NoteEntry>().isEmpty()

    // ── ViewHolder logic ────────────────────────────────────────

    override fun getItemCount(): Int = items.size
    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.SectionHeader -> TYPE_HEADER
        is ListItem.NoteEntry -> TYPE_NOTE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false)
            SectionViewHolder(view)
        } else {
            val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            NoteViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.SectionHeader -> (holder as SectionViewHolder).bind(item.title)
            is ListItem.NoteEntry -> (holder as NoteViewHolder).bind(item.note)
        }
    }

    inner class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvSectionTitle)
        fun bind(text: String) { title.text = text }
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            binding.tvDate.text = note.date
            binding.tvTime.text = note.time
            binding.tvPreview.text = RichTextHelper.loadForPreview(note.content, 100)

            binding.tvMeta.text = buildString {
                if (note.location.isNotBlank() && note.location != "Location N/A")
                    append("\uD83D\uDCCD ${note.location}")
                if (note.temperature.isNotBlank() && note.temperature != "Temp N/A") {
                    if (isNotEmpty()) append("  ")
                    append("\uD83C\uDF21 ${note.temperature}")
                }
            }

            if (isMultiSelectMode) {
                binding.checkboxSelect.visibility = View.VISIBLE
                binding.checkboxSelect.isChecked = selectedNotes.contains(note.id)
            } else {
                binding.checkboxSelect.visibility = View.GONE
            }

            if (note.version > 1) {
                binding.tvVersionBadge.visibility = View.VISIBLE
                binding.tvVersionBadge.text = "v${note.version}"
            } else {
                binding.tvVersionBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                if (isMultiSelectMode) toggleSelection(note) else onNoteClick(note)
            }
            binding.root.setOnLongClickListener {
                if (!isMultiSelectMode) onNoteLongClick(note) else false
            }
        }
    }
}
