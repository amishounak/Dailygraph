package com.diary.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diary.app.data.Profile
import com.diary.app.databinding.ItemProfileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProfileItem(
    val profile: Profile,
    val entryCount: Int
)

class ProfileAdapter(
    private val onRename: (Profile) -> Unit,
    private val onDelete: (Profile) -> Unit,
    private val onSetDefault: (Profile) -> Unit = {}
) : ListAdapter<ProfileItem, ProfileAdapter.ProfileViewHolder>(ProfileDiffCallback()) {

    inner class ProfileViewHolder(
        private val binding: ItemProfileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var lastClickTime = 0L

        fun bind(item: ProfileItem) {
            binding.tvProfileName.text = item.profile.name

            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val createdDate = dateFormat.format(Date(item.profile.createdAt))
            val entriesLabel = if (item.entryCount == 1) "entry" else "entries"
            binding.tvProfileMeta.text = "${item.entryCount} $entriesLabel\nCreated $createdDate"

            // Show Default badge based on isDefault flag in database
            binding.chipCurrent.visibility = if (item.profile.isDefault) View.VISIBLE else View.GONE

            binding.btnRename.setOnClickListener { onRename(item.profile) }
            binding.btnDelete.setOnClickListener { onDelete(item.profile) }

            // Double-tap card to set as default (simple timing-based detection)
            binding.root.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 400) {
                    // Double-tap detected
                    if (!item.profile.isDefault) {
                        onSetDefault(item.profile)
                    }
                    lastClickTime = 0
                } else {
                    lastClickTime = now
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val binding = ItemProfileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProfileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProfileDiffCallback : DiffUtil.ItemCallback<ProfileItem>() {
        override fun areItemsTheSame(oldItem: ProfileItem, newItem: ProfileItem) =
            oldItem.profile.id == newItem.profile.id
        override fun areContentsTheSame(oldItem: ProfileItem, newItem: ProfileItem) =
            oldItem == newItem
    }
}
