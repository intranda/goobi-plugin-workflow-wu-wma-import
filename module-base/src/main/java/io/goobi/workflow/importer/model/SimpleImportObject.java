package io.goobi.workflow.importer.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JacksonXmlRootElement(localName = "goobi-import")
public class SimpleImportObject {

    private SimpleData data;

    private SimpleProcess process;

}