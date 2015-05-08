package de.viaboxx.markdown

import groovy.util.slurpersupport.Node

/**
 * Description: <br>
 * <p>
 * Date: 24.10.14<br>
 * </p>
 */
public interface NodeHandler {
    boolean handleNode(Node node)
}