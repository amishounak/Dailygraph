package com.diary.app.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.diary.app.R
import com.diary.app.databinding.ActivityVersionHistoryBinding
import com.diary.app.utils.IOSStyleDialog
import com.diary.app.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

class VersionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVersionHistoryBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var adapter: VersionAdapter
    private var noteGroupId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVersionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit History"

        noteGroupId = intent.getStringExtra("noteGroupId") ?: run {
            finish()
            return
        }

        setupRecyclerView()
        loadVersions(noteGroupId)
    }

    private fun setupRecyclerView() {
        adapter = VersionAdapter(
            onVersionClick = { version ->
                val intent = android.content.Intent(this, NoteDetailActivity::class.java)
                intent.putExtra(NoteDetailActivity.EXTRA_NOTE_ID, version.id)
                intent.putExtra("READ_ONLY", true)
                startActivity(intent)
            },
            onVersionLongClick = { version ->
                enterMultiSelectMode(version)
                true
            },
            onSelectionChanged = { count ->
                if (adapter.isMultiSelectMode) {
                    binding.toolbarMultiSelect.title = "$count selected"
                }
            },
            onMultiSelectExited = {
                exitMultiSelectMode()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadVersions(groupId: String) {
        lifecycleScope.launch {
            val versions = viewModel.getNoteVersions(groupId)
            adapter.submitList(versions)
        }
    }

    private fun enterMultiSelectMode(version: com.diary.app.data.Note) {
        adapter.enterMultiSelectMode(version)
        binding.toolbar.visibility = View.GONE
        binding.toolbarMultiSelect.visibility = View.VISIBLE

        binding.toolbarMultiSelect.menu.clear()
        binding.toolbarMultiSelect.setNavigationOnClickListener { exitMultiSelectMode() }
        binding.toolbarMultiSelect.inflateMenu(R.menu.menu_multi_select)
        binding.toolbarMultiSelect.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_selected -> {
                    deleteSelectedVersions()
                    true
                }
                R.id.action_select_all -> {
                    adapter.selectAllExceptCurrent()
                    true
                }
                else -> false
            }
        }
    }

    private fun exitMultiSelectMode() {
        adapter.exitMultiSelectMode()
        binding.toolbarMultiSelect.visibility = View.GONE
        binding.toolbar.visibility = View.VISIBLE
        binding.toolbarMultiSelect.menu.clear()
        binding.toolbarMultiSelect.setOnMenuItemClickListener(null)
    }

    private fun deleteSelectedVersions() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) return

        val label = if (ids.size == 1) "1 version" else "${ids.size} versions"
        IOSStyleDialog.showConfirmDialog(this, "Delete $label?",
            "This will permanently delete the selected edit history.",
            "Delete", "Cancel") {
            viewModel.deleteVersionsByIds(ids) {
                runOnUiThread {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    exitMultiSelectMode()
                    loadVersions(noteGroupId)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (adapter.isMultiSelectMode) exitMultiSelectMode() else super.onBackPressed()
    }
}
