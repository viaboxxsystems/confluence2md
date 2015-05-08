package de.viaboxx.markdown

import net.sourceforge.plantuml.SourceStringReader
import org.apache.commons.io.FileUtils

/**
 * Description: generate a png file using plantUml<br>
 * <p>
 * Date: 24.10.14<br>
 * </p>
 */
class PlantUmlImageGenerator {
    final ConfluenceParser cp

    PlantUmlImageGenerator(ConfluenceParser cp) {
        this.cp = cp
    }

    /**
     * @param text - plantUml spec or the include to such a spec as attachment
     * @return
     */
    UmlAttachment generateImage(String text) {
        File img = new File(cp.downloadFolder, "puml${cp.currentPage.id}_${cp.imageCounter}.png")
        if (text.trim().startsWith("!include ")) {
            cp.log("Looking for plantUML-!include for ${img.path} with $text")
            def pumlAttachment = text.trim().substring("!include ".length())
            def page
            def idx = pumlAttachment.indexOf("^")
            if (idx >= 0) {
                page = pumlAttachment.substring(0, idx)
                pumlAttachment = pumlAttachment.substring(idx + 1)
            } else {
                page = null
            }
            def attachments
            if (page) {
                def pageId = cp.queryPageIdByTitle(page)
                if (pageId) {
                    attachments = cp.getAttachments(pageId)
                }
            } else {
                attachments = cp.getAttachments(cp.currentPage.id)
            }
            def attachment
            if (attachments) {
                attachment = cp.findAttachmentTitled(attachments, pumlAttachment)
            }
            if (attachment) {
                File pumlFile = cp.downloadedFile(attachment)
                if (pumlFile) {
                    text = FileUtils.readFileToString(pumlFile)
                }
            }
        }
        if (!text.contains("@startuml") && !text.contains("@startdot")) text = "@startuml\n" + text
        if (!text.contains("@enduml") && !text.contains("@enddot")) text += "\n@enduml"
        cp.log("Running PlantUml on ${img.path} with \n$text")
        def reader = new SourceStringReader(text)
        FileOutputStream file = new FileOutputStream(img)
        reader.generateImage(file);
        file.close()
        return new UmlAttachment(file: img, puml: text).computeMd5()
    }
}
