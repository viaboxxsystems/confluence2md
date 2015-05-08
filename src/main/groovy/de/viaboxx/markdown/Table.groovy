package de.viaboxx.markdown

/**
 * Description: <br>
 * <p>
 * Date: 16.09.14<br>
 * </p>
 */
class Table {
    List<Row> rows = []

    Row getRow() { rows[-1] }

}
