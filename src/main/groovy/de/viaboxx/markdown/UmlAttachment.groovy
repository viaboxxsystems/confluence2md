package de.viaboxx.markdown

import de.viaboxx.markdown.utils.Md5Util

/**
 * Description: <br>
 * <p>
 * Date: 27.10.14<br>
 * </p>
 */
class UmlAttachment {
    String puml
    String md5
    File file

    UmlAttachment computeMd5() {
        md5 = Md5Util.computeMd5(new ByteArrayInputStream(puml.getBytes('UTF-8')))
        return this
    }
}
