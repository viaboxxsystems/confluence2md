package de.viaboxx.markdown

import groovy.json.JsonSlurper
import groovy.util.slurpersupport.Node
import org.apache.commons.io.FileUtils

import javax.xml.bind.DatatypeConverter

/**
 * Description: methods for parsing and accessing confluence via REST API <br>
 * <p>
 * Date: 24.10.14<br>
 * </p>
 */
abstract class ConfluenceParser implements NodeHandler {
    static def GET_PAGE_BODY = { pageId -> "/rest/api/content/${pageId}?expand=body.storage,version" }
    static def GET_CHILD_PAGES = { pageId -> "/rest/api/content/${pageId}/child/page?limit=300" }
    static def GET_CHILD_ATTACHMENTS = { pageId -> "/rest/api/content/${pageId}/child/attachment?limit=300" }
    static def QUERY_PAGE_BY_TITLE = { title -> "/rest/api/content?title=${URLEncoder.encode(title, 'UTF-8')}" }
    static def QUERY_PAGE_BY_TITLE_AND_SPACE = { title, space -> "/rest/api/content?title=${URLEncoder.encode(title, 'UTF-8')}&spaceKey=${URLEncoder.encode(space, 'UTF-8')}" }

    static def ADD_ATTACHMENT = { pageId -> "/rest/api/content/${pageId}/child/attachment" }
    static def UPDATE_ATTACHMENT_DATA = { pageId, attachmentId -> "/rest/api/content/${pageId}/child/attachment/${attachmentId}/data" }
    static def UPDATE_PAGE_BODY = { pageId -> "/rest/api/content/${pageId}" }

    protected static final String PLANT_UML = "PlantUML"
    protected static final String PLANTUML_PREFIX1 = PLANT_UML + " - "
    protected static final String PLANTUML_PREFIX2 = PLANT_UML + ": "

    String userPassword  // user:password
    boolean verbose = true
    File downloadFolder = new File("attachments")

    String getUser() {
        return userPassword ? userPassword.substring(0, userPassword.indexOf(':')) : null
    }

    String getPassword() {
        return userPassword ? userPassword.substring(userPassword.indexOf(':') + 1) : null
    }

    /**
     * InputStream, URL or File (or something a JsonSlurper can parse)
     */
    def input

    String wikiServerUrl = "https://viaboxx.atlassian.net/wiki"
    String rootPageId
    int maxDepth = -1
    protected int depth = 0
    def currentPage  // LazyMap
    int imageCounter = 0 // per currentPage

    protected Map<String, Object> tagHandlers = [:]
    protected boolean caching = true // file caching true or false
    protected Map pageIdCache = [:]  // key = page.title, value = page.id
    protected Map attachmentCache = [:] // key = page.id, value = attachments (jsonSlurper)
    protected boolean gfm = false // GitHub flavoured markdown

    protected PlantUmlImageGenerator umlImageGenerator = new PlantUmlImageGenerator(this)

    protected def openInput(String urlString, String cache = null) {
        URL url = new URL(urlString)
        File cacheFile = cache ? new File(downloadFolder, "." + cache + ".json") : null
        if (caching && cacheFile?.exists()) {
            log("Found cached file $cacheFile.name")
            return new FileInputStream(cacheFile)
        }
        log("Requesting $urlString")
        if (userPassword) {
            def conn = url.openConnection()
            String basicAuth = "Basic " + DatatypeConverter.printBase64Binary(userPassword.getBytes())
            conn.setRequestProperty("Authorization", basicAuth)
            if (!cache) return conn.inputStream
            else {
                def stream = conn.inputStream
                FileUtils.copyInputStreamToFile(stream, cacheFile)
                stream.close()
                return new FileInputStream(cacheFile)
            }
        } else {
            if (!cache) return url
            else {
                def stream = url.openStream()
                FileUtils.copyInputStreamToFile(stream, cacheFile)
                stream.close()
                return new FileInputStream(cacheFile)
            }
        }
    }

    protected InputStream openStream(String urlString) {
        def stream = openInput(urlString)
        if (stream instanceof URL) stream = stream.openStream()
        return stream
    }

    void log(String text) {
        if (verbose) {
            println(text)
        }
    }

    void close() {
        close(input)
    }

    void close(def inputThing) {
        if (inputThing instanceof Closeable) input.close()
    }

    protected void withPages(def page, Closure processor) {
        processor(page)
        if (rootPageId && goDeeper()) {
            def queryChildren = openInput(wikiServerUrl + GET_CHILD_PAGES(page.id), page.id + ".children")
            try {
                def children = new JsonSlurper().parse(queryChildren)
                depth++
                children.results.each { child ->
                    withPages(child, processor)
                }
                depth--
            } finally {
                close(queryChildren)
            }
        }
    }

    void parseBody(String body) {
        def xmlSlurper = new XmlSlurper(HTMLParser.build())
        def html = xmlSlurper.parseText("<html><body>" + body + "</body></html>")
        html.BODY.childNodes().each { Node node ->
            if (!handleNode(node)) {
                walkThrough(node)
            }
        }
    }

    void walkThrough(def parent) {
        parent.children().each { def nodeOrText ->
            if (nodeOrText instanceof Node) {
                if (!handleNode(nodeOrText as Node)) {
                    walkThrough(nodeOrText as Node)
                }
            } else {
                handleText(nodeOrText as String)
            }
        }
    }

    void parsePages(page) {
        withPages(page) { each ->
            currentPage = each
            imageCounter = 0
            if (!each.body) {
                def childInput = openInput(wikiServerUrl + GET_PAGE_BODY(each.id), each.id + ".body")
                try {
                    parsePage(new JsonSlurper().parse(childInput))
                } finally {
                    close(childInput)
                }
            } else {
                parsePage(each)
            }
        }
    }

    protected void parsePage(page) {
        log("${depth} - Processing page $page.id '$page.title'")
        handlePage(page)
        parseBody(page.body.storage.value)
    }

    protected boolean goDeeper() {
        return (maxDepth < 0 || maxDepth > depth)
    }

    protected abstract void handlePage(page)

    protected abstract handleText(String text)

    boolean handleNode(Node node) {
        def handler = tagHandlers[node.name()]
        if (handler instanceof Closure) {
            return handler(node)
        } else if (handler instanceof NodeHandler) {
            return handler.handleNode(node)
        } else { // "COL", "COLGROUP", "CITE" and others
            unhandledTag(node)
        }
        return false
    }

    protected void unhandledTag(Node node) {
        log("Unhandled tag ${node.name()} = ${node.text()}")
    }

    Node getFirstChildNamed(Node node, String name) {
        return node.children().find { child ->
            (child instanceof Node && name == child.name())
        }
    }

    List<Node> getChildrenNamed(Node node, String name) {
        return node.children().findAll { child ->
            (child instanceof Node && name == child.name())
        }
    }

    Node getParameterWithAttribute(List<Node> parameters, String attributeName, String attributeValue) {
        return parameters.find({
            it.attributes()[attributeName] == attributeValue
        })
    }

    def findAttachmentTitled(def attachments, String title) {
        if (attachments == null) return null
        return attachments.results.find { it.title == title }
    }

    String queryPageIdByTitle(String title, String spaceKey = null) {
        def pageId = pageIdCache.get(title)
        if (pageId) return pageId
        def input = openInput(wikiServerUrl +
                (spaceKey ? QUERY_PAGE_BY_TITLE_AND_SPACE(title, spaceKey) : QUERY_PAGE_BY_TITLE(title)),
                spaceKey ? '$' + spaceKey + '$' + title + ".title.query" : title + ".title.query")
        def json = new JsonSlurper().parse(input)
        def page = json.results.find { it.title == title }
        close(input)
        if (page) {
            pageId = page.id
            pageIdCache.put(title, pageId)
        }
        return pageId
    }

    def getAttachments(def pageId) {
        def attachments = attachmentCache[pageId]
        if (attachments == null) {
            def url = wikiServerUrl + GET_CHILD_ATTACHMENTS(pageId)
            def stream = openInput(url, pageId + ".attachments")
            try {
                attachments = new JsonSlurper().parse(stream)
                attachmentCache[pageId] = attachments
            } finally {
                close(stream)
            }
        }
        return attachments
    }

    File downloadedFile(def attachment) {
        if (!downloadFolder.exists()) downloadFolder.mkdirs()
        File targetFile = new File(downloadFolder, attachment.id + "_" + attachment.title)
        if (caching && targetFile.exists()) { // speed up - use existing file
            log("Found downloaded file ${targetFile.name}")
        } else { // download and save
            def downloadUrl = wikiServerUrl + attachment._links.download
            log("Downloading '${targetFile.name}' from '$downloadUrl'")
            def stream = openStream(downloadUrl)
            FileUtils.copyInputStreamToFile(stream, targetFile)
            stream.close()
        }
        return targetFile
    }

}
