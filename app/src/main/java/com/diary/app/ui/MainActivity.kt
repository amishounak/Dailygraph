package com.diary.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.diary.app.R
import com.diary.app.data.Profile
import com.diary.app.databinding.ActivityMainBinding
import com.diary.app.utils.IOSStyleDialog
import com.diary.app.utils.ImportExportHelper
import com.diary.app.utils.ThemeHelper
import com.diary.app.viewmodel.NoteViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var adapter: NoteAdapter
    private var profiles: List<Profile> = emptyList()
    private var shouldScrollToTop = false
    private var currentDateMillis: Long? = null

    // Activity launchers
    private val newNoteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Set flag to scroll after list updates
            shouldScrollToTop = true
        }
    }
    
    // File pickers
    private val exportFilePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { handleExport(it) }
    }
    private val importFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use toolbar but don't set title (spinner will be the title)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.title = ""
        
        setupProfileSpinner()
        setupRecyclerView()
        setupSearch()
        setupCalendarFilter()
        setupFab()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        // Clear search focus when returning to this activity.
        // Without this, the EditText retains focus from the previous session
        // and the keyboard auto-appears when the app is reopened.
        binding.etSearch.clearFocus()
        binding.recyclerView.requestFocus()
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun setupProfileSpinner() {
        // Observe profiles list
        viewModel.allProfiles.observe(this) { profilesList ->
            profiles = profilesList
            val profileNames = profilesList.map { it.name }
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profileNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerProfile.adapter = spinnerAdapter
            
            // Update selection based on current profile
            viewModel.currentProfile.value?.let { currentProfile ->
                val index = profilesList.indexOfFirst { it.id == currentProfile.id }
                if (index >= 0) {
                    binding.spinnerProfile.setSelection(index, false)
                }
            }
        }
        
        // Observe current profile separately (only once)
        viewModel.currentProfile.observe(this) { currentProfile ->
            if (profiles.isNotEmpty()) {
                val index = profiles.indexOfFirst { it.id == currentProfile.id }
                if (index >= 0 && binding.spinnerProfile.selectedItemPosition != index) {
                    binding.spinnerProfile.setSelection(index, false)
                }
            }
        }

        binding.spinnerProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (profiles.isNotEmpty() && position < profiles.size) {
                    val selectedProfile = profiles[position]
                    if (selectedProfile.id != viewModel.currentProfile.value?.id) {
                        viewModel.switchProfile(selectedProfile)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        adapter = NoteAdapter(
            onNoteClick = { note ->
                val intent = Intent(this, NoteDetailActivity::class.java)
                intent.putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.id)
                startActivity(intent)
            },
            onNoteLongClick = { note ->
                enterMultiSelectMode(note)
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

    private fun setupSearch() {
        binding.etSearch.clearFocus()
        binding.recyclerView.requestFocus()

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })

        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            viewModel.clearFilters()
            binding.etSearch.clearFocus()
        }
    }

    private fun setupCalendarFilter() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())

        viewModel.selectedDate.observe(this) { date ->
            if (date != null) {
                binding.btnCalendar.setColorFilter(getColor(R.color.accent))
                binding.dateNavBar.visibility = View.VISIBLE
                binding.tvDateLabel.text = date
            } else {
                binding.btnCalendar.setColorFilter(getColor(R.color.text_secondary))
                binding.dateNavBar.visibility = View.GONE
                currentDateMillis = null
            }
        }

        binding.btnCalendar.setOnClickListener {
            val initialSelection = currentDateMillis ?: MaterialDatePicker.todayInUtcMilliseconds()

            // Block future dates, start week on Sunday
            val constraintsBuilder = com.google.android.material.datepicker.CalendarConstraints.Builder()
                .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
                .setValidator(com.google.android.material.datepicker.DateValidatorPointBackward.now())
                .setFirstDayOfWeek(java.util.Calendar.SUNDAY)

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Filter by date")
                .setSelection(initialSelection)
                .setCalendarConstraints(constraintsBuilder.build())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                currentDateMillis = selection
                val selectedDate = dateFormat.format(Date(selection))
                viewModel.setDateFilter(selectedDate)
            }

            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }

        binding.btnDatePrev.setOnClickListener {
            navigateDate(-1, dateFormat)
        }

        binding.btnDateNext.setOnClickListener {
            navigateDate(1, dateFormat)
        }
    }

    private fun navigateDate(dayOffset: Int, dateFormat: SimpleDateFormat) {
        val millis = currentDateMillis ?: return
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = millis
            add(java.util.Calendar.DAY_OF_MONTH, dayOffset)
        }
        // Block navigating to future dates
        val todayEnd = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
        }
        if (calendar.after(todayEnd)) return

        currentDateMillis = calendar.timeInMillis
        val newDate = dateFormat.format(Date(calendar.timeInMillis))
        viewModel.setDateFilter(newDate)
    }

    private fun setupFab() {
        binding.fabNewNote.setOnClickListener {
            newNoteLauncher.launch(Intent(this, NoteDetailActivity::class.java))
        }
    }

    private fun observeData() {
        viewModel.displayedNotes.observe(this) { notes ->
            adapter.submitNotes(notes)
            // Scroll to top after list updates
            if (shouldScrollToTop && notes.isNotEmpty()) {
                binding.recyclerView.post {
                    binding.recyclerView.scrollToPosition(0)
                }
                shouldScrollToTop = false
            }
            binding.tvEmpty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun enterMultiSelectMode(note: com.diary.app.data.Note) {
        adapter.enterMultiSelectMode(note)
        binding.toolbar.visibility = View.GONE
        binding.toolbarMultiSelect.visibility = View.VISIBLE
        
        // Clear menu first to avoid duplicates
        binding.toolbarMultiSelect.menu.clear()
        
        binding.toolbarMultiSelect.setNavigationOnClickListener { exitMultiSelectMode() }
        binding.toolbarMultiSelect.inflateMenu(R.menu.menu_multi_select)
        binding.toolbarMultiSelect.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_delete_selected -> {
                    deleteSelectedNotes()
                    true
                }
                R.id.action_select_all -> {
                    adapter.selectAll()
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

    private fun deleteSelectedNotes() {
        val selected = adapter.getSelectedNotes()
        IOSStyleDialog.showConfirmDialog(this, "Delete ${selected.size} entries?",
            "This will permanently delete ${selected.size} entries.",
            "Delete", "Cancel") {
            viewModel.deleteMultipleNotes(selected)
            exitMultiSelectMode()
            Toast.makeText(this, "${selected.size} deleted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export -> { exportNotes(); true }
            R.id.action_import -> { importFilePicker.launch(arrayOf("text/plain", "*/*")); true }
            R.id.action_manage_profiles -> { showProfileManager(); true }
            R.id.action_appearance -> { showAppearanceDialog(); true }
            R.id.action_about -> { startActivity(Intent(this, AboutActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportNotes() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        exportFilePicker.launch("Dailygraph_Export_$timestamp.txt")
    }

    private fun handleExport(uri: Uri) {
        lifecycleScope.launch {
            // Get ALL notes from ALL profiles (including all versions)
            val allNotes = withContext(Dispatchers.IO) { viewModel.getAllNotesForExport() }
            val allProfiles = withContext(Dispatchers.IO) { viewModel.getAllProfilesForExport() }
            
            val json = ImportExportHelper.exportAllNotesToJson(allNotes, allProfiles)
            val success = withContext(Dispatchers.IO) { ImportExportHelper.writeToUri(this@MainActivity, uri, json) }
            
            Toast.makeText(
                this@MainActivity, 
                if (success) "✓ Exported ${allProfiles.size} journals, ${allNotes.size} entries (all versions)" 
                else "Export failed", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleImport(uri: Uri) {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) { ImportExportHelper.readFromUri(this@MainActivity, uri) } ?: return@launch
            
            // Try parsing as v3 format (all profiles)
            val fullImport = ImportExportHelper.parseAllNotesFromJson(json)
            
            if (fullImport != null) {
                // V3 format - all profiles and notes
                AlertDialog.Builder(this@MainActivity, R.style.IOSDialogTheme)
                    .setTitle("Import Complete Data")
                    .setMessage("Import ${fullImport.profiles.size} journals with ${fullImport.notes.size} entries (including all versions)?")
                    .setPositiveButton("Import All") { _, _ ->
                        viewModel.importFullData(fullImport) { imported, duplicates ->
                            val msg = if (duplicates > 0) {
                                "✓ Imported $imported entries ($duplicates duplicates skipped)"
                            } else {
                                "✓ Imported $imported entries"
                            }
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                // Old format - single profile
                val profile = viewModel.currentProfile.value ?: return@launch
                val notes = ImportExportHelper.parseNotesFromJson(json, profile.id) ?: return@launch

                AlertDialog.Builder(this@MainActivity, R.style.IOSDialogTheme)
                    .setTitle("Import ${notes.size} notes")
                    .setMessage("Replace all or merge?")
                    .setPositiveButton("Replace") { _, _ ->
                        viewModel.importNotes(notes, true) { imp, dup ->
                            val msg = if (dup > 0) "✓ Imported $imp ($dup duplicates)" else "✓ Imported $imp"
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNeutralButton("Merge") { _, _ ->
                        viewModel.importNotes(notes, false) { imp, dup ->
                            val msg = if (dup > 0) "✓ Merged $imp ($dup duplicates)" else "✓ Merged $imp"
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showAppearanceDialog() {
        val options = arrayOf("Light", "Dark", "Auto (System)")
        val currentMode = ThemeHelper.getSavedThemeMode(this)

        AlertDialog.Builder(this, R.style.IOSDialogTheme)
            .setTitle("Appearance")
            .setSingleChoiceItems(options, currentMode) { dialog, which ->
                ThemeHelper.saveThemeMode(this, which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProfileManager() {
        startActivity(android.content.Intent(this, ProfileManagerActivity::class.java))
    }

    override fun onBackPressed() {
        if (adapter.isMultiSelectMode) exitMultiSelectMode() else super.onBackPressed()
    }
}
