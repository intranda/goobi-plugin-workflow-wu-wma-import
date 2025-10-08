package de.intranda.goobi.plugins;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.goobi.workflow.importer.model.SimpleImportObject;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.jdom2.filter.Filters;
import org.jdom2.output.XMLOutputter;
import org.jdom2.output.Format;

import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reorders XML under <data> (and recursively inside every <group>)
 * so Jackson can reliably parse mixed children.
 * Stable order per element: <group> ... | others (unchanged) ... | <metadata> ...
 */
public final class GoobiXmlReader {

    private GoobiXmlReader() {
    }

    /**
     * Read file and reorder children under every <data> (recursively for <group>), then parse with Jackson
     */
    public static SimpleImportObject readReordered(File file, XmlMapper om) throws Exception {
    	// Reorder xml elements
        SAXBuilder sax = new SAXBuilder();
        sax.setExpandEntities(false);
        Document doc = sax.build(file);

        reorderAllDataNodes(doc);

        // Serialize and hand to Jackson
        StringWriter sw = new StringWriter();
        new XMLOutputter(Format.getRawFormat()).output(doc, sw);
        return om.readValue(sw.toString(), SimpleImportObject.class);
    }

    /**
     * Finds all <data> elements and change the order
     */
    private static void reorderAllDataNodes(Document doc) {
        XPathExpression<Element> xp = XPathFactory.instance().compile("//data", Filters.element());
        List<Element> dataNodes = xp.evaluate(doc);
        for (Element data : dataNodes) {
            reorderChildren(data);
        }
    }

    /**
     * Do the reordering for children as well
     */
    private static void reorderChildren(Element parent) {
        List<Element> children = new ArrayList<>(parent.getChildren());
        List<Element> groups = new ArrayList<>();
        List<Element> metas = new ArrayList<>();

        for (Element c : children) {
            String name = c.getName();
            if ("group".equals(name)) {
                groups.add(c);
            } else if ("metadata".equals(name)) {
                metas.add(c);
            }
        }

        // Detach elements we intend to move
        for (Element g : groups) {
            g.detach();
        }
        for (Element m : metas) {
            m.detach();
        }

        // Insert groups at the beginning (reverse insert to preserve order)
        int insertAt = 0;
        for (int i = groups.size() - 1; i >= 0; i--) {
            parent.addContent(insertAt, groups.get(i));
        }

        // Append metadata at the end (order preserved)
        for (Element m : metas) {
            parent.addContent(m);
        }

        // Recurse into each group
        for (Element g : groups) {
            reorderChildren(g);
        }
    }
}