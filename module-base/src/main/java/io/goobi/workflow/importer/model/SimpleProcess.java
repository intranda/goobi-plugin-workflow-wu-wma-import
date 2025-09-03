package io.goobi.workflow.importer.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleProcess {

    private String title;
    private String batch;

    @JacksonXmlProperty(localName = "property")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimpleProperty> properties = new ArrayList<SimpleProperty>();

    @JacksonXmlProperty(localName = "journal")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimpleJournalEntry> journalentries = new ArrayList<SimpleJournalEntry>();

    @JacksonXmlProperty(localName = "content")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<SimpleContent> contents = new ArrayList<SimpleContent>();
}
