package com.diary.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.diary.app.R
import com.diary.app.data.Note
import com.diary.app.databinding.ActivityNoteDetailBinding
import com.diary.app.utils.LocationWeatherHelper
import com.diary.app.utils.RichTextHelper
import com.diary.app.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Uses a SINGLE AztecText for both view and edit mode.
 * - View mode: AztecText renders HTML perfectly (bullets, headings, etc.)
 *   with keyboard blocked. Text is selectable via long-press (native EditText).
 * - Edit mode: AztecText is fully editable with Aztec's toolbar (all features).
 *
 * This architecture eliminates the recurring rendering mismatch bug permanently.
 */
class NoteDetailActivity : AppCompatActivity(), IAztecToolbarClickListener {

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
    }

    private lateinit var binding: ActivityNoteDetailBinding
    private val viewModel: NoteViewModel by viewModels()
    private var currentNote: Note? = null
    private var isEditMode = false
    private var isReadOnly = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) fetchLocationAndWeather()
        else finalizeHeaderWithoutLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize Aztec with its own toolbar (all features: B/I/U/S, lists, heading, quote, link, HTML)
        Aztec.with(binding.aztecEditor, binding.aztecSource, binding.formattingToolbar, this)

        // Explicit colors for light/dark mode
        binding.aztecEditor.setTextColor(getColor(R.color.text_primary))
        binding.aztecEditor.setHintTextColor(getColor(R.color.text_tertiary))

        // Style the Aztec toolbar for dark mode support
        styleAztecToolbar()

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        isReadOnly = intent.getBooleanExtra("READ_ONLY", false)

        if (noteId != -1L) loadExistingNote(noteId)
        else setupNewNote()
    }

    // ── Toolbar styling: tint icons, hide media button, dark mode ──

    private fun styleAztecToolbar() {
        val toolbar = binding.formattingToolbar
        val iconTint = getColor(R.color.toolbar_icon)
        val surfaceColor = getColor(R.color.surface)

        toolbar.post {
            try {
                // Force background AFTER Aztec overrides XML attributes
                toolbar.setBackgroundColor(surfaceColor)
                tintAndCleanToolbar(toolbar, iconTint)
            } catch (_: Exception) {}
        }
    }

    private fun tintAndCleanToolbar(group: ViewGroup, tintColor: Int) {
        val surfaceColor = getColor(R.color.surface)

        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            val desc = child.contentDescription?.toString()?.lowercase() ?: ""

            // Hide media/image/add/html buttons
            if (desc.contains("media") || desc.contains("photo") ||
                desc.contains("image") || desc.contains("add") ||
                desc.contains("html")) {
                child.visibility = View.GONE
                continue
            }

            // Tint icons for dark mode — do NOT touch backgrounds or padding
            if (child is ImageView) {
                child.setColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN)
            }

            // Force surface color on any internal container backgrounds
            if (child is ViewGroup) {
                child.setBackgroundColor(surfaceColor)
                tintAndCleanToolbar(child, tintColor)
            }
        }
    }

    // ── IAztecToolbarClickListener (Aztec handles everything internally) ──

    override fun onToolbarCollapseButtonClicked() {}
    override fun onToolbarExpandButtonClicked() {}
    override fun onToolbarFormatButtonClicked(format: ITextFormat, isKeyboardShortcut: Boolean) {}
    override fun onToolbarHeadingButtonClicked() {}
    override fun onToolbarHtmlButtonClicked() {}
    override fun onToolbarListButtonClicked() {}
    override fun onToolbarMediaButtonClicked(): Boolean = false

    // ══════════════════════════════════════════════════════════════
    //  NOTE LOADING
    // ══════════════════════════════════════════════════════════════

    private fun loadExistingNote(noteId: Long) {
        supportActionBar?.title = "Entry"
        binding.btnUpdate.visibility = View.VISIBLE
        binding.btnSave.visibility = View.GONE
        binding.loadingGroup.visibility = View.GONE

        lifecycleScope.launch {
            val note = viewModel.getNoteById(noteId)
            if (note != null) { currentNote = note; displayNote(note) }
            else { Toast.makeText(this@NoteDetailActivity, "Note not found", Toast.LENGTH_SHORT).show(); finish() }
        }
    }

    private fun displayNote(note: Note) {
        binding.tvHeaderDate.text = note.date
        binding.tvHeaderTime.text = note.time
        binding.tvHeaderTemp.text = "\uD83C\uDF21 ${note.temperature}"
        binding.tvHeaderLocation.text = "\uD83D\uDCCD ${note.location}"

        // Load and show in view mode
        loadContentIntoAztec(note.content)
        setupViewMode()

        if (note.version > 1) {
            binding.tvVersionIndicator.visibility = View.VISIBLE
            binding.tvVersionIndicator.text = "Version ${note.version}"
        }

        if (isReadOnly) {
            binding.btnUpdate.visibility = View.GONE
            binding.layoutEditButtons.visibility = View.GONE
            binding.btnSave.visibility = View.GONE
            supportActionBar?.title = "Version ${note.version}"
        } else {
            binding.btnUpdate.visibility = View.VISIBLE
            binding.layoutEditButtons.visibility = View.GONE
            binding.btnUpdate.setOnClickListener { enterEditMode() }
            binding.btnCancelEdit.setOnClickListener { exitEditMode() }
            binding.btnSaveUpdate.setOnClickListener { saveUpdate() }
        }
    }

    private fun loadContentIntoAztec(content: String) {
        try {
            if (content.isEmpty()) { binding.aztecEditor.fromHtml(""); return }
            val cleaned = RichTextHelper.cleanContent(content)
            if (RichTextHelper.isHtml(cleaned)) binding.aztecEditor.fromHtml(cleaned)
            else binding.aztecEditor.fromHtml(cleaned.replace("\n", "<br>"))
        } catch (_: Exception) {
            binding.aztecEditor.fromHtml("")
            Toast.makeText(this, "Error loading content", Toast.LENGTH_SHORT).show()
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  VIEW MODE — AztecText renders its own HTML perfectly.
    //  Keyboard is blocked. Text is selectable via native long-press.
    // ══════════════════════════════════════════════════════════════

    private fun setupViewMode() {
        // Block keyboard but keep view enabled (text selectable via long-press)
        binding.aztecEditor.showSoftInputOnFocus = false
        binding.aztecEditor.isCursorVisible = false
        binding.aztecEditor.clearFocus()

        // Block Cut/Paste — only allow Copy and Select All in view mode
        val readOnlyCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                // Remove cut and paste items
                menu.removeItem(android.R.id.cut)
                menu.removeItem(android.R.id.paste)
                menu.removeItem(android.R.id.pasteAsPlainText)
                return true
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                menu.removeItem(android.R.id.cut)
                menu.removeItem(android.R.id.paste)
                menu.removeItem(android.R.id.pasteAsPlainText)
                return true
            }
            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode) {}
        }
        binding.aztecEditor.customSelectionActionModeCallback = readOnlyCallback
        binding.aztecEditor.customInsertionActionModeCallback = readOnlyCallback

        // Hide toolbar
        binding.toolbarContainer.visibility = View.GONE

        // Dismiss keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.aztecEditor.windowToken, 0)

        // Safety: if keyboard somehow opens on touch, hide it
        binding.aztecEditor.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus && !isEditMode) {
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  EDIT MODE — full Aztec WYSIWYG with its toolbar
    // ══════════════════════════════════════════════════════════════

    private fun enterEditMode() {
        isEditMode = true
        val content = currentNote?.content ?: ""

        // Remove view-mode safety listener and read-only action mode
        binding.aztecEditor.onFocusChangeListener = null
        binding.aztecEditor.customSelectionActionModeCallback = null
        binding.aztecEditor.customInsertionActionModeCallback = null

        // Reload content (clean state)
        loadContentIntoAztec(content)

        // Enable editing
        binding.aztecEditor.showSoftInputOnFocus = true
        binding.aztecEditor.isCursorVisible = true
        binding.aztecEditor.requestFocus()
        binding.aztecEditor.setSelection(binding.aztecEditor.length())

        // Show toolbar
        binding.toolbarContainer.visibility = View.VISIBLE

        // Show keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        binding.aztecEditor.postDelayed({
            imm.showSoftInput(binding.aztecEditor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 150)

        binding.btnUpdate.visibility = View.GONE
        binding.layoutEditButtons.visibility = View.VISIBLE
        supportActionBar?.title = "Editing"
    }

    private fun exitEditMode() {
        isEditMode = false
        binding.aztecEditor.clearFocus()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.aztecEditor.windowToken, 0)

        // Reload original content and switch to view mode
        loadContentIntoAztec(currentNote?.content ?: "")
        setupViewMode()

        binding.btnUpdate.visibility = View.VISIBLE
        binding.layoutEditButtons.visibility = View.GONE
        supportActionBar?.title = "Entry"
    }

    private fun saveUpdate() {
        val newContent = binding.aztecEditor.toHtml()
        val note = currentNote ?: return
        if (newContent.isBlank() || binding.aztecEditor.text.isNullOrBlank()) {
            Toast.makeText(this, "Content cannot be empty", Toast.LENGTH_SHORT).show(); return
        }
        if (newContent == note.content) {
            Toast.makeText(this, "No changes made", Toast.LENGTH_SHORT).show(); return
        }
        viewModel.updateNoteContent(note, newContent) {
            runOnUiThread {
                Toast.makeText(this, "\u2713 Update saved as version ${note.version + 1}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  NEW NOTE
    // ══════════════════════════════════════════════════════════════

    private fun setupNewNote() {
        isEditMode = true
        supportActionBar?.title = "New Entry"
        binding.loadingGroup.visibility = View.VISIBLE
        binding.btnSave.visibility = View.VISIBLE
        binding.btnUpdate.visibility = View.GONE

        binding.aztecEditor.showSoftInputOnFocus = true
        binding.aztecEditor.isCursorVisible = true
        binding.aztecEditor.requestFocus()
        binding.toolbarContainer.visibility = View.VISIBLE

        binding.btnSave.isEnabled = false
        binding.btnSave.alpha = 0.5f
        binding.btnSave.setOnClickListener { saveNote() }

        viewModel.currentProfile.observe(this) { profile ->
            if (profile != null && binding.loadingGroup.visibility == View.VISIBLE)
                requestLocationPermissionAndFetch()
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  LOCATION / WEATHER
    // ══════════════════════════════════════════════════════════════

    private fun requestLocationPermissionAndFetch() {
        val fg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val cg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fg || cg) fetchLocationAndWeather()
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun fetchLocationAndWeather() {
        lifecycleScope.launch {
            try {
                val info = LocationWeatherHelper.getLocationAndWeather(this@NoteDetailActivity)
                populateHeader(info.location, info.temperature)
            } catch (_: Exception) { finalizeHeaderWithoutLocation() }
        }
    }

    private fun finalizeHeaderWithoutLocation() = populateHeader("Location N/A", "Temp N/A")

    private fun populateHeader(location: String, temperature: String) {
        val now = Date()
        val date = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now)
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        binding.tvHeaderDate.text = date
        binding.tvHeaderTime.text = time
        binding.tvHeaderTemp.text = "\uD83C\uDF21 $temperature"
        binding.tvHeaderLocation.text = "\uD83D\uDCCD $location"
        binding.loadingGroup.visibility = View.GONE
        binding.btnSave.isEnabled = true
        binding.btnSave.alpha = 1.0f

        val profile = viewModel.currentProfile.value
        if (profile == null) { Toast.makeText(this, "Error: No journal loaded", Toast.LENGTH_SHORT).show(); finish(); return }
        currentNote = Note(profileId = profile.id, noteGroupId = viewModel.generateNoteGroupId(),
            date = date, time = time, temperature = temperature, location = location, content = "")
    }

    private fun saveNote() {
        val content = binding.aztecEditor.toHtml()
        if (content.isBlank() || binding.aztecEditor.text.isNullOrBlank()) {
            Toast.makeText(this, "Please write something", Toast.LENGTH_SHORT).show(); return
        }
        val note = currentNote?.copy(content = content) ?: return
        viewModel.insertNote(note) {
            runOnUiThread {
                Toast.makeText(this, "Entry saved \u2713", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK); finish()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  MENU
    // ══════════════════════════════════════════════════════════════

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (intent.getLongExtra(EXTRA_NOTE_ID, -1L) != -1L && !isReadOnly)
            menuInflater.inflate(R.menu.menu_note_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
        R.id.action_delete_note -> { confirmDelete(); true }
        R.id.action_version_history -> { showVersionHistory(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showVersionHistory() {
        val note = currentNote ?: return
        startActivity(Intent(this, VersionHistoryActivity::class.java).apply { putExtra("noteGroupId", note.noteGroupId) })
    }

    private fun confirmDelete() {
        val note = currentNote ?: return
        AlertDialog.Builder(this, R.style.IOSDialogTheme)
            .setTitle("Delete entry?")
            .setMessage("This will permanently delete this entry.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteNote(note); Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show(); finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
