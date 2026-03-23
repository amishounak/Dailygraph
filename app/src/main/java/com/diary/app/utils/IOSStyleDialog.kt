package com.diary.app.utils

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import com.diary.app.R

object IOSStyleDialog {

    fun showActionSheet(
        context: Context,
        title: String? = null,
        message: String? = null,
        actions: List<DialogAction>
    ) {
        val dialog = AlertDialog.Builder(context, R.style.IOSDialogTheme)
        
        if (title != null) dialog.setTitle(title)
        if (message != null) dialog.setMessage(message)

        val items = actions.map { it.title }.toTypedArray()
        dialog.setItems(items) { dialogInterface, which ->
            actions[which].action()
            dialogInterface.dismiss()
        }

        val alertDialog = dialog.create()
        alertDialog.show()
    }

    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        positiveText: String = "Confirm",
        negativeText: String = "Cancel",
        onConfirm: () -> Unit
    ) {
        val builder = AlertDialog.Builder(context, R.style.IOSDialogTheme)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(positiveText) { dialog, _ ->
            onConfirm()
            dialog.dismiss()
        }
        builder.setNegativeButton(negativeText) { dialog, _ ->
            dialog.dismiss()
        }
        
        val alertDialog = builder.create()
        alertDialog.show()
        
        // Ensure buttons are visible by explicitly setting their text color
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            context.getColor(R.color.accent)
        )
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            context.getColor(R.color.accent)
        )
    }
}

data class DialogAction(
    val title: String,
    val isDestructive: Boolean = false,
    val action: () -> Unit
)
