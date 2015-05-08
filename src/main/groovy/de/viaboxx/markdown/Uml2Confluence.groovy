package de.viaboxx.markdown

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.slurpersupport.Node
import org.apache.commons.lang.StringUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.StringEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

/**
 * Description: Read confluence pages with REST-API, generate puml-Images and upload the images as file-attachments to the page.
 * Then checks if the page shows the image and - if not - modify the page content to show the image.<br>
 * <p>
 * Date: 24.10.14<br>
 * </p>
 * Links:
 * <ul>
 * <li><a href="https://developer.atlassian.com/display/CONFDEV/Remote+Confluence+Methods">Old: Remote Confluence Methods - Notifications</a></li>
 * <li><a href="https://developer.atlassian.com/display/CONFDEV/Confluence+REST+API">New: Confluence REST API</a></li>
 * <li><a href="https://docs.atlassian.com/atlassian-confluence/REST/latest/#d3e841">New: Confluence REST API documentation</a></li>
 * <li><a href="https://developer.atlassian.com/display/CONFDEV/Confluence+REST+API+Examples?continue=https%3A%2F%2Fdeveloper.atlassian.com%2Fdisplay%2FCONFDEV%2FConfluence%2BREST%2BAPI%2BExamples&application=dac#ConfluenceRESTAPIExamples-ManipulatingContent">Confluence REST API Examples</a></li>
 * <li><a href="https://bunjil.jira-dev.com/wiki/plugins/servlet/restbrowser#/">Atlassian REST API Browser</a></li>
 * </ul>
 */
class Uml2Confluence extends ConfluenceParser {
    boolean dryRun = false
    boolean updateAttachments = true // true: add/update images as attachments
    boolean updatePage = true // true: modify page content

    static void main(String[] args) {
        def inst = new Uml2Confluence()
        if (inst.argsParser.parseArgs(args)) {
            inst.run()
        }
    }

    Uml2Confluence() {
        caching = false
        tagHandlers["AC:MACRO"] = { node -> // e.g. plantUML
            def macro = node.attributes()['ac:name']
            switch (macro) {
                case "plantuml":   /* ac:title: not yet tested if tag name correct */
                    plantUML(node, (node.attributes()['ac:title']) as String)
                    break
                default:
                    log("Unknown macro tag ${node.name()} = ${macro}")
            }
            return true
        }

        tagHandlers["AC:STRUCTURED-MACRO"] = { node ->
            def macroName = node.attributes().get('ac:name')
            List<Node> parameters = getChildrenNamed(node, "AC:PARAMETER")
            Node titleNode = getParameterWithAttribute(parameters, "ac:name", "title")
            String title = null
            if (titleNode) {
                title = titleNode.text().trim()
            }
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
                    break
                case "section":
                    def old = section
                    sectionCount++
                    section = new Section(sectionNumber: sectionCount, exists: true)
                    sections << section
                    walkThrough(node)
                    section = old
                    return true
            }
            false
        }

        tagHandlers["AC:IMAGE"] = { node ->
            /*
            <ac:image><ri:attachment ri:filename=\"Resequencer.png\" /></ac:image>
             */
            def child = getFirstChildNamed(node, "RI:ATTACHMENT")
            if (child) {    // attached image
                String fileName = child.attributes()["ri:filename"]
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
                if (pageId == currentPage.id && section && section.imageFile == null) {
                    section.imageFile = fileName
                }
            }
            return true
        }
    }

    int sectionCount = 0
    Section section
    def sections = []

    class Section {
        boolean exists
        int sectionNumber
        String imageFile
        File umlFile
        String title

        String toString() {
            return "{" + title + ", " + umlFile + "}"
        }
    }

    Uml2ConfluenceArgsParser getArgsParser() {
        return new Uml2ConfluenceArgsParser(this)
    }


    void run() {
        try {
            def page = new JsonSlurper().parse(input)
            log("Parsing pages")
            parsePages(page)
        } finally {
            close()
        }
        log("Done")
    }

    protected void plantUML(Node node, String title = null) {
        def text = node.text()
        imageCounter++
        UmlAttachment att = umlImageGenerator.generateImage(text)
        attach(att, currentPage.id)
        if (section == null || section.umlFile != null) {
            sectionCount++
            section = new Section(exists: false, sectionNumber: sectionCount)
            sections << section
        }
        section.title = title
        section.umlFile = att.file
    }

    /**
     * add or update the file as attachment on the page with given pageId
     * @param pageId - id of the page
     */
    void attach(UmlAttachment att, def pageId) {
        if (dryRun || !updateAttachments) return
        def attachments = pageId ? getAttachments(pageId) : null
        def attachment = findAttachmentTitled(attachments, att.file.name)
        if (attachment) {
            // compare md5 before upload
            if (att.md5 != attachment.metadata.comment) {
                updateAttachment(att, pageId, attachment)
            } else {
                log("MD5 not changed for ${att.file.name} => " + att.md5)
            }
        } else {
            addAttachment(att, pageId)
        }
    }

    void updateAttachment(UmlAttachment att, String pageId, def attachment) {
        post(att, wikiServerUrl + UPDATE_ATTACHMENT_DATA(pageId, attachment.id))
    }

    /**
     * create new attachment from file
     * @param file
     */
    void addAttachment(UmlAttachment att, String pageId) {
        post(att, wikiServerUrl + ADD_ATTACHMENT(pageId))
    }

    /**
     * post multipart attachment
     * @param file
     * @param uri
     */
    protected void post(UmlAttachment att, String uri) {
        FileBody bin = new FileBody(att.file)
        HttpEntity reqEntity = MultipartEntityBuilder.create()
                .addPart("file", bin) // The name of the multipart/form-data parameter that contains attachments must be "file"
                .addTextBody("comment", att.md5)
//                .addTextBody("minorEdit", "true")
                .build()
        log("POST $att.file.name to $uri")
        HttpUriRequest post = RequestBuilder.post()
                .setUri(uri)
                .addHeader("X-Atlassian-Token", "nocheck") // you must submit a header of X-Atlassian-Token: nocheck with the request, otherwise it will be blocked.
                .setEntity(reqEntity)
                .build()

        def url = new URL(wikiServerUrl)
        HttpHost target = new HttpHost(url.host, url.port, url.protocol);
        CloseableHttpClient client = httpClient(target)
        // Confluences forbids the request, if not using preemptive authentication using BASIC scheme.
        HttpClientContext localContext = httpContext(target)

        try {
            CloseableHttpResponse response = client.execute(post, localContext)
            try {
                log("POST response: " + response.getStatusLine())
            } finally {
                response.close()
            }
        } finally {
            client.close()
        }
    }

    private HttpClientContext httpContext(HttpHost target) {
        AuthCache authCache = new BasicAuthCache();
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(target, basicAuth);

        // Add AuthCache to the execution context
        HttpClientContext localContext = HttpClientContext.create();
        localContext.setAuthCache(authCache);
        localContext
    }

    private CloseableHttpClient httpClient(HttpHost target) {
        def builder = HttpClients.custom()
        if (userPassword) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider()
//        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password))
            credsProvider.setCredentials(new AuthScope(target.getHostName(), target.getPort()), new UsernamePasswordCredentials(user, password))
            builder.setDefaultCredentialsProvider(credsProvider)
        }
        CloseableHttpClient client = builder.build()
        client
    }

    @Override
    protected void handlePage(page) {
        // ignore
    }

    protected void parsePage(page) {
        sections.clear()
        super.parsePage(page)
        if (updatePage) {
            def newBody = computeModifiedContent(page.body.storage.value)
            if (newBody != page.body.storage.value) {
                // update Page Content
                updatePageContent(page, newBody)
            }
        }
    }

    private void updatePageContent(page, String htmlValue) {
        page.body.storage.value = htmlValue
        def version = page.version.number as Integer
        version++
        page.version.number = version as String

        def newContent = JsonOutput.toJson(page)

        log("Update Page ${page.id} => $newContent")
        if (dryRun) return

        StringEntity content = new StringEntity(newContent)
        HttpUriRequest put
        put = RequestBuilder.put().setUri(wikiServerUrl + UPDATE_PAGE_BODY(page.id))
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setEntity(content)
                .build()

        def url = new URL(wikiServerUrl)
        HttpHost target = new HttpHost(url.host, url.port, url.protocol);
        CloseableHttpClient client = httpClient(target)
        HttpClientContext localContext = httpContext(target)
        try {
            CloseableHttpResponse response = client.execute(put, localContext)
            try {
                log("PUT response: " + response.getStatusLine())
            } finally {
                response.close()
            }
        } finally {
            client.close()
        }
    }

    /**
     * TODO RSt - this approach is still heuristic + experimental! Can corrupt the page!
     * @param content
     * @return
     */
    private String computeModifiedContent(String content) {
        StringBuilder newContent = new StringBuilder(content)
        //log("OLD BODY: \n$newContent")
        sections.each { Section each ->
            if (!each.exists) {
                before(newContent, "<ac:structured-macro ac:name=\"code\">", each.sectionNumber)
                        .insert("<ac:structured-macro ac:name=\"section\"><ac:rich-text-body>")
                if (!each.imageFile) {
                    after(newContent, "</ac:plain-text-body></ac:structured-macro>", each.sectionNumber).insert("</ac:rich-text-body></ac:structured-macro>")
                } else {
                    def instruction = after(newContent, "</ac:image></p>", each.sectionNumber)
                    if (!instruction.condition) {
                        instruction = after(newContent, "</ac:image>", each.sectionNumber)
                    }
                    instruction.insert("</ac:rich-text-body></ac:structured-macro>")
                }
            } else {
                log("${each} - 'section' already in page $currentPage.id")
            }
            if (!each.imageFile && each.umlFile) {
                // insert image from umlFile
                if (each.title) {
                    after(newContent, "</ac:plain-text-body></ac:structured-macro>", each.sectionNumber)
                            .insert("<p><ac:image ac:alt=\"${each.title}\" ac:title=\"${each.title}\">" +
                            "<ri:attachment ri:filename=\"${each.umlFile.name}\" /></ac:image></p>")
                } else {
                    after(newContent, "</ac:plain-text-body></ac:structured-macro>", each.sectionNumber)
                            .insert("<p><ac:image>" +
                            "<ri:attachment ri:filename=\"${each.umlFile.name}\" /></ac:image></p>")
                }
            } else {
                log("${each} - 'image' already in page $currentPage.id")
            }
        }
        //log("NEW BODY: \n$newContent")
        return newContent.toString()
    }

    InstructionBuilder after(StringBuilder buf, String search, int count) {
        def builder = buildInstruction(new InstructionBuilder(),
                count, buf, search)
        builder.position += search.length()
        return builder
    }

    InstructionBuilder before(StringBuilder buf, String search, int count) {
        def builder = buildInstruction(new InstructionBuilder(),
                count, buf, search)
        return builder
    }

    private InstructionBuilder buildInstruction(
            InstructionBuilder builder, int count, StringBuilder buf, String search) {
        int from = 0
        for (int i = 0; i < count && from >= 0 && from < buf.length(); i++) {
            int next = buf.indexOf(search, from)
            if (next >= 0 && i < count - 1) {
                from = next + search.length()
            } else {
                from = next
            }
        }
        builder.buf = buf
        builder.condition = from >= 0 && from < buf.length()
        builder.position = from
        return builder
    }

    class InstructionBuilder {

        int position
        StringBuilder buf
        boolean condition

        void insert(String part) {
            if (condition) {
                buf.insert(position, part)
                log("Insert $part into page $currentPage.id")
            }
        }
    }

    @Override
    protected handleText(String text) {
        // ignore: text in page found
    }

    protected void unhandledTag(Node node) {
        // ignore: other tags
    }

}

