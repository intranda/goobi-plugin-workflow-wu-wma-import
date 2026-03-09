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
public class SimpleCorporate {

    @JacksonXmlProperty(isAttribute = true)
    private String label;

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

    /**
     * add authority information based on just one url
     * 
     * @param authorityEntryUrl
     */
    public void analyzeAuthority(String authorityEntryUrl) {
        authority = null;
        authorityURI = null;
        valueURI = null;

        // split authority information
        if (StringUtils.isNotBlank(authorityEntryUrl)) {
            if (authorityEntryUrl.contains("d-nb.info")) {
                authority = "gnd";
            }
            if (authorityEntryUrl.contains("/")) {
                authorityURI = authorityEntryUrl.substring(0, authorityEntryUrl.lastIndexOf("/") + 1);
                valueURI = authorityEntryUrl.substring(authorityEntryUrl.lastIndexOf("/") + 1);
            }
        }
    }
}
