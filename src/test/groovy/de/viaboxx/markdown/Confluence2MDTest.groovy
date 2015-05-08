package de.viaboxx.markdown

import spock.lang.Specification

/**
 * Description: <br>
 * <p>
 * Date: 16.09.14<br>
 * </p>
 */
class Confluence2MDTest extends Specification {
    Confluence2MD c2md
    ByteArrayOutputStream baos

    def setup() {
        c2md = new Confluence2MD()
        baos = new ByteArrayOutputStream()
        c2md.out = new PrintStream(baos)
    }

    private String markdown() {
        return baos.toString()
    }

    def table() {
        when:
        c2md.parseBody('<table><tbody>\n<tr>\n<th><p> AlarmType </p></th>\n<th><p> Message </p></th>\n' +
                '</tr>\n<tr>\n<td><p> sonstiges <br class=\"atl-forced-newline\" /> </p></td>\n<td><p> ' +
                'unbekannte Ursache<br class=\"atl-forced-newline\" />\n 70 Service-T&uuml;r ge&ouml;ffn' +
                'et<br class=\"atl-forced-newline\" /> </p></td>\n</tr>\n</tbody></table>')
        then:
        markdown() ==
                "| AlarmType | Message |\n" +
                "|-----------|---------|\n" +
                "| sonstiges \\\n" +
                "| unbekannte Ursache\\\n" +
                "\\\n" +
                " 70 Service\\-Tür geöffnet\\\n" +
                "|\n" +
                "\n"
    }

    def xmlPanel() {
        when:
        c2md.parseBody('<h2>Body</h2><ac:structured-macro ac:name=\"code\"><ac:parameter ac:name=\"\">xml</ac:parameter>' +
                '<ac:plain-text-body><![CDATA[<ExecSetup taskId=\"some unique id\">\n  <target>c:\\vtouch\\cortex\\downl' +
                'oads\\PREUPD.CMD</target>\n</ExecSetup>\n]]></ac:plain-text-body></ac:structured-macro>')
        then:
        markdown() ==
                "###Body###\n" +
                "\n" +
                "\n" +
                "\n" +
                "~~~~~~~\n" +
                "<ExecSetup taskId=\"some unique id\">\n" +
                "  <target>c:\\vtouch\\cortex\\downloads\\PREUPD.CMD</target>\n" +
                "</ExecSetup>\n" +
                "\n" +
                "~~~~~~~\n"
    }

    def strong() {
        when:
        c2md.parseBody('<p>This is<strong><em>  not easy</em></strong>, but it works</p>')
        then:
        markdown() == "This is  **_not easy_ **, but it works\n"
    }
}
