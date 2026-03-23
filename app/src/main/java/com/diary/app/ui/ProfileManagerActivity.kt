package com.diary.app.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.diary.app.R
import com.diary.app.data.Profile
import com.diary.app.databinding.ActivityProfileManagerBinding
import com.diary.app.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

class ProfileManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileManagerBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Journals"

        setupRecyclerView()
        setupFab()
        observeProfiles()
    }

    private fun setupRecyclerView() {
        adapter = ProfileAdapter(
            onRename = { profile -> showRenameDialog(profile) },
            onDelete = { profile -> confirmDeleteProfile(profile) },
            onSetDefault = { profile -> setAsDefault(profile) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddProfile.setOnClickListener {
            showAddProfileDialog()
        }
    }

    private fun observeProfiles() {
        viewModel.allProfiles.observe(this) { profiles ->
            if (profiles.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                buildProfileItems(profiles)
            }
        }
    }

    private fun buildProfileItems(profiles: List<Profile>) {
        lifecycleScope.launch {
            val items = profiles.map { profile ->
                val count = viewModel.getEntryCountForProfile(profile.id)
                ProfileItem(profile = profile, entryCount = count)
            }
            adapter.submitList(items)
        }
    }

    private fun showAddProfileDialog() {
        val input = EditText(this).apply {
            hint = "Journal name"
            setPadding(64, 40, 64, 24)
        }
        val container = FrameLayout(this).apply {
            addView(input)
        }

        AlertDialog.Builder(this, R.style.IOSDialogTheme)
            .setTitle("Create Journal")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    viewModel.createProfile(name) {
                        runOnUiThread {
                            Toast.makeText(this, "Journal created", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    input.requestFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    input.postDelayed({ imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }, 150)
                }
                dialog.show()
            }
    }

    private fun showRenameDialog(profile: Profile) {
        val input = EditText(this).apply {
            hint = "New name"
            setText(profile.name)
            setPadding(64, 40, 64, 24)
            selectAll()
        }
        val container = FrameLayout(this).apply {
            addView(input)
        }

        AlertDialog.Builder(this, R.style.IOSDialogTheme)
            .setTitle("Rename Journal")
            .setView(container)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    viewModel.renameProfile(profile, newName) {
                        runOnUiThread {
                            Toast.makeText(this, "Journal renamed", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    input.requestFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    input.postDelayed({ imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }, 150)
                }
                dialog.show()
            }
    }

    private fun confirmDeleteProfile(profile: Profile) {
        val profiles = viewModel.allProfiles.value ?: emptyList()
        if (profiles.size <= 1) {
            Toast.makeText(this, "Cannot delete the last journal", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.IOSDialogTheme)
            .setTitle("Delete '${profile.name}'?")
            .setMessage("This will permanently delete this journal and all its entries.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteProfile(profile) {
                    runOnUiThread {
                        Toast.makeText(this, "Journal deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setAsDefault(profile: Profile) {
        viewModel.setDefaultProfile(profile) {
            runOnUiThread {
                Toast.makeText(this, "'${profile.name}' set as default", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
