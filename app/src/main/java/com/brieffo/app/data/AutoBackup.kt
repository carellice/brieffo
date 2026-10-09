package com.brieffo.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Backup automatico della configurazione in una cartella scelta dall'utente (anche di un servizio cloud
 * che compare tra i file del telefono): ogni volta che le impostazioni cambiano, il file viene riscritto.
 */
object AutoBackup {
    private const val FILE = "brieffo-backup.json"

    /** Tiene il permesso sulla cartella anche dopo il riavvio e la ricorda come destinazione dei backup. */
    fun choose(ctx: Context, folder: Uri) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val prefs = Prefs(ctx)
        prefs.backupFolder = folder.toString()
        prefs.backupHash = 0
    }

    fun disable(ctx: Context) {
        val prefs = Prefs(ctx)
        runCatching {
            ctx.contentResolver.releasePersistableUriPermission(Uri.parse(prefs.backupFolder), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs.backupFolder = ""
    }

    /** Nome della cartella scelta, da mostrare nelle impostazioni. */
    fun folderName(ctx: Context): String? {
        val folder = Prefs(ctx).backupFolder.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return null
        return runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "cartella scelta"
    }

    /**
     * Scrive il backup se la configurazione è cambiata dall'ultima volta (o sempre, con [force]).
     * Restituisce falso se la cartella non è più raggiungibile. Va chiamata fuori dal thread principale.
     */
    fun write(ctx: Context, force: Boolean = false): Boolean {
        val prefs = Prefs(ctx)
        val folder = prefs.backupFolder.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return false
        val content = prefs.export()
        if (!force && content.hashCode() == prefs.backupHash) return true
        return runCatching {
            val resolver = ctx.contentResolver
            val root = DocumentsContract.getTreeDocumentId(folder)
            // Se il file c'è già lo si riscrive, altrimenti lo si crea: nella cartella ne resta sempre uno solo.
            var file: Uri? = null
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(folder, root),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
            )?.use { c ->
                while (c.moveToNext()) if (c.getString(1) == FILE) file = DocumentsContract.buildDocumentUriUsingTree(folder, c.getString(0))
            }
            val target = file ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, root), "application/json", FILE)
                ?: error("file non creato")
            resolver.openOutputStream(target, "wt")!!.use { it.write(content.toByteArray()) }
            prefs.backupHash = content.hashCode()
            prefs.backupAt = System.currentTimeMillis()
        }.isSuccess
    }
}
