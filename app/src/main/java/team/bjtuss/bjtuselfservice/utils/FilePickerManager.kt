package team.bjtuss.bjtuselfservice.utils

import android.app.Activity
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import team.bjtuss.bjtuselfservice.MainApplication
import java.lang.ref.WeakReference

class FilePickerManager {
    private var launcher: ActivityResultLauncher<String>? = null
    // Store selected files
    private val selectedFiles = mutableListOf<Uri>()

    fun init(launcher: ActivityResultLauncher<String>) {
        this.launcher = launcher
    }

    fun pickFile(mimeType: String = "*/*") {
        launcher?.launch(mimeType)
    }

    fun handleResult(uri: Uri) {
        selectedFiles.add(uri)
        println("Selected files: $selectedFiles")
    }

    fun getSelectedFiles(): List<Uri> {
        return selectedFiles
    }

    fun clearSelectedFiles() {
        selectedFiles.clear()
    }
}