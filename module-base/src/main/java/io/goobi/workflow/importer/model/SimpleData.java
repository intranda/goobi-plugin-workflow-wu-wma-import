package io.goobi.workflow.importer.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleData {

    @JacksonXmlProperty(localName = "data")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimpleData> data = new ArrayList<SimpleData>();
    
    @JacksonXmlProperty(localName = "metadata")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimpleMetadata> metadatas = new ArrayList<SimpleMetadata>();

    @JacksonXmlProperty(localName = "person")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimplePerson> persons = new ArrayList<SimplePerson>();

    @JacksonXmlProperty(localName = "corporate")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimpleCorporate> corporates = new ArrayList<SimpleCorporate>();

    @JacksonXmlProperty(localName = "group")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimpleGroup> groups = new ArrayList<SimpleGroup>();

    @JacksonXmlProperty(localName = "content")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimpleContent> contents = new ArrayList<SimpleContent>();
    
    @JacksonXmlProperty(isAttribute = true)
    private String type;

    /**
     * Add a {@link SimpleMetadata}
     *
     * @param md
     */
    public void add(SimpleMetadata md) {
        if (StringUtils.isNoneBlank(md.getValue(), md.getType())) {
            metadatas.add(md);
        }
    }

    /**
     * add a {@link SimplePerson}
     *
     * @param p
     */
    public void add(SimplePerson p) {
        if (StringUtils.isNoneBlank(p.getRole(), p.getLastname())) {
            persons.add(p);
        }
    }

    /**
     * add a {@link SimpleCorporate}
     *
     * @param c
     */
    public void add(SimpleCorporate c) {
        if (StringUtils.isNoneBlank(c.getName(), c.getRole())) {
            corporates.add(c);
        }
    }

    /**
     * add a {@link SimpleGroup}
     *
     * @param g
     */
    public void add(SimpleGroup g) {
        groups.add(g);
    }
}