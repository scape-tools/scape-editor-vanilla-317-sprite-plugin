package plugin

import scape.editor.gui.Settings

class ImageArchiveModel(val hash: Int, val sprites: MutableList<SpriteModel>) {

    override fun toString(): String {
        return Settings.getNameFromHash(hash) ?: hash.toString()
    }

}