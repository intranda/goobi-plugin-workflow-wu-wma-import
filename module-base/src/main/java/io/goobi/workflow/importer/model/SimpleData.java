package io.goobi.workflow.importer.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleData {

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

    @JacksonXmlProperty(isAttribute = true)
    private String type;
}