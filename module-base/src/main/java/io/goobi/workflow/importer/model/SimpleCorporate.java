package io.goobi.workflow.importer.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleCorporate {

    @JacksonXmlProperty(localName = "subname")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<String> subnames = new ArrayList<String>();

    private String partname;

    @JacksonXmlProperty(isAttribute = true)
    private String authority;

    @JacksonXmlProperty(isAttribute = true)
    private String authorityURI;

    @JacksonXmlProperty(isAttribute = true)
    private String valueURI;

    @JacksonXmlProperty(isAttribute = true)
    private String role;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

}
