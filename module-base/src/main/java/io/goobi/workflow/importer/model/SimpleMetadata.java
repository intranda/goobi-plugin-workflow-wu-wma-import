package io.goobi.workflow.importer.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleMetadata {

    @JacksonXmlProperty(isAttribute = true)
    private String label;

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlProperty(isAttribute = true)
    private String valueURI;

    @JacksonXmlProperty(isAttribute = true)
    private String doctype;

    @JacksonXmlProperty(isAttribute = true)
    private String authority;

    @JacksonXmlProperty(isAttribute = true)
    private String authorityURI;

    @JacksonXmlText
    private String value;
}