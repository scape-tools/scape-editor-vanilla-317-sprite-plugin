package plugin

import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.scene.canvas.Canvas
import javafx.scene.image.ImageView
import javafx.scene.layout.VBox
import org.imgscalr.Scalr
import scape.editor.fs.graphics.RSImageArchive
import scape.editor.gui.model.SpriteModel
import java.io.File
import java.net.URL
import java.util.*
import javafx.collections.FXCollections
import scape.editor.gui.model.ImageArchiveModel
import javafx.scene.text.Text
import javafx.collections.transformation.FilteredList
import javafx.collections.transformation.SortedList
import javafx.event.ActionEvent
import javafx.scene.control.*
import javafx.stage.FileChooser
import scape.editor.fs.RSArchive
import scape.editor.fs.RSFileStore
import scape.editor.fs.graphics.RSSprite
import scape.editor.gui.App
import scape.editor.gui.Settings
import scape.editor.gui.controller.BaseController
import scape.editor.gui.model.SpriteEncodingType
import scape.editor.gui.util.toType
import scape.editor.gui.util.write24Int
import scape.editor.util.HashUtils
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import javax.imageio.ImageIO

class Controller : BaseController() {

    lateinit var archiveList: ListView<ImageArchiveModel>
    lateinit var spriteList: ListView<SpriteModel>
    lateinit var canvas: Canvas
    lateinit var canvasParent: VBox
    lateinit var searchTf : TextField
    lateinit var colorCountT: Text
    lateinit var spriteCountT: Text
    lateinit var widthT: Text
    lateinit var heightT: Text
    lateinit var resizeWidthT: Text
    lateinit var resizeHeightT: Text
    lateinit var pixelCountT: Text
    lateinit var colorsT: Text
    lateinit var encodingCb: ComboBox<SpriteEncodingType>
    lateinit var offsetXTf: TextField
    lateinit var offsetYTf: TextField

    var encodingOptions = FXCollections.observableArrayList(SpriteEncodingType.HORIZONTAL, SpriteEncodingType.VERTICAL)
    var archives = FXCollections.observableArrayList<ImageArchiveModel>()
    var sprites = FXCollections.observableArrayList<SpriteModel>()

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        encodingCb.items = encodingOptions
        encodingCb.valueProperty().addListener { _, _, newValue ->
            newValue ?: return@addListener
            val selectedArchive = archiveList.selectionModel.selectedItem ?: return@addListener
            val selectedSpriteModel = spriteList.selectionModel.selectedItem ?: return@addListener

            val spriteModel = selectedArchive.sprites[selectedSpriteModel.id]
            spriteModel.sprite.format = newValue.id
            selectedSpriteModel.sprite.format = newValue.id
        }

        canvas.widthProperty().bind(canvasParent.widthProperty())
        canvas.heightProperty().bind(canvasParent.heightProperty())

        val filteredList = FilteredList(archives, {_ -> true})
        searchTf.textProperty().addListener { _, _, newValue -> filteredList.setPredicate { it ->
            if (newValue == null || newValue.isEmpty()) {
                return@setPredicate true
            }

            val lowercase = newValue.toLowerCase()

            if (it.toString().toLowerCase().contains(lowercase)) {
                return@setPredicate true
            }

            return@setPredicate false
        }
        }

        val sortedList = SortedList(filteredList)
        archiveList.items = sortedList

        archiveList.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            newValue ?: return@addListener

            sprites.clear()
            sprites.addAll(newValue.sprites)

            updateArchiveInfo(newValue.sprites)

            spriteList.isVisible = true
        }

        spriteList.items = sprites
        spriteList.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            newValue ?: return@addListener
            val sprite = newValue.sprite
            var bimage = sprite.toBufferedImage() ?: return@addListener

            if (bimage.width > 256 || bimage.height > 256) {
                bimage = Scalr.resize(bimage, 256, 256)
            }

            updateSpriteInfo(sprite)

            val g = canvas.graphicsContext2D
            g.clearRect(0.0, 0.0, canvas.width, canvas.height)
            g.drawImage(SwingFXUtils.toFXImage(bimage, null), (canvas.width / 2) - (bimage.width / 2), (canvas.height / 2) - (bimage.height / 2))
        }

        spriteList.setCellFactory { _ ->
            object : ListCell<SpriteModel?>() {
                private val imageView = ImageView()
                public override fun updateItem(model: SpriteModel?, empty: Boolean) {
                    super.updateItem(model, empty)
                    if (empty || model == null) {
                        text = null
                        graphic = null
                    } else {
                        var bimage = model.sprite.toBufferedImage()

                        if (bimage.width > 64 || bimage.height > 64) {
                            bimage = Scalr.resize(bimage, 64, 64)
                        }

                        imageView.image = SwingFXUtils.toFXImage(bimage, null)

                        text = model.toString()
                        graphic = imageView
                    }
                }
            }
        }

    }

    @FXML
    private fun onAction(event: ActionEvent) {
        val selectedArchive = archiveList.selectionModel.selectedItem ?: return
        val selectedSpriteModel = spriteList.selectionModel.selectedItem ?: return
        val index = selectedSpriteModel.id

        var flag = false

        try {
            val offsetX = offsetXTf.text.toInt()
            selectedArchive.sprites[index].sprite.offsetX = offsetX
            selectedSpriteModel.sprite.offsetX = offsetX
            flag = true
        } catch (ex: IOException) {

        }

        try {
            val offsetY = offsetYTf.text.toInt()
            selectedArchive.sprites[index].sprite.offsetY = offsetY
            selectedSpriteModel.sprite.offsetY = offsetY
            flag = true
        } catch (ex: IOException) {

        }

        if (flag) {
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.headerText = "Saved!"
            alert.showAndWait()
        }
    }

    private fun updateArchiveInfo(models: MutableList<SpriteModel>) {
        val colors = mutableSetOf<Int>()

        for (model in models) {
            colors.addAll(model.sprite.pixels.toMutableSet())
        }

        colorCountT.text = colors.size.toString()
        spriteCountT.text = sprites.size.toString()
    }

    private fun updateSpriteInfo(sprite: RSSprite) {
        offsetXTf.text = sprite.offsetX.toString()
        offsetYTf.text = sprite.offsetY.toString()
        widthT.text = sprite.width.toString()
        heightT.text = sprite.height.toString()
        resizeWidthT.text = sprite.resizeWidth.toString()
        resizeHeightT.text = sprite.resizeHeight.toString()
        pixelCountT.text = (sprite.width * sprite.height).toString()

        if (sprite.format == 0 || sprite.format == 1) {
            encodingCb.selectionModel.select(sprite.format)
        }

        val colors = mutableSetOf<Int>()
        colors.addAll(sprite.pixels.toMutableSet())

        colorsT.text = colors.size.toString()
    }

    @FXML
    private fun addSprite() {
        val selectedArchive = archiveList.selectionModel.selectedItem ?: return

        val chooser = FileChooser()
        chooser.initialDirectory = File("./")
        chooser.title = "Select sprites to add"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("Images", "*.png", "*.jpg"))

        val files = chooser.showOpenMultipleDialog(App.mainStage) ?: return

        val archivedColors = mutableSetOf<Int>()

        for (model in selectedArchive.sprites) {
            archivedColors.addAll(model.sprite.pixels.toMutableSet())
        }

        for (file in files) {
            try {
                var bimage = ImageIO.read(file) ?: continue

                if (bimage.type != BufferedImage.TYPE_INT_RGB) {
                    bimage = bimage.toType(BufferedImage.TYPE_INT_RGB)
                }

                val sprite = RSSprite(bimage)

                val colors = sprite.pixels.toMutableSet()

                if (colors.size > 255) {
                    val alert = Alert(Alert.AlertType.WARNING)
                    alert.headerText = "Sprite=${file.name} exceeds color limit=255 colors=${colors.size}"
                    alert.showAndWait()
                    break
                }

                archivedColors.addAll(sprite.pixels.toMutableSet())

                if (archivedColors.size > 255) {
                    val alert = Alert(Alert.AlertType.WARNING)
                    alert.headerText = "Archive exceeds color limit=255 colors=${archivedColors.size}"
                    alert.showAndWait()
                    break
                }

                selectedArchive.sprites.add(SpriteModel(selectedArchive.sprites.size, sprite))
                sprites.add(SpriteModel(sprites.size, sprite))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    @FXML
    private fun removeSprite() {
        val selectedArchive = archiveList.selectionModel.selectedItem ?: return
        val selectedSprite = spriteList.selectionModel.selectedItem ?: return
        val index = selectedSprite.id

        sprites.removeAt(index)
        selectedArchive.sprites.removeAt(index)

        for (i in 0 until sprites.size) {
            val sprite = sprites[i]
            sprite.id = i

            val archivedSprite = selectedArchive.sprites[i]
            archivedSprite.id = i
        }
    }

    @FXML
    private fun replaceSprite() {
        val selectedArchive = archiveList.selectionModel.selectedItem ?: return
        val selectedSpriteModel = spriteList.selectionModel.selectedItem ?: return
        val selectedSprite = selectedSpriteModel.sprite

        val chooser = FileChooser()
        chooser.initialDirectory = File("./")
        chooser.title = "Select the new sprite"
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("Images", "*.png", "*.jpg"))

        val selectedFile = chooser.showOpenDialog(App.mainStage) ?: return

        try {
            var bimage = ImageIO.read(selectedFile) ?: return

            if (bimage.type != BufferedImage.TYPE_INT_RGB) {
                bimage = bimage.toType(BufferedImage.TYPE_INT_RGB)
            }

            val sprite = RSSprite(bimage)
            sprite.offsetX = selectedSprite.offsetX
            sprite.offsetY = selectedSprite.offsetY
            sprite.format = selectedSprite.format

            selectedArchive.sprites[selectedSpriteModel.id].sprite = sprite
            selectedSpriteModel.sprite = sprite

            updateArchiveInfo(selectedArchive.sprites)
            updateSpriteInfo(sprite)

            val g = canvas.graphicsContext2D
            g.clearRect(0.0, 0.0, canvas.width, canvas.height)
            g.drawImage(SwingFXUtils.toFXImage(bimage, null), (canvas.width / 2) - bimage.width / 2, (canvas.height / 2) - bimage.height / 2)

            spriteList.refresh()
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }

    override fun onPopulate() {
        archives.clear()
        sprites.clear()

        val archive = App.fs.getArchive(RSFileStore.ARCHIVE_FILE_STORE, RSArchive.MEDIA_ARCHIVE)

        val indexHash = HashUtils.hashName("index.dat")

        for (entry in archive.entries) {

            if (entry.hash == indexHash) {
                continue
            }

            val imageArchive = RSImageArchive.decode(archive, entry.hash)
            val spriteModels = mutableListOf<SpriteModel>()

            for (i in 0 until imageArchive.sprites.size) {
                val sprite = imageArchive.sprites[i] ?: continue

                val spriteModel = SpriteModel(i, sprite)

                spriteModels.add(spriteModel)
            }

            archives.add(ImageArchiveModel(entry.hash, spriteModels))
        }
    }

    @FXML
    private fun pack() {
        if (archives.isEmpty()) {
            return
        }

        val archive = RSArchive()

        ByteArrayOutputStream().use { ibos ->
            var idxOffset = 0

            val indexHash = HashUtils.hashName("index.dat")

            DataOutputStream(ibos).use { idxOut ->
                for (archiveModel in archives) {

                    if (archiveModel.hash == indexHash || archiveModel.sprites.isEmpty()) {
                        continue
                    }

                    ByteArrayOutputStream().use { dbos ->

                        DataOutputStream(dbos).use { datOut ->

                            var largestWidth = 0
                            var largestHeight = 0

                            val images = mutableListOf<BufferedImage>()
                            val colorSet = mutableListOf<Int>()
                            colorSet.add(0)

                            for (spriteModel in archiveModel.sprites) {
                                val bimage = spriteModel.sprite.toBufferedImage()

                                if (largestWidth < bimage.width) {
                                    largestWidth = bimage.width
                                }

                                if (largestHeight < bimage.height) {
                                    largestHeight = bimage.height
                                }

                                for (x in 0 until bimage.width) {
                                    for (y in 0 until bimage.height) {
                                        val argb = bimage.getRGB(x, y)
                                        val rgb = argb and 0xFFFFFF

                                        if (colorSet.contains(rgb)) {
                                            continue
                                        }

                                        colorSet.add(rgb)
                                    }
                                }

                                images.add(bimage)

                            }

                            idxOut.writeShort(largestWidth)
                            idxOut.writeShort(largestHeight)
                            idxOut.writeByte(colorSet.size)

                            for (i in 1 until colorSet.size) {
                                idxOut.write24Int(colorSet[i])
                            }

                            for (spriteModel in archiveModel.sprites) {
                                val sprite = spriteModel.sprite
                                idxOut.writeByte(sprite.offsetX)
                                idxOut.writeByte(sprite.offsetY)
                                idxOut.writeShort(sprite.width)
                                idxOut.writeShort(sprite.height)
                                idxOut.writeByte(sprite.format)
                            }

                            datOut.writeShort(idxOffset)

                            idxOffset = idxOut.size()

                            for (spriteModel in archiveModel.sprites) {

                                val sprite = spriteModel.sprite
                                val bimage = sprite.toBufferedImage()

                                if (sprite.format === 0) { // horizontal encoding
                                    for (y in 0 until bimage.height) {
                                        for (x in 0 until bimage.width) {
                                            val argb = bimage.getRGB(x, y)

                                            val rgb = argb and 0xFFFFFF

                                            val paletteIndex = colorSet.indexOf(rgb)

                                            assert(paletteIndex != -1)

                                            datOut.writeByte(paletteIndex)
                                        }
                                    }
                                } else { // vertical encoding
                                    for (x in 0 until bimage.width) {
                                        for (y in 0 until bimage.height) {
                                            val argb = bimage.getRGB(x, y)

                                            val rgb = argb and 0xFFFFFF

                                            val paletteIndex = colorSet.indexOf(rgb)

                                            assert(paletteIndex != -1)

                                            datOut.writeByte(paletteIndex)
                                        }
                                    }
                                }
                            }
                        }

                        archive.writeFile(archiveModel.hash, dbos.toByteArray())

                    }
                }

                archive.writeFile("index.dat", ibos.toByteArray())

            }
        }

        val encoded = archive.encode()

        val store = App.fs.getStore(RSFileStore.ARCHIVE_FILE_STORE)
        store.writeFile(RSArchive.MEDIA_ARCHIVE, encoded)

        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = "Info"
        alert.headerText = "Success!"
        alert.showAndWait()
    }

    @FXML
    private fun createArchive() {
        val dialog = TextInputDialog()
        dialog.title = "Image Archive Creator"
        dialog.headerText = "Create an Image Archive"
        dialog.contentText = "Please enter a name for this archive:"

        val result = dialog.showAndWait()

        result.ifPresent {
            for (archive in archives) {
                val name = archive.toString()

                if (name.equals("index.dat", ignoreCase = true)) {
                    val alert = Alert(Alert.AlertType.WARNING)
                    alert.title = "Warning"
                    alert.headerText = "This name is restricted!"
                    alert.showAndWait()
                    return@ifPresent
                }

                if (it.equals(name, ignoreCase = true)) {
                    val alert = Alert(Alert.AlertType.WARNING)
                    alert.title = "Warning"
                    alert.headerText = "Image Archive already exists!"
                    alert.showAndWait()
                    return@ifPresent
                }
            }

            Settings.putNameForHash(it)
            Settings.save()
            archives.add(ImageArchiveModel(HashUtils.hashName(it), mutableListOf()))

        }
    }

    @FXML
    private fun removeArchive() {
        val selectedArchive = archiveList.selectionModel.selectedItem ?: return
        archives.remove(selectedArchive)
    }

    override fun onClear() {
        spriteList.isVisible = false
        sprites.clear()
        archives.clear()

        colorCountT.text = "0"
        spriteCountT.text = "0"

        offsetXTf.text = "0"
        offsetYTf.text = "0"
        widthT.text = "0"
        heightT.text = "0"
        resizeWidthT.text = "0"
        resizeHeightT.text = "0"
        pixelCountT.text = "0"
        encodingCb.selectionModel.select(0)
        colorsT.text = "0"

        val g = canvas.graphicsContext2D
        g.clearRect(0.0, 0.0, canvas.width, canvas.height)
    }

    @FXML
    private fun exportArchive() {
        val selectedArchive = archiveList.selectionModel.selectedItem ?: return

        val outputDir = File("./dump/$selectedArchive")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        for (spriteModel in selectedArchive.sprites) {
            val bimage = spriteModel.sprite.toBufferedImage()
            ImageIO.write(bimage, "png", File(outputDir, "${spriteModel.id}.png"))
        }

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Information"
        alert.headerText = "Would you like to view these files?"
        alert.contentText = "Choose an option."

        val choiceOne = ButtonType("Yes.")
        val close = ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE)

        alert.buttonTypes.setAll(choiceOne, close)

        val result = alert.showAndWait()

        if (result.isPresent) {

            val type = result.get()

            if (type == choiceOne) {
                try {
                    Desktop.getDesktop().open(outputDir)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }

        }


    }

    @FXML
    private fun exportSprite() {
        val selectedItem = spriteList.selectionModel.selectedItem ?: return

        val outputDir = File("./dump/")

        if (!outputDir.exists()) {
            outputDir.mkdir()
        }

        val bimage = selectedItem.sprite.toBufferedImage()

        ImageIO.write(bimage, "png", File(outputDir, "${selectedItem.id}.png"))

        val alert = Alert(Alert.AlertType.CONFIRMATION)
        alert.title = "Information"
        alert.headerText = "Would you like to view this sprite?"
        alert.contentText = "Choose an option."

        val choiceOne = ButtonType("Yes.")
        val close = ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE)

        alert.buttonTypes.setAll(choiceOne, close)

        val result = alert.showAndWait()

        if (result.isPresent) {

            val type = result.get()

            if (type == choiceOne) {
                try {
                    Desktop.getDesktop().open(outputDir)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }

        }
    }

}