package de.viaboxx.markdown

import groovy.json.JsonSlurper
import groovy.util.slurpersupport.Node
import org.apache.commons.lang.StringUtils

import static de.viaboxx.markdown.Confluence2MD.Mode.*

/**
 * Description: convert a confluence Json page(s)/space to a markdown for pandoc<br>
 * <p>
 * Date: 15.09.14<br>
 * </p>
 * @see Confluence2MDArgsParser#usage
 */
@SuppressWarnings("GroovyFallthrough")
class Confluence2MD extends ConfluenceParser implements Walker {

    PrintStream out = System.out

    enum Mode {
        Default, Section, Panel, BlockQuote, CodeBlock, Table
    }

    protected int listIndent = 0, blockIndent = 0
    protected Integer itemNumber
    protected Mode mode = Default
    protected Table table

    protected def written = null
    protected boolean footnotes = true

    int maxHeaderDepth = 5 // docx does not support more!

    boolean runPlantUml = true
    boolean docHierarchy = true, newLine = true, blankLine = true, strong = false
    boolean titleTransformation = true, titleRootPage = true

    def File outFile

    Confluence2MDArgsParser getArgsParser() {
        return new Confluence2MDArgsParser(this)
    }

    static void main(String[] args) {
        def inst = new Confluence2MD()
        if (inst.argsParser.parseArgs(args)) {
            inst.run()
        }
    }

    void run() {
        try {
            def page = new JsonSlurper().parse(input)
            fillPageIdCache(page)
            log("Generating markdown")
            parsePages(page)
        } finally {
            close()
        }
        log("Done")
    }

    Confluence2MD() {
        caching = true

        tagHandlers = [
                "H1": { node ->
                    writeHeader(2, node)
                    return true
                },
                "H2": { node ->
                    writeHeader(3, node)
                    return true
                },
                "H3": { node ->
                    writeHeader(4, node)
                    true
                },
                "H4": { node ->
                    writeHeader(5, node)
                    true
                },
                "H5": { node ->
                    writeHeader(6, node)
                    true
                },
                "H6": { node ->
                    writeHeader(7, node)
                    true
                },
                "P": { node ->
                    if (mode != Mode.Table && listIndent == 0) {
                        assertBlankLine()
                    }
                    walkThrough(node)
                    writeln()
                    true
                },
                "A": { node ->
                    def href = node.attributes()["href"]
                    if (href) {
                        writeRaw("[")
                        def label = intoString { walkThrough(node) }
                        writeRaw(label)
                        writeRaw("]")
                        writeRaw("(")
                        def link = intoString { write(href as String) }
                        writeRaw(link)
                        writeRaw(")")
                        if (footnotes && label != link
                                && !StringUtils.startsWithIgnoreCase(link, "mailto:")) {
                            writeRaw("^[")
                            writeRaw(link)
                            writeRaw("]")
                        }
                    } // else <a name=\"BACKUP-FILE\"></a> --> anchor not yet supported
                    true
                },
                "AC:LINK": { node ->
                    // <ac:link><ri:page ri:content-title=\"Nachricht - Example\" />
                    // <ac:link><ac:link-body>Example</ac:link-body></ac:link>
                    // <ac:link><ri:attachment ri:filename=\"Monitoring.xls\" /></ac:link>
                    String linkText = null
                    String linkUrl = null
                    def child
                    child = getFirstChildNamed(node, "RI:PAGE")
                    if (child) {
                        linkUrl = child.attributes()["ri:content-title"]
                        if (pageIdCache.get(linkUrl)) {
                            linkText = linkUrl
                            linkUrl = "PAGE_" + pageIdCache.get(linkUrl)
                        } else {
                            log("Link out of scope to page: \'$linkUrl\'")
                            linkText = linkUrl
                            linkUrl = null
                        }
                    }
                    child = getFirstChildNamed(node, "AC:LINK-BODY")
                    if (child) {
                        linkText = child.text()
                    } else {
                        child = getFirstChildNamed(node, "AC:PLAIN-TEXT-LINK-BODY")
                        if (child) {
                            linkText = child.text()
                        }
                    }
                    if (!linkText) {
                        child = getFirstChildNamed(node, "RI:ATTACHMENT")
                        if (child) {
                            linkText = child.attributes()["ri:filename"]
                        }
                    }
                    if (linkUrl) {
                        writeRaw("[")
                        write(linkText ?: linkUrl)
                        writeRaw("](#")
                        writeRaw(linkUrl)
                        writeRaw(")")
                    } else if (linkText) {
                        writeRaw("_")
                        write(linkText)
                        writeRaw("_ ")
                    }
                    true
                },
                "BLOCKQUOTE": { node ->
                    if (mode != Mode.Table) {
                        def needBlankLine = (mode != BlockQuote)
                        withMode(BlockQuote) {
                            blockIndent++
                            if (needBlankLine) assertBlankLine()
                            walkThrough(node)
                            blockIndent--
                        }
                        return true
                    }
                    false
                },
                "AC:RICH-TEXT-BODY": { node ->
                    if (mode == Default) {
                        String text = intoString {
                            withMode(Panel) {
                                walkThrough(node)
                            }
                        }
                        if (text) {
                            if (!newLine) writeln()
                            writeRaw(text)
                            assertBlankLine()
                        }
                        return true
                    }
                    false
                },
                "PRE": { node ->
                    if (mode == Default) {
                        String text = intoString {
                            def needBlankLine = (mode != BlockQuote)
                            withMode(BlockQuote) {
                                blockIndent++
                                if (needBlankLine) assertBlankLine()
                                walkThrough(node)
                                blockIndent--
                            }
                        }
                        if (text) {
                            if (!newLine) writeln()
                            writeRaw(text)
                            assertBlankLine()
                        }
                        return true
                    }
                    false
                },
                "AC:STRUCTURED-MACRO": { node ->
                    def macroName = node.attributes().get('ac:name')
                    List<Node> parameters = getChildrenNamed(node, "AC:PARAMETER")
                    Node titleNode = getParameterWithAttribute(parameters, "ac:name", "title")
                    String title = null
                    if (titleNode) {
                        title = titleNode.text().trim()
                    }
                    // Node collapseNode = getParameterWithAttribute(parameters, "ac:name", "collapse")
                    // boolean collapse = collapseNode?.text() == "true"
                    switch (macroName) {
                        case "code":
                            if (title && (title.equalsIgnoreCase(PLANT_UML)
                                    || StringUtils.startsWithIgnoreCase(title, PLANTUML_PREFIX1)
                                    || StringUtils.startsWithIgnoreCase(title, PLANTUML_PREFIX2))) {
                                Node umlNode = getFirstChildNamed(node, "AC:PLAIN-TEXT-BODY")
                                if (umlNode) {
                                    String imageTitle = null
                                    if (StringUtils.startsWithIgnoreCase(title, PLANTUML_PREFIX1)) {
                                        imageTitle = title.substring(PLANTUML_PREFIX1.length())
                                    } else if (StringUtils.startsWithIgnoreCase(title, PLANTUML_PREFIX2)) {
                                        imageTitle = title.substring(PLANTUML_PREFIX2.length())
                                    }
                                    plantUML(umlNode, imageTitle)
                                    return true
                                }
                            }
                        case "noformat":
                        case "panel":
                        case "info":
                        case "warning":
                            // ignore
                            if (title) {
                                assertBlankLine()
                                writeRaw("**")
                                if (macroName == "info") write("(i) ")
                                else if (macroName == "warning") write("(!) ")
                                write(title)
                                writeRaw("**")
                                writeln()
                            }
                            if (mode != Mode.Table && macroName != "code" && macroName != "noformat") {
                                def needBlankLine = (mode != BlockQuote)
                                withMode(BlockQuote) {
                                    blockIndent++
                                    if (needBlankLine) assertBlankLine()
                                    walkThrough(node)
                                    blockIndent--
                                }
                                return true
                            }
                            break
                        case "section": // Inhaltsverzeichnis
                            if (mode == Default) {
                                withMode(Section) {
                                    walkThrough(node)
                                }
                                return true
                            }
                        default:
                            log("WARN: '$macroName' structured-macro not supported")
                    }
                    false
                },
                "AC:PARAMETER": { node -> true /* skip */ },
                "TR": { node ->
                    table.rows << new Row()
                    walkThrough(node)
                    writeRaw("|\n")
                    if (table.rows.size() == 1) {
                        table.row.renderSeparator(this)
                    }
                    return true
                },
                "TD": { node ->
                    writeRaw("|")
                    table.row.cells << new Cell(node)
                    table.row.cell.render(this)
                    return true
                },
                "TH": { node ->
                    writeRaw("|")
                    table.row.cells << new Cell(node)
                    table.row.cell.render(this)
                    return true
                },
                "BR": { node ->
                    writeln()
                    return true
                },
                "S": { node -> // strikeout
                    writeRaw("~~")
                    def text = intoString { walkThrough(node) }
                    writeRaw(text.trim())
                    writeRaw("~~")
                    return true
                },
                "HR": { node ->
                    writeRaw("\n---\n")
                    return true
                },
                "AC:IMAGE": { node ->
                    /*
                    <ac:image><ri:attachment ri:filename=\"Resequencer.png\" /></ac:image>
                     */
                    String title = node.attributes()["ac:title"]
                    String url = null
                    def child = getFirstChildNamed(node, "RI:ATTACHMENT")
                    if (child) {    // attached image
                        String fileName = child.attributes()["ri:filename"]
                        title = title ?: fileName
                        Node page = getFirstChildNamed(child, "RI:PAGE")
                        def pageId
                        if (page) {
                            String pageTitle = page.attributes()["ri:content-title"]
                            String pageSpace = page.attributes()["ri:space-key"]
                            pageId = pageTitle ? queryPageIdByTitle(pageTitle, pageSpace) : currentPage.id
                            if (!pageId) pageId = currentPage.id
                        } else {
                            pageId = currentPage.id
                        }
                        def attachments = pageId ? getAttachments(pageId) : null
                        def attachment = findAttachmentTitled(attachments, fileName)
                        if (!attachment) {
                            log("WARN: Cannot find attachment $fileName")
                        } else {
                            url = downloadedFile(attachment).path
                        }
                    } else {
                        child = getFirstChildNamed(node, "RI:URL")
                        if (child) { // image by URL
                            url = child.attributes()["ri:value"]
                        }
                    }
                    assertBlankLine()
                    writeRaw("![")
                    write(title ?: url)
                    writeRaw("](")
                    write(url)
                    writeRaw(")\n")
                    return true
                },
                "UL": { node ->
                    withList {
                        itemNumber = null
                        walkThrough(node)
                    }
                    return true
                },
                "OL": { node ->
                    withList {
                        itemNumber = 1
                        walkThrough(node)
                    }
                    return true
                },
                "LI": { node ->
                    //                def oldItemNumber = itemNumber
                    //                itemNumber = null
                    assertBlankLine()
                    //                itemNumber = oldItemNumber
                    ((listIndent - 1) * 2).times { writeRaw(' ') }
                    if (itemNumber != null) {
                        write("${itemNumber}. ")
                        itemNumber = itemNumber + 1
                    } else {
                        writeRaw("+ ")
                    }
                    false
                },
                "TABLE": { node ->
                    // we use pipe_tables: Pandoc says:
                    /* "The cells of pipe tables cannot contain block elements like paragraphs and lists,
                       and cannot span multiple lines." */
                    // see http://johnmacfarlane.net/pandoc/demo/example9/pandocs-markdown.html#extension-pipe_tables

                    /* At least multiple lines are possible, with a \\ at the end of the line.
                      But paragraphs and list corrupt the table! */

                    if (mode == Mode.Table) {
                        // nested tables not supported by pandoc
                        def table = intoString {
                            assertBlankLine()
                            withMode(Mode.Table) {
                                def oldTable = table
                                table = new Table()
                                walkThrough(node)
                                table = oldTable
                            }
                            assertBlankLine()
                        }
                        log("WARN nested table not supported: $table")
                        writeRaw("{table}" + table.replace("|", ",").replace('\n', ';') + "{/table}")
                    } else {
                        assertBlankLine()
                        withMode(Mode.Table) {
                            def oldTable = table
                            table = new Table()
                            walkThrough(node)
                            table = oldTable
                        }
                        assertBlankLine()
                    }
                    return true
                },
                "TBODY": { node -> false /* ignore */ },
                "SPAN": { node ->// ignore
                    walkThrough(node)
                    if (written instanceof String) {  /* char(160) &nbsp; */
                        if (!written.endsWith(' ') && !written.endsWith("\u00A0")) {
                            written = 1  // space maybe to be written
                        }
                    }
                    return true
                },
                "AC:EMOTICON": { node ->
                    def icon = node.attributes()["ac:name"]
                    switch (icon) {
                        case "minus":
                            write(" (-) ")
                            break
                        case "smile":
                            write(" :-) ")
                            break
                        case "sad":
                            write(" :-( ")
                            break
                        case "cheeky":
                            write(" :-P ")
                            break
                        case "laugh":
                            write(" :-D ")
                            break
                        case "wink":
                            write(" ;-) ")
                            break
                        case "thumbs-up":
                            write(" (^.^) ")
                            break
                        case "thumbs-down":
                            write(" (:-[) ")
                            break
                        case "tick":
                            write(" (ok) ")
                            break
                        case "cross":
                            write(" (x) ")
                            break
                        case "warning":
                            write(" (!) ")
                            break
                        case "question":
                            write(" (?) ")
                            break
                        default:
                            write("($icon)")
                    }
                    return true
                },
                "DIV": { node -> false /* ignore */ },
                "AC:MACRO": { node -> // e.g. plantUML
                    def macro = node.attributes()['ac:name']
                    switch (macro) {
                        case "plantuml":   /* ac:title: not yet tested if tag name correct */
                            plantUML(node, (node.attributes()['ac:title']) as String)
                            break
                        default:
                            log("Unknown macro tag ${node.name()} = ${macro}")
                    }
                    return true
                },
        ]

        def handler = { node -> // codeblock
            /**
             * Problem codeblock inside table currently not supported,
             * see http://comments.gmane.org/gmane.text.pandoc/5170
             */
            /*
 +-----------------------+------------------------+
 | ~~~~                  |                        |
 | This is a code block! | This is ordinary text! |
 | ~~~~                  |                        |
 +-----------------------+------------------------+
             */
            if (mode != Default) {
                log("WARN code block nested in $mode currently not supported: " + node.text())
                write(node.text())
            } else {
                withMode(CodeBlock) {
                    writeRaw("\n\n~~~~~~~\n")
                    write(node.text())
                    writeRaw("\n~~~~~~~\n")
                }
            }
            return true
        }
        tagHandlers["CODE"] = handler
        tagHandlers["AC:PLAIN-TEXT-BODY"] = handler

        handler = { node ->
            def markdown = intoString {
                walkThrough(node)
            }
            markdown = writeMovedSpaces(markdown)
            writeRaw("_")
            writeRaw(markdown)
            writeRaw("_ ")
            return true
        }
        tagHandlers["EM"] = handler
        tagHandlers["I"] = handler  // italic = emphasis
        tagHandlers["U"] = handler  // underline: not yet supported. using italic

        handler = { node ->
            if (strong) { // avoid duplication of ** because **** would not work, this can happen when <strong><em>... is nested
                return false
            } else {
                strong = true
                def markdown = intoString {
                    walkThrough(node)
                }
                markdown = writeMovedSpaces(markdown)
                writeRaw("**")
                writeRaw(markdown)
                writeRaw("**")
                strong = false
                return true
            }
        }
        tagHandlers["STRONG"] = handler
        tagHandlers["B"] = handler
    }

    protected void handlePage(page) {
        if (depth > 0 || titleRootPage) {
            writeHeader(1, {
                write("${pageTitle(page.title)}")
            }, "PAGE_${page.id}")
        }
    }

    protected String pageTitle(String realTitle) {
        if (!titleTransformation) return realTitle
        String tok = ' - '
        int pos = realTitle.indexOf(tok)
        if (pos < 0) return realTitle
        return realTitle.substring(pos + tok.length())
    }

    protected void fillPageIdCache(def page) {
        withPages(page) { each ->
            def effectiveLevel = effectiveLevel(1)
            if (effectiveLevel <= maxHeaderDepth) {
                log("${depth} - Found page $each.id '$each.title'")
                pageIdCache.put(each.title, each.id)
            } else {
                log("${depth} - Found page $each.id '${each.title}', but header depth $effectiveLevel is too deep in the hierachy")
            }
        }
    }

    String intoString(Closure process) {
        def baos = new ByteArrayOutputStream()
        def out = new PrintStream(baos)
        withOut(out, process)
        return baos.toString()
    }

    void withOut(final PrintStream out, Closure process) {
        def old = this.out
        this.out = out
        process()
        this.out = old
    }

    protected String INTERPUNKTION = ".,;:\"'"

    protected void writeHeader(int level, def node) {
        if (node.text()) {
            writeHeader(level) {
                write(node.text().trim())
            }
        } else {
            writeHeader(level) {
                walkThrough(node)
            }
        }
    }

    protected void writeHeader(int level, Closure processor, String ref = null) {
        if (!blankLine) {
            if (!newLine) writeln()
            writeln()
        }
        final int effectiveLevel = effectiveLevel(level)
        boolean isHeader = writeHeaderBeginTags(effectiveLevel)
        processor()
        writeHeaderEndTags(effectiveLevel)
        if (ref && isHeader) writeRaw(" {#${ref}}")
        assertBlankLine()
    }

    protected int effectiveLevel(int level) {
        return docHierarchy ? depth + (titleRootPage ? level : level - 1) : level
    }

    protected void assertBlankLine() {
        if (!blankLine) {
            if (!newLine) writeln()
            writeln()
        }
    }

    protected boolean writeHeaderBeginTags(int level) {
        return writeHeaderTags(level)
    }

    protected boolean writeHeaderEndTags(int level) {
        return writeHeaderTags(level)
    }

    protected boolean writeHeaderTags(int level) {
        if (mode != Default || level > maxHeaderDepth) {
            writeRaw("__")
            return false
        } else {
            level.times { writeRaw('#') }
            return true
        }
    }

    protected handleText(String text) {
        if (written == 1 && text) { // workaround, because neko-html parser does not detect spaces between <span>
            /* example 1: write the space
            <span class=\"hps\">is a locker</span> <span class=\"hps\">system</span>
            example 2: do not write the space because there it no space before a ,
            <span class=\"hps\">system</span><span>,</span> where parcels
            */
            int lastChar = text.charAt(text.length() - 1)
            if (INTERPUNKTION.indexOf(lastChar) < 0) writeRaw(' ')
        }
        write(text)
        written = text
    }

    protected String writeMovedSpaces(String markdown) {
        int idx = 0
        while (markdown.length() > idx && markdown.charAt(idx) == ' ') {
            idx++
            writeRaw(" ") // write spaces before **  or _ because after ** or _ must not follow a direct space
        }
        if (idx > 0) {
            markdown = markdown.substring(idx)
        }
        markdown
    }

    protected void plantUML(Node node, String title = null) {
        def text = node.text()
        if (runPlantUml) {
            imageCounter++
            File img = umlImageGenerator.generateImage(text).file
            assertBlankLine()
            writeRaw("![")
            write(title ?: img.name)
            writeRaw("](")
            write(img.path)
            writeRaw(")\n")
        } else {
            log("Not rendered PlantUML - ${title ?: ''}: \n" + text)
        }
    }

    void writeln() {
        boolean newLineBefore = newLine
        if (mode == Mode.Table) {
            out.println('\\')
            newLine = true
        } else {
            out.println()
//            if (itemNumber != null) out.print(" ")
            newLine = true
            if (mode == Panel) {
                writeRaw("\n| ")
            } else if (mode == BlockQuote) {
                writeRaw("\n")
                blockIndent.times { writeRaw("> ") }
            }
        }
        blankLine = newLine && newLineBefore
    }

    void log(String text) {
        if (verbose) {
            (depth * 2).times { print(" ") }
            println(text)
        }
    }

    void write(String text) {
        if (text) {
            writeRaw(transform(text))
        }
    }

    protected void computeLineStatus(String text) {
        if (mode == Panel) {
            newLine = text.endsWith("\n ")
            blankLine = text.endsWith("\n \n ")
        } else if (mode == BlockQuote) {
            StringBuffer buf = new StringBuffer(2 * blockIndent)
            blockIndent.times { buf.append("> ") }
            def nl = "\n " + buf.toString()
            newLine = text.endsWith(nl)
            blankLine = text.endsWith(nl + nl)
        } else if (mode == Mode.Table) {
            text = text.replace('\n', '\\\n')
            newLine = text.endsWith('\\\n')
            blankLine = text.endsWith('\\\n\\\n')
        } else {
            newLine = text.endsWith('\n')
            blankLine = text.endsWith('\n\n')
        }
    }

    protected static final String[] SEARCH = ['_', '$', '*', '\\', '<', '#', "^[", "*", "`", "{", "}", "[", "]", ">", "#", "+", "-", ".", "!"] as String[]
    protected static final String[] REPLACE = ['\\_', '\\$', '\\*', '\\\\', '\\<', '\\#', "^\\[", "\\*", "\\`", "\\{", "\\}", "\\[", "\\]", "\\>", "\\#", "\\+", "\\-", "\\.", "\\!"] as String[]

    protected String transform(String text) {
        // escape unwanted footnotes etc.
        if (mode != CodeBlock && itemNumber == null) { // CodeBlock: do not replace most of the things
            text = StringUtils.replaceEach(text, SEARCH, REPLACE)
        }
        if (mode == Panel) {
            text = text.replace("\n", "\n ")
        } else if (mode == BlockQuote) {
            StringBuffer buf = new StringBuffer(2 * blockIndent)
            blockIndent.times { buf.append("> ") }
            text = text.replace("\n", "\n " + buf.toString())
        } else if (mode == Mode.Table) {
            text = text.replace('\n', '\\\n')
        }
        // \`*_{}[]()>#+-.!
        return text
    }

    void writeRaw(String text) {
        if (text) {
            out.print(text)
            computeLineStatus(text)
        }
    }

    protected void withList(Closure processor) {
        listIndent++
        def oldItemNumber = itemNumber
        processor()
        itemNumber = oldItemNumber
        listIndent--
        assertBlankLine()
    }

    protected void withMode(final Mode mode, Closure processor) {
        def old = this.mode
        this.mode = mode
        processor()
        this.mode = old
    }

    void close() {
        super.close()
        if (out instanceof Closeable) out.close()
    }

}
