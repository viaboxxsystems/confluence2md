# Confluence2MD #

## Idea ##
1. Export JSON (with HTML inside) from Confluence via REST-API, e.g.
curl -u user:password https://viaboxx.atlassian.net/wiki/rest/api/content/3408268/child?expand=page.body.storage
curl -u user:password https://viaboxx.atlassian.net/wiki/rest/api/content/3408270

2. Convert the JSON/HTML to Markdown

3. Convert Markdown to .docx with Pandoc
   (you need to install pandoc on your machine, see "Links")

4. Include the .docx file into a customer-specific parent-document (multi-file doc with title page etc).

==> automate the steps (e.g. as Jenkins job)

## Usage ##

	Usage:
       java Confluence2MD -m wiki <pageId>  > [outputfile]
       java Confluence2MD -m file <nput file>  > [outputfile]
       java Confluence2MD -m url  <input URL>   > [outputfile]

	options:
	-m wiki|file|url specify input format/processing mode (default: wiki)
	-o file specify output format, charset=UTF-8  (default: stdout, charset=file.encoding of plaform)
	-oa file specify output format, charset=UTF-8 - open for append!
	-v true for verbose output       (default: false)
	-u user:password to use HTTP-Basic-Auth to request the URL (default: no auth)
	-depth -1..n the depth to follow down the child-pages hierarchy. -1=infinte, 0=no children (default: -1)
	-server URL of confluence server. used in wiki-mode (default: https://viaboxx.atlassian.net/wiki)
	-plantuml  turn off integrated run of PlantUML to render diagrams (default is to call PlantUML automatically)
	-a download folder for attachments (default: attachments)
	last parameter: the file to read n (-f file) or the URL to get (-f url) or the pageId to start with (-f wiki)
	+H true/false true: document hierarchy used to generate page header format type (child document => h2 etc) (default: true)
	+T true/false true: title transformation ON (cut everything before first -) (default: true)
  	+RootPageTitle true/false true: generate header for root page, false: omit header of root page (default: true)
  	+gfm true/false: generate GitHub Flavored Markdown (default: false) 

## Examples ##

java -jar target/confluence2md-fat.jar Confluence2MD -o wiki.md -u myUser:myPassword <pageId>

java -jar target/confluence2md-fat.jar Confluence2MD +T true +H true +RootPageTitle false -o wiki.md -u myUser:myPassword <pageId>

## Extension: PlantUML ##
If you have a Confluence with PlantUML-Macro installed, you can write and see the UML diagrams in confluence.
If you do not have the PlantUML-Macro, you can still write UML diagrams in confluence and let them render by confluence2md:

Create a Code-Block with title = "PlantUML" and insert you plantUml-code as the body, e.g.

    @startuml
    frame "Load Balancer" {
    [Load Balancer]
    }
    frame "Web Server Node 1" {
        [Apache 1]
        [Load Balancer] --> [Apache 1]
    }
    frame "App Server Node 1" {
        [Apache 1] --> [Tomcat 1]
        [ActiveMQ 1] <- [Tomcat 1]
    }
    @enduml

Confluence2MD will render the text in the same way as it was created with the PlantUML-Macro.
You can give the diagram an image title by chosing a title for the Code-Block that either starts with
"PlantUML: " or "PlantUML - " where the part after this prefix is the image title:

+ "PlantUML - My Picture" will result in an image with title "My Picture"


## Links ##
+ <https://developer.atlassian.com/display/CONFDEV/Confluence+REST+API>
+ <https://docs.atlassian.com/atlassian-confluence/REST/latest/>
+ <https://confluence.atlassian.com/display/DOC/Confluence+Storage+Format>
+ <http://johnmacfarlane.net/pandoc/README.html>

# Uml2Confluence #

Render PlantUml specs in pages and replace/add images as attachment for them. (To update pages when Confluence has no PlantUml plugin installed).

## Examples ##
java -jar target/confluence2md-fat.jar Uml2Confluence -u myUser:myPassword <pageId>


# maven way to deploy to OSSRH and release them to the Central Repository #

see http://central.sonatype.org/pages/apache-maven.html

If your version is a release version:
  mvn clean deploy

(With the property autoReleaseAfterClose set to false) Trigger a release of the staging repository with:
  mvn nexus-staging:release

If something went wrong you can drop the staging repository with:
  mvn nexus-staging:drop

Perform a release deployment to OSSRH with:
  mvn release:clean release:prepare
  mvn release:perform