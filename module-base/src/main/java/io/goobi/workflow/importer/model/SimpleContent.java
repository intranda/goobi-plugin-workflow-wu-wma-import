package io.goobi.workflow.importer.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleContent {

    @JacksonXmlProperty(isAttribute = true)
    private String folder;

    @JacksonXmlText
    private String source;

}
