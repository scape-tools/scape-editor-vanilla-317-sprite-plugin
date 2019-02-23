package plugin

import scape.editor.fs.RSArchive
import scape.editor.fs.RSFileStore
import scape.editor.gui.plugin.Plugin
import scape.editor.gui.plugin.extension.SpriteExtension

@Plugin(name = "Vanilla 317 Sprite Plugin")
class SpritePlugin : SpriteExtension() {

    override fun getStoreId(): Int {
        return RSFileStore.ARCHIVE_FILE_STORE
    }

    override fun getFileId(): Int {
        return RSArchive.MEDIA_ARCHIVE
    }

    override fun applicationIcon(): String {
        return "icons/icon.png"
    }

    override fun fxml(): String {
        return "scene.fxml"
    }

    override fun stylesheets(): Array<String> {
        return arrayOf("css/style.css")
    }
}
